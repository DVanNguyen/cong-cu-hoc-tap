import logging
from typing import List, Literal, Optional
from pydantic import BaseModel, Field
from app.llm.client import get_llm_client
from app.schemas import verify_evidence_grounding

logger = logging.getLogger(__name__)

# ==============================================================================
# 1. SCHEMA CHO TỔNG HỢP GHI CHÚ BÁM SLIDE (SLIDE-AWARE)
# ==============================================================================
class StructuredNoteBlock(BaseModel):
    target_block_id: Optional[str] = Field(
        default=None,
        description="ID block trên slide (VD: slide_4_b2) mà ý này giải thích trực tiếp; null nếu giải thích chung cho cả slide"
    )
    category: Literal["core_explanation", "worked_example", "exam_tip"] = Field(
        description="core_explanation: thầy giải thích bản chất/trực quan; worked_example: ví dụ thực tế/code; exam_tip: lưu ý dễ sai hoặc hay ra thi"
    )
    headline: str = Field(description="Tiêu đề ngắn 4-8 từ tóm ý chính")
    content: str = Field(description="Nội dung viết lại mạch lạc, súc tích 1-3 câu bằng tiếng Việt chuẩn, giữ nguyên thuật ngữ tiếng Anh chuyên ngành")
    evidence_quote: str = Field(description="Trích nguyên văn 1 câu từ transcript của giảng viên chứng minh ý này")
    start_time_sec: float
    end_time_sec: float


class SlideSynthesisResult(BaseModel):
    slide_number: int
    cleaned_slide_bullets: List[str] = Field(
        description="Khung sườn slide gốc đã lọc bỏ rác header/footer/số trang/khẩu hiệu trường"
    )
    notes: List[StructuredNoteBlock] = Field(
        description="Tối đa 3-4 ý giá trị nhất giảng viên nói thêm ngoài slide; trả về rỗng [] nếu giảng viên chỉ đọc lại slide"
    )


SYSTEM_INSTRUCTION = """Bạn là trợ lý biên tập vở ghi chép đại học chuyên ngành CNTT.
Đầu vào gồm: (1) nội dung thô của 1 trang Slide, và (2) danh sách các đoạn transcript lời giảng kèm mốc giây [start - end] được căn vào trang đó.

Quy tắc bắt buộc:
1. KHÔNG sao chép nguyên văn câu nói lắp, từ đệm ("ờ", "à", "các bạn thấy không", "nhìn lên bảng").
2. KHÔNG lặp lại những gì đã ghi rõ trên slide; chỉ trích xuất: (a) giải thích trực quan giúp dễ hiểu hơn, (b) ví dụ thực tế / đoạn code thầy minh họa, (c) mẹo thi hoặc lỗi hay gặp.
3. Mỗi ghi chú BẮT BUỘC kèm `evidence_quote` copy nguyên văn từ transcript đầu vào và đúng `start_time_sec`, `end_time_sec` của đoạn đó.
4. Giữ nguyên thuật ngữ chuyên ngành tiếng Anh (VD: Transaction, ACID, Heuristic, Deadlock) kèm giải thích tiếng Việt tự nhiên.
5. Nếu trang slide này giảng viên chỉ đọc lướt hoặc không nói thêm ý mới, trả về `notes: []`.
"""


