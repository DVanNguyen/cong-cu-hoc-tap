import os
import io
import re
import json
import tempfile
import asyncio
from typing import List, Dict, Any, Optional
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from pydantic import BaseModel

import fitz
from pptx import Presentation

from app.modules.lecture_notes.inline_block_aligner import (
    SlideBlock,
    InlineNoteEvent,
    split_slide_into_blocks,
    NonMonotonicSlideTracker,
    extract_content_tokens,
)
from app.services.asr_vad_service import SlideAwareASREngine
from app.modules.living_dictionary.reference_expander import resolve_concept_two_tier

router = APIRouter(tags=["AI StudyAI Endpoints"])

# Lazy-loaded singleton for ASR engine to prevent high RAM/startup delays
_ASR_ENGINE: Optional[SlideAwareASREngine] = None

def get_asr_engine() -> SlideAwareASREngine:
    global _ASR_ENGINE
    if _ASR_ENGINE is None:
        model_name = os.getenv("WHISPER_MODEL", "base")
        device = os.getenv("WHISPER_DEVICE", "cpu")
        compute_type = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
        _ASR_ENGINE = SlideAwareASREngine(
            model_name_or_path=model_name,
            device=device,
            compute_type=compute_type,
        )
    return _ASR_ENGINE


def extract_terms_from_text(text: str) -> List[str]:
    """Trích xuất các thuật ngữ kỹ thuật, viết tắt in hoa hoặc từ khóa nổi bật."""
    terms = set()
    # Các từ viết tắt in hoa (AI, BFS, DFS, PEAS, SQL, T-SQL, DAG, v.v.)
    acronyms = re.findall(r"\b[A-Z]{2,8}(?:\-[A-Z0-9]+)?\b", text)
    terms.update(acronyms)
    # Các thuật ngữ dạng Title Case
    capitalized = re.findall(r"\b[A-Z][a-z0-9]+(?:\s+[A-Z][a-z0-9]+)*\b", text)
    for c in capitalized:
        if len(c) > 3 and c.lower() not in {"slide", "chapter", "trang", "bài giảng", "chương", "phần"}:
            terms.add(c)
    return list(terms)[:30]


# ============================================================================
# 1. POST /parse-slide-blocks (Tách slide PDF/PPTX thành SlideBlock[])
# ============================================================================
@router.post("/parse-slide-blocks")
async def parse_slide_blocks_endpoint(slide_file: UploadFile = File(...)):
    filename = slide_file.filename or "slide.pdf"
    content = await slide_file.read()
    if not content:
        raise HTTPException(status_code=400, detail="Tệp slide rỗng")

    is_pdf = filename.lower().endswith(".pdf")
    is_pptx = filename.lower().endswith((".pptx", ".ppt"))

    if not is_pdf and not is_pptx:
        raise HTTPException(status_code=400, detail="Chỉ hỗ trợ file định dạng .pdf hoặc .pptx")

    all_blocks: List[SlideBlock] = []
    page_texts: List[str] = []

    try:
        if is_pdf:
            doc = fitz.open(stream=content, filetype="pdf")
            for page_idx, page in enumerate(doc, start=1):
                raw_text = page.get_text() or ""
                page_texts.append(raw_text)
                blocks = split_slide_into_blocks(page_idx, raw_text)
                all_blocks.extend(blocks)
            doc.close()
        else:
            prs = Presentation(io.BytesIO(content))
            for page_idx, slide in enumerate(prs.slides, start=1):
                text_parts = []
                for shape in slide.shapes:
                    if hasattr(shape, "text") and shape.text:
                        text_parts.append(shape.text)
                raw_text = "\n".join(text_parts)
                page_texts.append(raw_text)
                blocks = split_slide_into_blocks(page_idx, raw_text)
                all_blocks.extend(blocks)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Lỗi khi đọc file slide: {str(e)}")

    full_text = " ".join(page_texts)
    slide_terms = extract_terms_from_text(full_text)

    return {
        "page_count": len(page_texts),
        "total_blocks": len(all_blocks),
        "blocks": [b.model_dump() for b in all_blocks],
        "slide_terms": slide_terms,
    }


# ============================================================================
# 2. POST /live-align-chunk (Lọc giọng ECAPA-TDNN + ASR + Windowed BGE-M3 Live)
# ============================================================================
_LIVE_FILLER_PREFIX_RE = re.compile(
    r"^(?:(?:ờ+|à+|ừm+|ưm+|ừ|ok|okay|rồi|thì|vậy thì|nào|ha|nhé|nha)[,\.\s]+)+",
    flags=re.IGNORECASE,
)


