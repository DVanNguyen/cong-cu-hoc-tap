import re
from typing import List, Dict, Any, Optional, Tuple
from datetime import datetime
from rapidfuzz import fuzz

TASK_TERM_SYNONYMS = [
    (r"\bbài tập thực hành\b", "lab"),
    (r"\bthực hành\b", "lab"),
    (r"\bbài tập lớn\b", "project"),
    (r"\bđồ án\b", "project"),
    (r"\bbài tập nhóm\b", "group assignment"),
    (r"\bbài tập\b", "assignment"),
    (r"\bkiểm tra giữa kỳ\b", "midterm exam"),
    (r"\bthi giữa kỳ\b", "midterm exam"),
    (r"\bkiểm tra\b", "quiz"),
]

def normalize_task_title(title: str) -> str:
    """Chuẩn hóa thuật ngữ song ngữ Việt - Anh trong tiêu đề công việc."""
    if not title:
        return ""
    t = title.lower()
    for pattern, replacement in TASK_TERM_SYNONYMS:
        t = re.sub(pattern, replacement, t)
    return " ".join(t.split())

def extract_numbers(text: str) -> List[str]:
    """Trích xuất các chuỗi số trong tiêu đề để tránh gộp nhầm bài 1 với bài 2."""
    if not text:
        return []
    return re.findall(r'\d+', text)

def compute_number_overlap(title1: str, title2: str) -> float:
    """
    Tính NumberOverlap:
    - Nếu cả 2 đều có số: 1.0 nếu có ít nhất 1 số trùng nhau, 0.0 nếu số hoàn toàn khác nhau.
    - Nếu cả 2 đều không có số: 1.0.
    - Nếu 1 cái có số và 1 cái không: 0.5.
    """
    nums1 = set(extract_numbers(title1))
    nums2 = set(extract_numbers(title2))

    if not nums1 and not nums2:
        return 1.0
    if bool(nums1) != bool(nums2):
        return 0.5
    
    if nums1.intersection(nums2):
        return 1.0
    return 0.0

def compute_task_match_score(
    new_title: str,
    new_type: str,
    existing_title: str,
    existing_type: str
) -> float:
    """
    MatchScore(T_new, T_i) = 0.6 * CosineSim + 0.2 * I(type) + 0.2 * NumberOverlap
    Áp dụng chuẩn hóa thuật ngữ song ngữ Việt - Anh để tăng độ chính xác gộp trùng.
    """
    norm_t1 = normalize_task_title(new_title)
    norm_t2 = normalize_task_title(existing_title)

    sim_title = fuzz.token_set_ratio(norm_t1, norm_t2) / 100.0
    same_type = 1.0 if new_type.lower() == existing_type.lower() else 0.0
    num_overlap = compute_number_overlap(new_title, existing_title)

    return 0.6 * sim_title + 0.2 * same_type + 0.2 * num_overlap

def match_existing_task(
    new_task: Dict[str, Any],
    existing_tasks: List[Dict[str, Any]],
    threshold: float = 0.78
) -> Tuple[Optional[Dict[str, Any]], float, str]:
    """
    Tìm công việc trùng trong danh sách công việc hiện có của cùng môn học:
    Trả về (matched_task, match_score, action: 'merge_evidence' | 'reschedule' | 'create_new')
    """
    new_title = new_task.get("title", "")
    new_type = new_task.get("task_type", "assignment")
    is_extension = new_task.get("is_deadline_extension", False)
    new_due_str = new_task.get("due_at")

    best_match = None
    best_score = 0.0

    for task in existing_tasks:
        score = compute_task_match_score(
            new_title=new_title,
            new_type=new_type,
            existing_title=task.get("title", ""),
            existing_type=task.get("task_type", task.get("type", "assignment"))
        )
        if score > best_score:
            best_score = score
            best_match = task

    if best_match and best_score >= threshold:
        if is_extension:
            return best_match, best_score, "reschedule"
        
        # Kiểm tra khoảng cách hạn chót nếu cả hai đều có ngày
        exist_due_str = best_match.get("due_at")
        if new_due_str and exist_due_str:
            try:
                d_new = datetime.fromisoformat(new_due_str)
                d_exist = datetime.fromisoformat(exist_due_str)
                diff_hours = abs((d_new - d_exist).total_seconds()) / 3600.0
                if diff_hours <= 24.0:
                    return best_match, best_score, "merge_evidence"
                else:
                    return best_match, best_score, "reschedule"
            except Exception:
                return best_match, best_score, "merge_evidence"
        return best_match, best_score, "merge_evidence"

    return None, best_score, "create_new"
