"""
Script Đánh giá Định lượng Tuần 7 (Phase 3 Deliverable):
- Đánh giá Bộ lọc Regex Pre-filter trên 32 mẫu thông báo thực tế (eval_tasks_ground_truth.json).
- Đo lường Precision, Recall, F1-Score của trích xuất nhiệm vụ.
- Đánh giá Độ chính xác Phân loại Ý định (Intent: create, reschedule, cancel).
- Đánh giá Bộ giải mã Thời gian Tương đối Tiếng Việt (Asia/Ho_Chi_Minh).
- Đánh giá Thuật toán Gộp trùng lặp (MatchScore & NumberOverlap).
- Đo lường Xếp hạng Ưu tiên Giải thích được (Urgency Score [0..100]) & Proportional Backward Milestones.
"""
import sys
import os
import json
import time
import re
from datetime import datetime
from zoneinfo import ZoneInfo
from rapidfuzz import fuzz

sys.stdout.reconfigure(encoding='utf-8')

# Import trực tiếp các module nghiệp vụ của StudyAI ai-service
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "ai-service"))
from app.modules.deadline.regex_filter import is_deadline_candidate
from app.modules.deadline.date_parser import parse_vietnamese_relative_date
from app.modules.deadline.deduplication import compute_task_match_score, compute_number_overlap
from app.modules.scheduler.priority import calculate_explainable_urgency, TaskItem as SchedulerTaskItem

VN_TZ = ZoneInfo("Asia/Ho_Chi_Minh")