def clean_live_note_text(text: str) -> str:
    """Làm sạch nhanh từ đệm mở đầu để câu note Live gọn hơn transcript thô (< 1ms)."""
    cleaned = _LIVE_FILLER_PREFIX_RE.sub("", text.strip()).strip()
    if not cleaned:
        return text.strip()
    return cleaned[0].upper() + cleaned[1:] if len(cleaned) > 1 else cleaned.upper()


def match_live_utterance_semantic(
    transcript_text: str,
    blocks: List[SlideBlock],
    current_slide_page: int,
    start_sec: float,
    end_sec: float,
    off_slide_threshold: float = 0.26,
    novelty_threshold: float = 0.30,
) -> InlineNoteEvent:
    """
    Windowed Semantic Live Matcher dùng BGE-M3 (kèm cache vector slide trong SemanticEmbedderService):
    - Kết hợp điểm ngữ nghĩa cấp block + cấp trang slide + token overlap.
    - Ưu tiên bám mượt (+0.08 trang hiện tại, +0.05 trang kế tiếp), phạt nhảy xa (-0.18 khi |Δ| > 3).
    - Phạt trang Mục lục sau giây 60 và chặn câu đệm làm trôi slide.
    """
    import numpy as np
    from app.modules.lecture_notes.alignment import (
        compute_token_overlap,
        is_filler_sentence,
        slide_is_overview,
    )
    from app.services.semantic_embedder import get_semantic_embedder

    if not blocks or not transcript_text.strip():
        return InlineNoteEvent(
            matched_slide_page=current_slide_page,
            matched_block_id=None,
            action="highlight_only",
            confidence=0.0,
            novelty_score=0.0,
            transcript_text=transcript_text,
            start_sec=start_sec,
            end_sec=end_sec,
        )

    # Câu đệm ngắn ("ờ", "ok", "rồi"...) giữ nguyên trang hiện tại, không sinh ghi chú rác
    if is_filler_sentence(transcript_text):
        return InlineNoteEvent(
            matched_slide_page=current_slide_page,
            matched_block_id=None,
            action="highlight_only",
            confidence=0.0,
            novelty_score=0.0,
            transcript_text=transcript_text,
            start_sec=start_sec,
            end_sec=end_sec,
        )

    # Gom văn bản cấp trang để đánh giá ngữ cảnh trang slide
    page_blocks_map: Dict[int, List[SlideBlock]] = {}
    for b in blocks:
        page_blocks_map.setdefault(b.slide_page, []).append(b)

    page_numbers = sorted(page_blocks_map.keys())
    page_texts = ["\n".join(b.text for b in page_blocks_map[p]) for p in page_numbers]
    block_texts = [b.text for b in blocks]

    embedder = get_semantic_embedder()
    # Chỉ encode 1 câu transcript mới; page_texts & block_texts được cache trong _cache sau chunk đầu tiên
    page_sims = embedder.similarity_matrix([transcript_text], page_texts)[0]
    block_sims = embedder.similarity_matrix([transcript_text], block_texts)[0]

    page_score_map: Dict[int, float] = {}
    for idx, p_num in enumerate(page_numbers):
        p_text = page_texts[idx]
        p_title = page_blocks_map[p_num][0].text if page_blocks_map[p_num] else ""
        tok_ov = compute_token_overlap(transcript_text, p_text)
        base_p = 0.80 * float(page_sims[idx]) + 0.20 * tok_ov

        # Cửa sổ chuyển tiếp mượt đúng đặc tả Tuần 5:
        # +0.08 giữ nguyên trang, +0.05 tiến 1 trang, 0.0 trong cửa sổ ±3, -0.18 khi nhảy xa |Δ| > 3
        delta = p_num - current_slide_page
        if delta == 0:
            bias = 0.08
        elif delta == 1:
            bias = 0.05
        elif abs(delta) <= 3:
            bias = 0.0
        else:
            bias = -0.18

        # Phạt trang mục lục/tổng quan sau phút đầu tiên
        if start_sec > 60.0 and slide_is_overview({"title": p_title, "raw_text": p_text}):
            bias -= 0.12

        page_score_map[p_num] = base_p + bias

    # Chọn trang thắng cuộc
    best_page = max(page_numbers, key=lambda p: page_score_map[p])
    best_page_score = page_score_map[best_page]

    # Tầng 2: chọn block khớp nhất ngay trong trang best_page (ưu tiên block nội dung hơn tiêu đề ngắn b1)
    page_indices = [i for i, b in enumerate(blocks) if b.slide_page == best_page]
    candidate_indices = [
        i for i in page_indices
        if not (blocks[i].block_index == 1 and len(blocks[i].text) < 80)
    ] or page_indices

    best_idx = max(
        candidate_indices,
        key=lambda i: 0.75 * float(block_sims[i]) + 0.25 * compute_token_overlap(transcript_text, blocks[i].text),
    )
    best_block = blocks[best_idx]
    raw_block_sim = float(block_sims[best_idx])
    tok_ov_best = compute_token_overlap(transcript_text, best_block.text)
    confidence = float(np.clip(best_page_score, 0.0, 1.0))
    novelty = round(float(np.clip(1.0 - (0.5 * tok_ov_best + 0.5 * raw_block_sim), 0.0, 1.0)), 3)

    if best_page_score < off_slide_threshold:
        return InlineNoteEvent(
            matched_slide_page=current_slide_page,
            matched_block_id=None,
            action="general_note",
            confidence=round(confidence, 3),
            novelty_score=1.0,
            transcript_text=transcript_text,
            start_sec=start_sec,
            end_sec=end_sec,
            jumped_non_linear=False,
        )

    jumped = best_page < current_slide_page or best_page > current_slide_page + 1
    action = "attach_inline_note" if novelty >= novelty_threshold else "highlight_only"

    return InlineNoteEvent(
        matched_slide_page=best_page,
        matched_block_id=best_block.block_id,
        action=action,
        confidence=round(confidence, 3),
        novelty_score=novelty,
        transcript_text=transcript_text,
        start_sec=start_sec,
        end_sec=end_sec,
        jumped_non_linear=jumped,
    )


