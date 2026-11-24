import logging
from typing import List, Literal, Optional
from pydantic import BaseModel, Field
from app.llm.client import get_llm_client
from app.schemas import verify_evidence_grounding

logger = logging.getLogger(__name__)

class StructuredNoteBlock(BaseModel):
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

    prompt = f"""Môn học: {course_title}
Trang Slide số: {slide_number}

[NỘI DUNG SLIDE GỐC]:
{slide_text}

[LỜI GIẢNG CỦA GIẢNG VIÊN ĐƯỢC CĂN VÀO TRANG NÀY]:
{full_transcript_str if full_transcript_str else "(Giảng viên không nói gì trong trang này)"}

Hãy trích xuất và tổng hợp vở ghi chép thông minh theo đúng quy tắc và schema JSON yêu cầu.
"""

    try:
        result = llm.generate_structured(prompt, SlideSynthesisResult, system_instruction=SYSTEM_INSTRUCTION)
        result.slide_number = slide_number

        # Bước hậu kiểm chống bịa đặt (Anti-Hallucination Gate)
        verified_notes = []
        for note in result.notes:
            if not note.evidence_quote or not full_transcript_raw:
                # Nếu không có transcript mà sinh note thì bỏ qua
                continue

            # Kiểm chứng trích dẫn bám sát transcript
            is_grounded = verify_evidence_grounding(note.evidence_quote, full_transcript_raw, threshold=70.0)
            if is_grounded:
                verified_notes.append(note)
            else:
                logger.warning(
                    f"Loại bỏ ghi chú bịa đặt/không bám transcript ở slide {slide_number}: '{note.headline}' "
                    f"- Quote: '{note.evidence_quote}'"
                )

        result.notes = verified_notes
        return result

    except Exception as e:
        logger.error(f"Lỗi khi gọi LLM sinh note cho slide {slide_number}: {e}")
        # Fallback an toàn
        cleaned_bullets = [line.strip() for line in slide_text.split("\n") if line.strip() and len(line.strip()) > 3][:6]
        return SlideSynthesisResult(
            slide_number=slide_number,
            cleaned_slide_bullets=cleaned_bullets,
            notes=[]
        )
