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
# 2. POST /live-align-chunk (ASR chunk + NonMonotonicSlideTracker + Highlights)
# ============================================================================
@router.post("/live-align-chunk")
async def live_align_chunk_endpoint(
    audio_chunk: UploadFile = File(...),
    blocks_json: str = Form(...),
    current_slide_page: int = Form(1),
    language: Optional[str] = Form("auto"),
    course_id: Optional[int] = Form(None),
    chunk_start_sec: float = Form(0.0),
    previous_concepts_json: Optional[str] = Form(None),
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

    # Ghi file tạm để đưa vào Faster-Whisper
    ext = os.path.splitext(audio_chunk.filename or ".m4a")[1] or ".m4a"
    content = await audio_chunk.read()
    if not content:
        raise HTTPException(status_code=400, detail="Audio chunk rỗng")

    with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as tmp_file:
        tmp_file.write(content)
        tmp_path = tmp_file.name

    try:
        engine = get_asr_engine()
        all_terms = extract_terms_from_text(" ".join(b.text for b in blocks))[:20]
        segments, meta = engine.transcribe_with_metadata(
            audio_path=tmp_path,
            slide_terms=all_terms[:40],
            language=language,
            use_vad=True,
        )
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

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
        }

    duration = segments[-1]["end_sec"] if segments else 8.0
    tracker = NonMonotonicSlideTracker(blocks)
    tracker.current_slide_page = current_slide_page

    event: InlineNoteEvent = tracker.process_utterance(
        transcript_text=transcript_text,
        start_sec=chunk_start_sec,
        end_sec=chunk_start_sec + duration,
    )

    # Tìm khái niệm để gắn highlight
    new_highlights = []
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
        inline_note_payload = {
            "explanation_text": event.transcript_text,
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
    }


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