async def process_live_chunk_core(
    audio_path: str,
    blocks: List[SlideBlock],
    current_slide_page: int = 1,
    language: Optional[str] = "auto",
    chunk_start_sec: float = 0.0,
    previous_concepts: Optional[List[dict]] = None,
    teacher_embedding: Optional[List[float]] = None,
    voice_threshold: float = 0.35,
    resolve_highlights: bool = True,
) -> Dict[str, Any]:
    """
    Hàm lõi xử lý 1 chunk âm thanh Live (5-10s):
    1. Nếu có teacher_embedding -> lọc giọng bằng ECAPA-TDNN (~25ms) TRƯỚC Faster-Whisper.
    2. Chạy Faster-Whisper + VAD nhận dạng lời giảng.
    3. Khớp ngữ nghĩa có cửa sổ bằng BGE-M3 (match_live_utterance_semantic).
    4. Làm sạch câu đệm & gắn khái niệm 2 tầng.
    """
    voice_info = None
    if teacher_embedding and len(teacher_embedding) >= 64:
        from app.services.speaker_verification import get_voice_filter_service
        voice_service = get_voice_filter_service()
        voice_info = voice_service.verify_short_chunk(
            audio_path=audio_path,
            teacher_embedding=teacher_embedding,
            threshold=voice_threshold,
        )
        if not voice_info.get("is_teacher", False):
            return {
                "matched_slide_page": current_slide_page,
                "matched_block_id": None,
                "action": "ignored_non_teacher",
                "inline_note": None,
                "new_highlights": [],
                "jumped_non_linear": False,
                "transcript": "",
                "voice_verification": voice_info,
                "alignment_method": "windowed_bge_m3_live",
            }

    engine = get_asr_engine()
    all_terms = extract_terms_from_text(" ".join(b.text for b in blocks))[:20]
    segments, _meta = engine.transcribe_with_metadata(
        audio_path=audio_path,
        slide_terms=all_terms[:40],
        language=language,
        use_vad=True,
    )

    transcript_text = " ".join(s["text"] for s in segments).strip()
    if not transcript_text:
        return {
            "matched_slide_page": current_slide_page,
            "matched_block_id": None,
            "action": "highlight_only",
            "inline_note": None,
            "new_highlights": [],
            "jumped_non_linear": False,
            "transcript": "",
            "voice_verification": voice_info,
            "alignment_method": "windowed_bge_m3_live",
        }

    duration = segments[-1]["end_sec"] if segments else 8.0
    event: InlineNoteEvent = match_live_utterance_semantic(
        transcript_text=transcript_text,
        blocks=blocks,
        current_slide_page=current_slide_page,
        start_sec=chunk_start_sec,
        end_sec=chunk_start_sec + duration,
    )

    # Tìm khái niệm để gắn highlight
    new_highlights = []
    if resolve_highlights:
        prev_concepts = previous_concepts or []
        tokens = extract_terms_from_text(transcript_text)
        if event.matched_block_id:
            matched_block = next((b for b in blocks if b.block_id == event.matched_block_id), None)
            if matched_block:
                tokens.extend(extract_terms_from_text(matched_block.text))

        for term in list(dict.fromkeys(tokens))[:3]:
            info = await resolve_concept_two_tier(term, previous_lectures_concepts=prev_concepts)
            new_highlights.append({
                "term": term,
                "source_type": info.get("source_type", "academic_internet"),
                "source_label": info.get("source_label", "Học thuật quốc tế"),
                "explanation": info.get("explanation", ""),
                "course_context_id": None,
                "alternative_contexts": info.get("alternative_contexts", []),
            })

    inline_note_payload = None
    if event.action in ("attach_inline_note", "general_note"):
        cleaned_note = clean_live_note_text(event.transcript_text)
        inline_note_payload = {
            "explanation_text": cleaned_note,
            "raw_transcript": event.transcript_text,
            "timestamp_start_sec": event.start_sec,
            "timestamp_end_sec": event.end_sec,
            "novelty_score": event.novelty_score,
            "jumped_non_linear": event.jumped_non_linear,
        }

    return {
        "matched_slide_page": event.matched_slide_page,
        "matched_block_id": event.matched_block_id,
        "action": event.action,
        "inline_note": inline_note_payload,
        "new_highlights": new_highlights,
        "jumped_non_linear": event.jumped_non_linear,
        "transcript": transcript_text,
        "confidence": event.confidence,
        "voice_verification": voice_info,
        "alignment_method": "windowed_bge_m3_live",
    }