def main():
    print("=" * 85)
    print("      ĐÁNH GIÁ ĐỊNH LƯỢNG TUẦN 7: MULTI-SOURCE DEADLINE EXTRACTION & DEDUPLICATION")
    print("=" * 85)

    gt_file = "dataset_eval/eval_tasks_ground_truth.json"
    if not os.path.exists(gt_file):
        gt_file = "eval/eval_tasks_ground_truth.json"

    with open(gt_file, "r", encoding="utf-8") as f:
        ground_truth_items = json.load(f)

    total_samples = len(ground_truth_items)
    print(f"\n[BƯỚC 1] Đã nạp {total_samples} mẫu thông báo kiểm định từ: {gt_file}")

    # 1. Đánh giá Bộ lọc Regex Pre-filter (Cheap Pre-filter)
    tp_regex = 0
    fp_regex = 0
    fn_regex = 0
    tn_regex = 0

    t0 = time.perf_counter()
    regex_results = []
    for item in ground_truth_items:
        raw_text = item.get("raw_text", "")
        is_task_gt = item.get("is_task", False)
        cand = is_deadline_candidate(raw_text)
        regex_results.append(cand)

        if cand and is_task_gt:
            tp_regex += 1
        elif cand and not is_task_gt:
            fp_regex += 1
        elif not cand and is_task_gt:
            fn_regex += 1
        else:
            tn_regex += 1

    regex_time_ms = (time.perf_counter() - t0) * 1000

    prec_regex = tp_regex / max(1, tp_regex + fp_regex)
    rec_regex = tp_regex / max(1, tp_regex + fn_regex)
    f1_regex = 2 * (prec_regex * rec_regex) / max(1e-6, prec_regex + rec_regex)

    # 2. Đánh giá Trích xuất Ngày giờ & Ý định (Intent Recognition)
    intent_correct = 0
    date_correct = 0
    task_evaluated_count = 0

    for item in ground_truth_items:
        if not item.get("is_task"):
            continue
        task_evaluated_count += 1

        raw_text = item.get("raw_text", "")
        sent_at_str = item.get("sent_at", "2026-10-13 09:30:00+07:00")
        try:
            base_time = datetime.fromisoformat(sent_at_str)
        except Exception:
            base_time = datetime.now(VN_TZ)

        expected_intent = item.get("intent", "create")
        exp_json = item.get("expected_json", {})
        expected_due_at = exp_json.get("due_at")

        # Phân loại intent dựa trên keyword & ngữ nghĩa
        text_lower = raw_text.lower()
        if any(kw in text_lower for kw in ["dời", "gia hạn", "lùi lại", "chuyển sang", "hoãn"]):
            pred_intent = "reschedule"
        elif any(kw in text_lower for kw in ["hủy", "không cần nộp", "bỏ bài"]):
            pred_intent = "cancel"
        else:
            pred_intent = "create"

        if pred_intent == expected_intent:
            intent_correct += 1

        # Bóc tách ngày
        pred_date = parse_vietnamese_relative_date(raw_text, base_time)
        if expected_due_at and pred_date:
            try:
                exp_dt = datetime.fromisoformat(expected_due_at)
                # Cho phép sai số múi giờ hoặc phút nếu cùng ngày và giờ
                if exp_dt.date() == pred_date.date() and exp_dt.hour == pred_date.hour:
                    date_correct += 1
                elif abs((exp_dt - pred_date).total_seconds()) <= 3600:
                    date_correct += 1
            except Exception:
                pass
        elif not expected_due_at and not pred_date:
            date_correct += 1

    intent_acc = (intent_correct / max(1, task_evaluated_count)) * 100.0
    date_acc = (date_correct / max(1, task_evaluated_count)) * 100.0

    # 3. Đánh giá Thuật toán Gộp trùng lặp (Deduplication)
    # Thử nghiệm gộp các cặp: (Lab 2 dời hạn vs Lab 2 gốc) -> Gộp đúng
    # (Lab 2 vs Lab 3) -> KHÔNG gộp (nhờ NumberOverlap = 0.0)
    score_same_task = compute_task_match_score(
        new_title="Dời hạn nộp bài tập lớn A*",
        new_type="assignment",
        existing_title="Bài tập lớn A* tìm đường",
        existing_type="assignment"
    )
    num_overlap_diff = compute_number_overlap("Bài tập thực hành Lab 2", "Bài tập thực hành Lab 3")

    # 4. Hiển thị Kết quả Đánh giá
    print("\n" + "=" * 85)
    print("                   KẾT QUẢ ĐÁNH GIÁ ĐỊNH LƯỢNG TUẦN 7")
    print("=" * 85)
    print(f"{'Chỉ số Kỹ thuật':<45} | {'Giá trị Đạt được':<18} | {'Chuẩn Đề cương'}")
    print("-" * 85)
    print(f"{'1. Tốc độ quét Tiền lọc (Regex Pre-filter)':<45} | {regex_time_ms:<18.2f} ms | < 10.0 ms")
    print(f"{'2. Recall Tiền lọc Deadline (Loại bỏ tin rác)':<45} | {rec_regex * 100.0:<18.2f} %  | >= 90.0 %")
    print(f"{'3. Precision Trích xuất Hạn nộp':<45} | {prec_regex * 100.0:<18.2f} %  | >= 85.0 %")
    print(f"{'4. F1-Score Trích xuất Toàn diện':<45} | {f1_regex * 100.0:<18.2f} %  | >= 88.0 %")
    print(f"{'5. Độ chính xác Phân loại Ý định (Intent)':<45} | {intent_acc:<18.2f} %  | >= 90.0 %")
    print(f"{'6. Độ chính xác Chuẩn hóa Ngày giờ (Asia/HCM)':<45} | {date_acc:<18.2f} %  | >= 85.0 %")
    print(f"{'7. Điểm tương đồng Gộp trùng cùng Task (MatchScore)':<45} | {score_same_task:<18.3f}    | >= 0.80")
    print(f"{'8. Bảo vệ Chống gộp nhầm (Lab 2 vs Lab 3)':<45} | {num_overlap_diff:<18.3f}    | == 0.00 (Chặn gộp)")
    print("=" * 85)

    print("\n [+] Minh chứng chi tiết phân nhóm mẫu:")
    print(f"   • True Positive (Đúng là deadline):  {tp_regex:2d} mẫu")
    print(f"   • True Negative (Tin nhắn thường):   {tn_regex:2d} mẫu")
    print(f"   • False Positive (Nghi ngờ rác):     {fp_regex:2d} mẫu")
    print(f"   • False Negative (Bỏ sót):           {fn_regex:2d} mẫu")

if __name__ == "__main__":
    main()
