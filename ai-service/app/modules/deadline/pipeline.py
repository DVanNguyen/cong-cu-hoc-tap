import hashlib
from datetime import datetime
import zoneinfo
from app.llm.client import get_llm_client
from app.modules.deadline.schemas import DeadlineExtractionRequest, DeadlineExtractionResponse, DeadlineExtractedItem
from app.modules.deadline.regex_filter import is_deadline_candidate
from app.modules.deadline.date_parser import parse_vietnamese_relative_date
from app.schemas import verify_evidence_grounding

VN_TZ = zoneinfo.ZoneInfo("Asia/Ho_Chi_Minh")
CONFIDENCE_THRESHOLD = 0.75  # Ngưỡng chuẩn theo đề cương

def process_deadline_message(request: DeadlineExtractionRequest) -> DeadlineExtractionResponse:
    # 1. Tính hash nội dung thô để đảm bảo tính Idempotent (không xử lý trùng)
    hash_obj = hashlib.sha256(f"{request.source}_{request.text.strip()}".encode("utf-8"))
    raw_hash = hash_obj.hexdigest()

    # 2. Tiền lọc rẻ trước đắt bằng Regex
    if not is_deadline_candidate(request.text):
        return DeadlineExtractionResponse(
            is_deadline_candidate=False,
            filtered_by_regex=True,
            raw_hash=raw_hash
        )

    # 3. Parse base_time
    try:
        base_time = datetime.fromisoformat(request.received_at)
        if base_time.tzinfo is None:
            base_time = base_time.replace(tzinfo=VN_TZ)
    except Exception:
        base_time = datetime.now(VN_TZ)

    # 4. Trích xuất thông tin bằng LLM với Pydantic Structured Output
    llm_client = get_llm_client()
    prompt = f"""
    Bạn là trợ lý AI trích xuất thông tin bài tập/deadline từ tin nhắn sinh viên.
    Thời điểm nhận tin: {base_time.isoformat()} (Múi giờ Asia/Ho_Chi_Minh).
    Môn học ưu tiên: {request.course_hint}.
    
    Nội dung tin nhắn:
    \"\"\"{request.text}\"\"\"
    
    Hãy trích xuất:
    - title: Tên bài tập/nhiệm vụ
    - course: Môn học
    - type: assignment, project, exam, reading, admin
    - due_expression: Cụm từ chỉ thời gian trong câu (ví dụ: "thứ 6 tuần sau", "23h59 ngày 20/10")
    - due_iso_candidate: Thời điểm quy đổi sang ISO 8601
    - intent: create (tạo mới), reschedule (dời hạn), cancel (hủy)
    - effort_est_min: Ước tính số phút làm bài
    - confidence: Độ tin cậy từ 0.0 đến 1.0
    - evidence_quote: Đoạn trích câu nói làm bằng chứng (bắt buộc phải trích nguyên văn từ nội dung tin nhắn)
    """
    
    task_extracted: DeadlineExtractedItem = llm_client.generate_structured(
        prompt=prompt,
        schema=DeadlineExtractedItem
    )

    # 5. Xác thực ngày giờ bằng Code xác định (Deterministic Date Cross-check)
    valid_date = False
    if task_extracted.due_expression:
        code_parsed_date = parse_vietnamese_relative_date(task_extracted.due_expression, base_time)
        if code_parsed_date:
            valid_date = True
            if task_extracted.due_iso_candidate:
                try:
                    llm_parsed_date = datetime.fromisoformat(task_extracted.due_iso_candidate)
                    diff_hours = abs((code_parsed_date - llm_parsed_date).total_seconds()) / 3600.0
                    if diff_hours > 2.0:
                        # Hai kết quả lệch nhau -> hạ độ tin cậy và ưu tiên dùng ngày do code xác định
                        task_extracted.confidence *= 0.7
                        task_extracted.due_iso_candidate = code_parsed_date.isoformat()
                except Exception:
                    task_extracted.due_iso_candidate = code_parsed_date.isoformat()
            else:
                task_extracted.due_iso_candidate = code_parsed_date.isoformat()

    # 6. Kiểm tra xác định bằng chứng gốc (verify_evidence_grounding)
    grounding_ok = verify_evidence_grounding(
        evidence_quote=task_extracted.evidence_quote,
        source_text=request.text,
        threshold=85.0
    )
    if not grounding_ok:
        task_extracted.confidence *= 0.5
        task_extracted.needs_confirmation = True

    # 7. Kiểm tra ngưỡng tin cậy để đưa vào Hộp xác nhận (Confirmation Queue)
    if task_extracted.confidence < CONFIDENCE_THRESHOLD or not valid_date:
        task_extracted.needs_confirmation = True

    return DeadlineExtractionResponse(
        is_deadline_candidate=True,
        filtered_by_regex=False,
        task=task_extracted,
        raw_hash=raw_hash
    )