def assign_target_blocks(
    slide_number: int,
    bullets: List[str],
    notes: List[StructuredNoteBlock],
    trust_llm_ids: bool = True,
) -> None:
    """
    Gán target_block_id cho từng ghi chú: giữ ID do LLM trả về nếu hợp lệ và không bị lệch chỉ số,
    ngược lại chọn block có cosine similarity cao nhất (BGE-M3) trong trang.
    Quy ước block_id khớp Laravel: slide_{N}_b{i}, i từ 1; b1 ngắn (<80 ký tự) là tiêu đề.
    """
    if not notes or not bullets:
        return
    ids = [f"slide_{slide_number}_b{i}" for i in range(1, len(bullets) + 1)]
    if len(ids) == 1:
        for n in notes:
            n.target_block_id = ids[0]
        return
    candidate_idx = [
        i for i, b in enumerate(bullets) if not (i == 0 and len(b) < 80)
    ] or list(range(len(bullets)))
    if not trust_llm_ids:
        for n in notes:
            n.target_block_id = None
    todo = [n for n in notes if n.target_block_id not in ids]
    if not todo:
        return
    try:
        from app.services.semantic_embedder import get_semantic_embedder
        sim = get_semantic_embedder().similarity_matrix(
            [f"{n.headline} {n.content}" for n in todo],
            [bullets[i] for i in candidate_idx],
        )
        for row, n in enumerate(todo):
            n.target_block_id = ids[candidate_idx[int(sim[row].argmax())]]
    except Exception as e:
        logger.warning(f"Semantic block assignment failed for slide {slide_number}: {e}")


def synthesize_slide_notes(
    slide_number: int,
    slide_text: str,
    transcript_segments: List[dict],
    course_title: str = ""
) -> SlideSynthesisResult:
    """
    Gom transcript của slide, gọi LLM tổng hợp ghi chú 3 tầng và kiểm chứng trích dẫn chống ảo giác.
    """
    llm = get_llm_client()

    # Gom transcript thành chuỗi có mốc thời gian
    transcript_lines = []
    combined_transcript_text = []
    for s in transcript_segments:
        start = round(float(s.get("start_sec", s.get("start_time_sec", 0.0))), 1)
        end = round(float(s.get("end_sec", s.get("end_time_sec", 0.0))), 1)
        txt = s.get("text", "").strip()
        if txt:
            transcript_lines.append(f"[{start}s - {end}s]: {txt}")
            combined_transcript_text.append(txt)

    full_transcript_str = "\n".join(transcript_lines)
    full_transcript_raw = " ".join(combined_transcript_text)

    # Nếu slide không có lời giảng tương ứng, không cần gọi LLM (tránh tốn quota và tránh ảo giác)
    if not full_transcript_raw.strip():
        cleaned_bullets = [line.strip("-•* ").strip() for line in slide_text.split("\n") if len(line.strip("-•* ").strip()) > 3][:6]
        return SlideSynthesisResult(
            slide_number=slide_number,
            cleaned_slide_bullets=cleaned_bullets,
            notes=[]
        )

    # Tách slide thành các block có ID chuẩn slide_{N}_b{i} để đưa vào prompt cho LLM
    from app.modules.lecture_notes.inline_block_aligner import split_slide_into_blocks
    raw_blocks = split_slide_into_blocks(slide_number, slide_text)
    block_lines = [f"- slide_{slide_number}_b{b.block_index} ({b.kind}): {b.text}" for b in raw_blocks]
    blocks_str = "\n".join(block_lines) if block_lines else f"- slide_{slide_number}_b1 (heading): {slide_text[:120]}"

    prompt = f"""Môn học: {course_title}
Trang Slide số: {slide_number}

[NỘI DUNG SLIDE GỐC]:
{slide_text}

[DANH SÁCH CÁC BLOCK TRÊN SLIDE (dùng ID này cho target_block_id)]:
{blocks_str}

[LỜI GIẢNG CỦA GIẢNG VIÊN ĐƯỢC CĂN VÀO TRANG NÀY]:
{full_transcript_str if full_transcript_str else "(Giảng viên không nói gì trong trang này)"}

Hãy trích xuất và tổng hợp vở ghi chép thông minh theo đúng quy tắc và schema JSON yêu cầu. Với mỗi note, hãy chọn `target_block_id` khớp nhất với ý mà giảng viên đang giải thích.
"""

    try:
        result = llm.generate_structured(prompt, SlideSynthesisResult, system_instruction=SYSTEM_INSTRUCTION)
        result.slide_number = slide_number

        # Bước hậu kiểm chống bịa đặt (Anti-Hallucination Gate)
        verified_notes = []
        for note in result.notes:
            if not note.evidence_quote or not full_transcript_raw:
                continue

            is_grounded = verify_evidence_grounding(note.evidence_quote, full_transcript_raw, threshold=70.0)
            if is_grounded:
                verified_notes.append(note)
            else:
                logger.warning(
                    f"Loại bỏ ghi chú bịa đặt/không bám transcript ở slide {slide_number}: '{note.headline}' "
                    f"- Quote: '{note.evidence_quote}'"
                )

        if not result.cleaned_slide_bullets and raw_blocks:
            result.cleaned_slide_bullets = [b.text for b in raw_blocks]

        result.notes = verified_notes
        # Chỉ giữ target_block_id từ LLM khi số block khớp raw_blocks; ngược lại remap bằng BGE-M3 để tránh lệch chỉ số
        same_block_count = len(result.cleaned_slide_bullets) == len(raw_blocks)
        assign_target_blocks(
            slide_number,
            result.cleaned_slide_bullets,
            result.notes,
            trust_llm_ids=same_block_count,
        )
        return result

    except Exception as e:
        logger.error(f"Lỗi khi gọi LLM sinh note cho slide {slide_number}: {e}")
        cleaned_bullets = [line.strip() for line in slide_text.split("\n") if line.strip() and len(line.strip()) > 3][:6]
        return SlideSynthesisResult(
            slide_number=slide_number,
            cleaned_slide_bullets=cleaned_bullets,
            notes=[]
        )


