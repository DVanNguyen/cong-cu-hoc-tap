from pydantic import BaseModel, Field
from typing import List, Literal, Optional
from rapidfuzz import fuzz

# ==============================================================================
# 1. SCHEMA MODULE GHI CHÚ BÀI GIẢNG & BẮT DEADLINE NÓI MIỆNG
# ==============================================================================
class TermExplanationItem(BaseModel):
    term: str = Field(description="Thuật ngữ chuyên ngành hoặc từ tiếng nước ngoài giảng viên nhắc tới")
    explanation: str = Field(description="Giải thích ngắn gọn, dễ hiểu trong ngữ cảnh bài giảng")
    source_type: Literal["course_material", "general_knowledge"] = Field(
        description="Nguồn giải thích: từ tài liệu môn học hay kiến thức chung"
    )
    source_reference: Optional[str] = Field(None, description="Vị trí bài/slide nếu lấy từ tài liệu môn học")

class SupplementaryNoteItem(BaseModel):
    bullet_content: str = Field(description="Ý giảng viên nói thêm hoặc làm rõ ngoài nội dung đã có trên slide")
    evidence_quote: str = Field(description="Câu trích dẫn nguyên văn từ transcript làm bằng chứng")
    start_time_sec: float = Field(description="Mốc thời gian bắt đầu (giây)")
    end_time_sec: float = Field(description="Mốc thời gian kết thúc (giây)")
    terms: List[TermExplanationItem] = Field(default_factory=list)

class OralDeadlineCandidate(BaseModel):
    raw_announcement_quote: str = Field(description="Nguyên văn câu giảng viên dặn nộp bài/kiểm tra")
    start_time_sec: float
    end_time_sec: float

class SlideNoteOutputSchema(BaseModel):
    slide_number: int
    supplementary_notes: List[SupplementaryNoteItem]
    oral_deadlines: List[OralDeadlineCandidate] = Field(default_factory=list)

# ==============================================================================
# 2. SCHEMA MODULE ĐỒ THỊ KHÁI NIỆM LIÊN BÀI
# ==============================================================================
class ExtractedConceptItem(BaseModel):
    name: str = Field(description="Tên khái niệm xuất hiện trong bài")
    english_or_vietnamese_alias: Optional[str] = Field(None, description="Tên tương đương tiếng Anh/Việt nếu có")
    mention_role: Literal["DEFINED", "USED"] = Field(
        description="DEFINED nếu đoạn văn đang định nghĩa khái niệm này; USED nếu chỉ nhắc tới để giải thích ý khác"
    )
    evidence_quote: str = Field(description="Đoạn văn bản nguyên gốc chứa khái niệm")
    slide_number: int
    timestamp_sec: Optional[float] = None

class ExtractedRelationItem(BaseModel):
    source_concept: str
    target_concept: str
    relation_type: Literal["prerequisite", "part_of", "example_of", "contrast"]
    evidence_quote: str = Field(description="Câu nguyên văn chứng minh mối quan hệ giữa 2 khái niệm")
    slide_number: int
    timestamp_sec: Optional[float] = None
    confidence: float = Field(ge=0.0, le=1.0)

class ConceptGraphOutputSchema(BaseModel):
    concepts: List[ExtractedConceptItem]
    relations: List[ExtractedRelationItem]

# ==============================================================================
# 3. SCHEMA MODULE TRÍCH XUẤT CÔNG VIỆC / DEADLINE
# ==============================================================================
class TaskMilestoneItem(BaseModel):
    step_order: int
    title: str
    estimated_hours: float
    suggested_deadline: Optional[str] = None

class ExtractedTaskOutputSchema(BaseModel):
    is_actionable_task: bool = Field(description="True nếu văn bản chứa thông báo bài tập/kiểm tra/deadline")
    title: Optional[str] = Field(None, description="Tên công việc ngắn gọn, rõ ràng")
    course_hint: Optional[str] = Field(None, description="Tên hoặc mã môn học nếu được nhắc tới")
    task_type: Literal["assignment", "project", "quiz", "exam", "other"] = "assignment"
    raw_due_time_expression: Optional[str] = Field(
        None, description="Cụm từ chỉ thời gian hạn nộp nguyên văn (VD: '23h59 thứ 6 tuần sau')"
    )
    is_deadline_extension: bool = Field(
        default=False, description="True nếu thông báo này dời/gia hạn cho một công việc đã có trước đó"
    )
    evidence_quote: str = Field(description="Trích dẫn nguyên văn câu thông báo hạn nộp trong văn bản gốc")
    estimated_effort_hours: float = Field(default=2.0, description="Số giờ ước lượng để hoàn thành")
    suggested_milestones: List[TaskMilestoneItem] = Field(default_factory=list)
    llm_confidence: float = Field(ge=0.0, le=1.0)

# ==============================================================================
# 4. HÀM KIỂM TRA BẰNG CHỨNG XÁC ĐỊNH (DETERMINISTIC EVIDENCE VERIFIER)
# ==============================================================================
def verify_evidence_grounding(evidence_quote: str, source_text: str, threshold: float = 85.0) -> bool:
    """
    Kiểm tra xác định: Đoạn bằng chứng LLM trích ra có thực sự nằm trong văn bản gốc hay không.
    Ngăn chặn triệt để việc LLM tự bịa đặt (hallucination).
    """
    if not evidence_quote or len(evidence_quote.strip()) < 5:
        return False
    # Kiểm tra khớp con chính xác hoặc khớp mờ (cho phép sai lệch dấu câu nhỏ)
    norm_quote = " ".join(evidence_quote.lower().split())
    norm_source = " ".join(source_text.lower().split())
    if norm_quote in norm_source:
        return True
    score = fuzz.partial_ratio(norm_quote, norm_source)
    return score >= threshold
