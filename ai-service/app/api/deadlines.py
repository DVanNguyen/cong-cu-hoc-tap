from fastapi import APIRouter, HTTPException
from typing import List
from app.modules.deadline.schemas import DeadlineExtractionRequest, DeadlineExtractionResponse
from app.modules.deadline.pipeline import process_deadline_message

router = APIRouter(prefix="/deadlines", tags=["Deadlines"])

@router.post("/extract", response_model=DeadlineExtractionResponse)
def extract_single_deadline(request: DeadlineExtractionRequest):
    """Trích xuất deadline từ một tin nhắn đơn lẻ (Telegram forward / Email / Lời nói)."""
    try:
        return process_deadline_message(request)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Lỗi xử lý trích xuất deadline: {str(e)}")

@router.post("/batch", response_model=List[DeadlineExtractionResponse])
def extract_batch_deadlines(requests: List[DeadlineExtractionRequest]):
    """Xử lý hàng loạt tin nhắn từ email/hàng đợi Laravel."""
    results = []
    for req in requests:
        results.append(process_deadline_message(req))
    return results