# ==============================================================================
# 2. SCHEMA & HÀM XỬ LÝ GHI CHÚ BÀI GIẢNG CHỈ TỪ GIỌNG NÓI (VOICE-ONLY TEST)
# ==============================================================================
class VoiceNoteItem(BaseModel):
    category: Literal["core_concept", "example_code", "exam_tip", "announcement"] = Field(
        description="core_concept: khái niệm/bản chất; example_code: ví dụ/minh họa; exam_tip: lưu ý/mẹo thi; announcement: dặn dò/bài tập"
    )
    headline: str = Field(description="Tiêu đề súc tích 4-8 từ")
    content: str = Field(description="Nội dung viết lại mạch lạc, rõ ràng 1-3 câu")
    timestamp_label: str = Field(description="Mốc thời gian dạng MM:SS, ví dụ '01:25'")
    start_time_sec: float
    end_time_sec: float
    evidence_quote: str = Field(description="Câu trích dẫn nguyên văn từ lời nói làm bằng chứng")


class TechnicalTermItem(BaseModel):
    term: str = Field(description="Thuật ngữ kỹ thuật hoặc tiếng Anh chuyên ngành")
    explanation: str = Field(description="Giải thích ngắn gọn, dễ hiểu")


class VoiceLectureNoteResult(BaseModel):
    title: str = Field(description="Tiêu đề ngắn gọn phản ánh đúng nội dung người nói")
    summary: str = Field(description="Tóm tắt tổng quan 2-4 câu nội dung cốt lõi của file âm thanh")
    key_points: List[VoiceNoteItem] = Field(description="Danh sách các ý chính, kiến thức hoặc hướng dẫn trong bài nói")
    technical_terms: List[TechnicalTermItem] = Field(default_factory=list, description="Các thuật ngữ quan trọng nếu có")
    action_items_or_tips: List[str] = Field(default_factory=list, description="Các lưu ý quan trọng, bài tập hoặc việc cần làm")