@router.post("/live-align-chunk")
async def live_align_chunk_endpoint(
    audio_chunk: UploadFile = File(...),
    blocks_json: str = Form(...),
    current_slide_page: int = Form(1),
    language: Optional[str] = Form("auto"),
    course_id: Optional[int] = Form(None),
    chunk_start_sec: float = Form(0.0),
    previous_concepts_json: Optional[str] = Form(None),
    teacher_embedding_json: Optional[str] = Form(None),
    voice_threshold: float = Form(0.35),
):
    try:
        raw_blocks = json.loads(blocks_json)
        blocks = [SlideBlock(**b) for b in raw_blocks]
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Dữ liệu blocks_json không hợp lệ: {str(e)}")

    prev_concepts = []
    if previous_concepts_json:
        try:
            prev_concepts = json.loads(previous_concepts_json)
        except Exception:
            pass

    teacher_emb = None
    if teacher_embedding_json and teacher_embedding_json.strip():
        try:
            parsed_emb = json.loads(teacher_embedding_json)
            if isinstance(parsed_emb, list) and len(parsed_emb) >= 64:
                teacher_emb = [float(x) for x in parsed_emb]
        except Exception:
            teacher_emb = None

    ext = os.path.splitext(audio_chunk.filename or ".m4a")[1] or ".m4a"
    content = await audio_chunk.read()
    if not content:
        raise HTTPException(status_code=400, detail="Audio chunk rỗng")

    with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as tmp_file:
        tmp_file.write(content)
        tmp_path = tmp_file.name

    try:
        return await process_live_chunk_core(
            audio_path=tmp_path,
            blocks=blocks,
            current_slide_page=current_slide_page,
            language=language,
            chunk_start_sec=chunk_start_sec,
            previous_concepts=prev_concepts,
            teacher_embedding=teacher_emb,
            voice_threshold=voice_threshold,
            resolve_highlights=True,
        )
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


