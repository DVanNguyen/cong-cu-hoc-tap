from fastapi import APIRouter, HTTPException
from typing import List, Dict, Any
from pydantic import BaseModel
from app.modules.lecture_notes.slide_parser import extract_pdf_slides
from app.modules.lecture_notes.alignment import align_transcript_to_slides, AlignedNoteItem

router = APIRouter(prefix="/lectures", tags=["Lecture Notes"])

class ParseSlidesRequest(BaseModel):
    pdf_path: str

class AlignLectureRequest(BaseModel):
    transcript_segments: List[Dict[str, Any]]
    slides: List[Dict[str, Any]]
    user_slide_clicks: Dict[int, float] | None = None

@router.post("/parse-slides")
def parse_slides_endpoint(req: ParseSlidesRequest):
    """Trích xuất danh sách slide và text từ file PDF bài giảng."""
    try:
        return extract_pdf_slides(req.pdf_path)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@router.post("/align", response_model=List[AlignedNoteItem])
def align_lecture_endpoint(req: AlignLectureRequest):
    """Căn lời giảng với slide và tính điểm độ mới Novelty Score."""
    try:
        return align_transcript_to_slides(req.transcript_segments, req.slides, req.user_slide_clicks)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
