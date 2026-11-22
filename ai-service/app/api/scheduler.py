from fastapi import APIRouter
from typing import List
from datetime import datetime
import zoneinfo
from app.modules.scheduler.priority import TaskItem, TaskPriorityResult, sort_tasks_by_edf

router = APIRouter(prefix="/scheduler", tags=["Scheduler & Priority"])
VN_TZ = zoneinfo.ZoneInfo("Asia/Ho_Chi_Minh")

@router.post("/rank", response_model=List[TaskPriorityResult])
def rank_tasks_by_priority(tasks: List[TaskItem]):
    """Sắp xếp danh sách việc theo Earliest Deadline First (EDF) và tỷ lệ tải load_ratio."""
    current_time = datetime.now(VN_TZ)
    return sort_tasks_by_edf(tasks, current_time)