# ============================================================================
# 3. POST /process-lecture-pair (Xử lý toàn bộ Slide + Audio cả bài)
# ============================================================================
@router.post("/process-lecture-pair")
async def process_lecture_pair_endpoint(
    slide_file: UploadFile = File(...),
    audio_file: UploadFile = File(...),
    language: Optional[str] = Form("auto"),
    course_id: Optional[int] = Form(None),
    title: Optional[str] = Form("Bài giảng mới"),
    previous_concepts_json: Optional[str] = Form(None),
):
    # Bước 1: Parse slide
    slide_content = await slide_file.read()
    filename = slide_file.filename or "slide.pdf"
    is_pdf = filename.lower().endswith(".pdf")

    all_blocks: List[SlideBlock] = []
    blocks_by_page: Dict[int, List[SlideBlock]] = {}

    if is_pdf:
        doc = fitz.open(stream=slide_content, filetype="pdf")
        for page_idx, page in enumerate(doc, start=1):
            raw_text = page.get_text() or ""
            blocks = split_slide_into_blocks(page_idx, raw_text)
            all_blocks.extend(blocks)
            blocks_by_page[page_idx] = blocks
        doc.close()
    else:
        prs = Presentation(io.BytesIO(slide_content))
        for page_idx, slide in enumerate(prs.slides, start=1):
            text_parts = []
            for shape in slide.shapes:
                if hasattr(shape, "text") and shape.text:
                    text_parts.append(shape.text)
            raw_text = "\n".join(text_parts)
            blocks = split_slide_into_blocks(page_idx, raw_text)
            all_blocks.extend(blocks)
            blocks_by_page[page_idx] = blocks

    # Bước 2: ASR toàn bài audio
    audio_ext = os.path.splitext(audio_file.filename or ".m4a")[1] or ".m4a"
    audio_content = await audio_file.read()

    with tempfile.NamedTemporaryFile(suffix=audio_ext, delete=False) as tmp_audio:
        tmp_audio.write(audio_content)
        tmp_audio_path = tmp_audio.name

    try:
        engine = get_asr_engine()
        terms_for_bias = extract_terms_from_text(" ".join(b.text for b in all_blocks))[:25]
        segments, meta = engine.transcribe_with_metadata(
            audio_path=tmp_audio_path,
            slide_terms=terms_for_bias,
            course_title=title,
            language=language,
            use_vad=True,
        )
    finally:
        if os.path.exists(tmp_audio_path):
            os.remove(tmp_audio_path)

    # Bước 3: Tracker Non-Monotonic
    tracker = NonMonotonicSlideTracker(all_blocks)
    notes_by_block: Dict[str, List[Dict[str, Any]]] = {}
    general_notes: List[Dict[str, Any]] = []

    for seg in segments:
        text = seg["text"].strip()
        if not text:
            continue
        ev = tracker.process_utterance(text, seg["start_sec"], seg["end_sec"])
        if ev.action == "attach_inline_note" and ev.matched_block_id:
            notes_by_block.setdefault(ev.matched_block_id, []).append({
                "explanation_text": text,
                "raw_transcript": text,
                "timestamp_start_sec": ev.start_sec,
                "timestamp_end_sec": ev.end_sec,
                "novelty_score": ev.novelty_score,
                "jumped_non_linear": ev.jumped_non_linear,
            })
        elif ev.action == "general_note":
            general_notes.append({
                "explanation_text": text,
                "raw_transcript": text,
                "timestamp_start_sec": ev.start_sec,
                "timestamp_end_sec": ev.end_sec,
                "novelty_score": 1.0,
                "jumped_non_linear": False,
            })

    # Bước 4: Tạo highlights 2 màu cho từng block
    prev_concepts = []
    if previous_concepts_json:
        try:
            prev_concepts = json.loads(previous_concepts_json)
        except Exception:
            pass

    highlights_by_block: Dict[str, List[Dict[str, Any]]] = {}
    all_extracted_terms = set()
    for b in all_blocks:
        terms = extract_terms_from_text(b.text)
        for t in terms[:2]:
            all_extracted_terms.add((b.block_id, t))

    # Xử lý song song các thuật ngữ với resolve_concept_two_tier
    async def process_term(block_id: str, t: str):
        info = await resolve_concept_two_tier(t, previous_lectures_concepts=prev_concepts)
        return block_id, {
            "term": t,
            "source_type": info.get("source_type", "academic_internet"),
            "source_label": info.get("source_label", "Học thuật quốc tế"),
            "explanation": info.get("explanation", ""),
            "course_context_id": None,
            "alternative_contexts": info.get("alternative_contexts", []),
        }

    tasks = [process_term(b_id, t) for b_id, t in list(all_extracted_terms)[:25]]
    resolved = await asyncio.gather(*tasks, return_exceptions=True)
    for res in resolved:
        if isinstance(res, tuple):
            b_id, h_data = res
            highlights_by_block.setdefault(b_id, []).append(h_data)

    # Bước 5: Đóng gói pages[]
    pages = []
    for page_num in sorted(blocks_by_page.keys()):
        b_list = blocks_by_page[page_num]
        page_title = b_list[0].text if b_list else f"Trang {page_num}"
        formatted_blocks = []
        for b in b_list:
            formatted_blocks.append({
                "block_id": b.block_id,
                "slide_page": b.slide_page,
                "block_index": b.block_index,
                "kind": b.kind,
                "original_slide_text": b.text,
                "is_confused": False,
                "last_spoken_sec": None,
                "inline_notes": notes_by_block.get(b.block_id, []),
                "highlights": highlights_by_block.get(b.block_id, []),
            })
        pages.append({
            "page_number": page_num,
            "title": page_title,
            "blocks": formatted_blocks,
        })

    return {
        "title": title,
        "total_pages": len(pages),
        "pages": pages,
        "general_notes": general_notes,
    }
