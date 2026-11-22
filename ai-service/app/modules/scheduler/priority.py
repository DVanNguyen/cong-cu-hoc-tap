import math
from datetime import datetime, timezone
from zoneinfo import ZoneInfo
from pydantic import BaseModel, Field
from typing import List, Optional

VIETNAM_TZ = ZoneInfo("Asia/Ho_Chi_Minh")

class TaskItem(BaseModel):
    id: str
    title: str
    due_at: str  # ISO timestamp
    estimated_effort_hours: float = 2.0
    remaining_effort_hours: float = 2.0
    task_type: str = "assignment"  # exam, project, assignment, quiz, other

class UrgencyScoreResult(BaseModel):
    task_id: str
    title: str
    hours_left: float
    hours_available: float
    workload_pressure_ratio: float
    urgency_score: int  # [0, 100]
    urgency_level: str  # "critical", "high", "normal"
    urgency_explanation: str

def get_task_type_weight(task_type: str) -> float:
    t = task_type.lower()
    if t in ["exam", "project"]:
        return 1.2
    elif t in ["assignment"]:
        return 1.0
    elif t in ["quiz"]:
        return 0.8
    return 1.0

def calculate_explainable_urgency(
    task: TaskItem, 
    current_time: Optional[datetime] = None
) -> UrgencyScoreResult:
    """
    Công thức tính Mức cấp bách giải thích được (Explainable Urgency Score):
    - E_rem: Số giờ công sức còn lại
    - H_left = max(0.5, (T_due - t_now) / 3600)
    - h_daily = 4.0 (Quỹ giờ học trung bình mỗi ngày)
    - H_avail = max(0.5, H_left * 4.0 / 24.0)
    - W_type: Trọng số loại việc (exam/project=1.2, assignment=1.0, quiz=0.8)
    - R = (E_rem / H_avail) * W_type
    - U = min(100, round(R * 50))
    """
    if current_time is None:
        current_time = datetime.now(VIETNAM_TZ)
    elif current_time.tzinfo is None:
        current_time = current_time.replace(tzinfo=VIETNAM_TZ)

    try:
        due_time = datetime.fromisoformat(task.due_at)
        if due_time.tzinfo is None:
            due_time = due_time.replace(tzinfo=VIETNAM_TZ)
    except Exception:
        # Nếu ngày không parse được
        return UrgencyScoreResult(
            task_id=task.id,
            title=task.title,
            hours_left=0.0,
            hours_available=0.0,
            workload_pressure_ratio=0.0,
            urgency_score=0,
            urgency_level="normal",
            urgency_explanation="Chưa xác định được hạn chót rõ ràng."
        )

    # 1. H_left
    seconds_left = (due_time - current_time).total_seconds()
    h_left = max(0.5, seconds_left / 3600.0)

    # 2. H_avail với h_daily = 4.0
    h_avail = max(0.5, h_left * (4.0 / 24.0))

    # 3. W_type & R
    e_rem = max(0.1, task.remaining_effort_hours)
    w_type = get_task_type_weight(task.task_type)
    r = (e_rem / h_avail) * w_type

    # 4. Điểm cấp bách U in [0, 100]
    u = min(100, round(r * 50))

    # 5. Giải thích tự động (urgency_explanation)
    days_left = round(h_left / 24.0, 1)
    if u >= 80:
        level = "critical"
        explanation = (
            f"Rất cấp bách: Cần ~{e_rem:.1f}h làm việc trong khi chỉ còn {h_left:.1f}h đến hạn "
            f"(quỹ giờ rảnh ước tính chỉ còn ~{h_avail:.1f}h)."
        )
    elif u >= 50:
        level = "high"
        explanation = f"Cần làm ngay hôm nay: Khối lượng còn ~{e_rem:.1f}h, hạn nộp sau {days_left} ngày."
    else:
        level = "normal"
        explanation = f"Trong tầm kiểm soát: Còn {days_left} ngày cho ~{e_rem:.1f}h công việc."

    return UrgencyScoreResult(
        task_id=task.id,
        title=task.title,
        hours_left=round(h_left, 1),
        hours_available=round(h_avail, 1),
        workload_pressure_ratio=round(r, 2),
        urgency_score=u,
        urgency_level=level,
        urgency_explanation=explanation
    )

class TaskPriorityResult(BaseModel):
    task_id: str
    title: str
    remaining_effort_hours: float
    available_hours: float
    load_ratio: float
    urgency_level: str
    priority_score: float
    recommended_action: str

def sort_tasks_by_edf(tasks: List[TaskItem], current_time: Optional[datetime] = None) -> List[TaskPriorityResult]:
    results = []
    for t in tasks:
        urg = calculate_explainable_urgency(t, current_time)
        results.append(TaskPriorityResult(
            task_id=t.id,
            title=t.title,
            remaining_effort_hours=t.remaining_effort_hours,
            available_hours=urg.hours_available,
            load_ratio=urg.workload_pressure_ratio,
            urgency_level=urg.urgency_level,
            priority_score=float(urg.urgency_score),
            recommended_action=urg.urgency_explanation
        ))
    results.sort(key=lambda x: x.priority_score, reverse=True)
    return results