VOICE_ONLY_SYSTEM_INSTRUCTION = """Bạn là trợ lý biên tập vở ghi chép học thuật thông minh từ file ghi âm giọng nói.
Đầu vào là toàn bộ bản ghi âm lời giảng/hội thoại của người nói kèm mốc thời gian [start_sec - end_sec].

Nhiệm vụ:
1. Đặt tiêu đề (title) ngắn gọn, chuẩn xác thể hiện đúng chủ đề được nói.
2. Viết đoạn tóm tắt (summary) 2-4 câu tổng quan nội dung chính.
3. Trích xuất danh sách các ý kiến thức/bài giảng cốt lõi (key_points):
   - Loại bỏ từ đệm, nói lắp ("ờ", "à", "ừ thì").
   - Mỗi ý phải có `evidence_quote` trích nguyên văn câu nói làm bằng chứng, kèm đúng `start_time_sec`, `end_time_sec` và `timestamp_label` (định dạng MM:SS).
   - Phân loại rõ: core_concept (khái niệm), example_code (ví dụ), exam_tip (lưu ý/mẹo), announcement (dặn dò).
4. Liệt kê các thuật ngữ chuyên ngành (technical_terms) được nhắc tới.
5. Liệt kê các việc cần làm / bài tập / hạn nộp / lưu ý thực hành (action_items_or_tips) nếu có.
6. TUYỆT ĐỐI KHÔNG BỊA ĐẶT những ý người nói không đề cập. Giữ nguyên thuật ngữ tiếng Anh chuyên ngành.
"""


def synthesize_voice_only_notes(
    transcript_segments: List[dict],
    title_hint: str = ""
) -> VoiceLectureNoteResult:
    """
    Nhận danh sách transcript segments từ audio, gọi Gemini LLM tổng hợp vở ghi chép hoàn chỉnh.
    """
    llm = get_llm_client()

    if not transcript_segments:
        return VoiceLectureNoteResult(
            title=title_hint or "Ghi chú âm thanh",
            summary="Không nhận diện được giọng nói trong tệp âm thanh này.",
            key_points=[],
            technical_terms=[],
            action_items_or_tips=[]
        )

    # Gom transcript thành chuỗi có mốc thời gian
    transcript_lines = []
    combined_transcript_text = []
    for s in transcript_segments:
        start = float(s.get("start_sec", s.get("start_time_sec", 0.0)))
        end = float(s.get("end_sec", s.get("end_time_sec", 0.0)))
        txt = s.get("text", "").strip()
        if txt:
            m = int(start // 60)
            sec = int(start % 60)
            ts_label = f"{m:02d}:{sec:02d}"
            transcript_lines.append(f"[{ts_label}] ({start:.1f}s - {end:.1f}s): {txt}")
            combined_transcript_text.append(txt)

    full_transcript_str = "\n".join(transcript_lines)
    full_transcript_raw = " ".join(combined_transcript_text)

    prompt = f"""Tiêu đề gợi ý (nếu có): {title_hint}

[TOÀN BỘ TRANSCRIPT CỦA TỆP ÂM THANH]:
{full_transcript_str}

Hãy nghe và biên tập lại thành vở ghi chép thông minh theo đúng schema JSON yêu cầu.
"""

    try:
        result = llm.generate_structured(
            prompt,
            VoiceLectureNoteResult,
            system_instruction=VOICE_ONLY_SYSTEM_INSTRUCTION
        )

        # Hậu kiểm chống bịa đặt (Anti-Hallucination Gate)
        verified_points = []
        for point in result.key_points:
            if not point.evidence_quote:
                continue
            is_grounded = verify_evidence_grounding(point.evidence_quote, full_transcript_raw, threshold=65.0)
            if is_grounded:
                verified_points.append(point)
            else:
                logger.warning(
                    f"Loại bỏ ý bịa đặt/không khớp lời nói: '{point.headline}' - Quote: '{point.evidence_quote}'"
                )

        result.key_points = verified_points
        return result

    except Exception as e:
        logger.error(f"Lỗi khi gọi LLM sinh voice notes: {e}")
        return VoiceLectureNoteResult(
            title=title_hint or "Ghi chú bài giảng",
            summary=full_transcript_raw[:300] + ("..." if len(full_transcript_raw) > 300 else ""),
            key_points=[],
            technical_terms=[],
            action_items_or_tips=[]
        )

