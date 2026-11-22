from typing import Optional, List
from pydantic import BaseModel, Field

class DeadlineExtractionRequest(BaseModel):
    text: str
    source: str = "telegram"  # "telegram", "email", "speech", "manual"
    received_at: str  # ISO timestamp, e.g. "2026-10-13T09:30:00+07:00"
    external_id: Optional[str] = None
    course_hint: Optional[str] = "Trí tuệ nhân tạo"

class DeadlineExtractedItem(BaseModel):
    title: str = Field(description="Tên ngắn gọn của công việc/bài tập")
    course: str = Field(default="Chung", description="Môn học liên quan")
    type: str = Field(default="assignment", description="assignment, project, exam, reading, admin")
    due_expression: str = Field(default="", description="Cụm từ gốc chỉ ngày giờ nộp trong tin nhắn")
    due_iso_candidate: Optional[str] = Field(default=None, description="Thời điểm ISO 8601 quy đổi từ due_expression")
    intent: str = Field(default="create", description="create (tạo mới), reschedule (dời hạn), cancel (hủy)")
    effort_est_min: int = Field(default=60, description="Ước lượng thời gian làm bài tính bằng phút")
    confidence: float = Field(default=1.0, description="Độ tin cậy từ 0.0 đến 1.0")
    evidence_quote: str = Field(default="", description="Đoạn văn bản gốc làm căn cứ trích xuất")
    needs_confirmation: bool = Field(default=False, description="True nếu độ tin cậy thấp dưới ngưỡng tau")

class DeadlineExtractionResponse(BaseModel):
    is_deadline_candidate: bool
    filtered_by_regex: bool = False
    task: Optional[DeadlineExtractedItem] = None
    raw_hash: str
