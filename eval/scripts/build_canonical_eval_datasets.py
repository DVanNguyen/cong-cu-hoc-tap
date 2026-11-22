import json
import csv
import os

target_dir = r"c:\Users\Nguyen\AndroidStudioProjects\StudyAI\dataset_eval"
eval_dir = r"c:\Users\Nguyen\AndroidStudioProjects\StudyAI\eval"

os.makedirs(target_dir, exist_ok=True)
os.makedirs(eval_dir, exist_ok=True)

# ==============================================================================
# 1. eval_asr_wer.json (Đánh giá nhận dạng giọng nói & thuật ngữ song ngữ)
# ==============================================================================
asr_segments = [
    {
        "audio_path": "dataset_eval/audio/lecture_01_agents.wav",
        "segment_id": 1,
        "start_sec": 0.0,
        "end_sec": 12.5,
        "ground_truth_text": "Hôm nay chúng ta bắt đầu Chapter 2 về Intelligent Agents trong hệ thống trí tuệ nhân tạo.",
        "domain_terms": ["Chapter 2", "Intelligent Agents"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_01_agents.wav",
        "segment_id": 2,
        "start_sec": 13.0,
        "end_sec": 28.0,
        "ground_truth_text": "Một agent tương tác với environment thông qua sensors và actuators để nhận thức và hành động.",
        "domain_terms": ["agent", "environment", "sensors", "actuators"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_01_agents.wav",
        "segment_id": 3,
        "start_sec": 28.5,
        "end_sec": 48.0,
        "ground_truth_text": "Trong phần mềm, sensors là input packet hoặc file, còn actuators là API response hoặc database write.",
        "domain_terms": ["sensors", "actuators", "input packet", "database write"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_01_agents.wav",
        "segment_id": 4,
        "start_sec": 48.5,
        "end_sec": 65.0,
        "ground_truth_text": "Hàm tác tử agent function f ánh xạ từ percept sequence sang action.",
        "domain_terms": ["agent function", "percept sequence", "action"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_01_agents.wav",
        "segment_id": 5,
        "start_sec": 65.5,
        "end_sec": 85.0,
        "ground_truth_text": "Mô hình PEAS bao gồm Performance measure, Environment, Actuators, và Sensors.",
        "domain_terms": ["PEAS", "Performance measure", "Environment", "Actuators", "Sensors"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_01_agents.wav",
        "segment_id": 6,
        "start_sec": 85.5,
        "end_sec": 105.0,
        "ground_truth_text": "Đối với xe tự hành automated taxi, performance measure là an toàn, tốc độ, và tiện nghi hành khách.",
        "domain_terms": ["automated taxi", "performance measure"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_01_agents.wav",
        "segment_id": 7,
        "start_sec": 105.5,
        "end_sec": 125.0,
        "ground_truth_text": "Môi trường fully observable cho phép agent quan sát toàn bộ trạng thái hệ thống tại mọi thời điểm.",
        "domain_terms": ["fully observable", "agent", "state"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_01_agents.wav",
        "segment_id": 8,
        "start_sec": 125.5,
        "end_sec": 145.0,
        "ground_truth_text": "Ngược lại, partially observable environment chứa nhiều yếu tố ẩn hoặc nhiễu trong sensor.",
        "domain_terms": ["partially observable environment", "sensor"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_02_search.wav",
        "segment_id": 9,
        "start_sec": 0.0,
        "end_sec": 20.0,
        "ground_truth_text": "Sang Chapter 3, chúng ta khảo sát bài toán State Space Search trong không gian trạng thái.",
        "domain_terms": ["State Space Search", "không gian trạng thái"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_02_search.wav",
        "segment_id": 10,
        "start_sec": 20.5,
        "end_sec": 40.0,
        "ground_truth_text": "Thuật toán Breadth-First Search duyệt theo chiều rộng, dùng queue FIFO và đảm bảo tính optimal nếu chi phí đồng nhất.",
        "domain_terms": ["Breadth-First Search", "queue FIFO", "optimal"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_02_search.wav",
        "segment_id": 11,
        "start_sec": 40.5,
        "end_sec": 60.0,
        "ground_truth_text": "Trong khi đó Depth-First Search dùng stack LIFO, tiết kiệm memory nhưng có thể rơi vào infinite loop.",
        "domain_terms": ["Depth-First Search", "stack LIFO", "memory", "infinite loop"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_02_search.wav",
        "segment_id": 12,
        "start_sec": 60.5,
        "end_sec": 80.0,
        "ground_truth_text": "Uniform Cost Search mở rộng node có đường đi ngắn nhất g(n), tương đương với thuật toán Dijkstra.",
        "domain_terms": ["Uniform Cost Search", "Dijkstra", "g(n)"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_03_heuristic.wav",
        "segment_id": 13,
        "start_sec": 0.0,
        "end_sec": 22.0,
        "ground_truth_text": "Heuristic Search sử dụng hàm lượng giá h(n) để ước lượng chi phí từ node hiện tại đến goal state.",
        "domain_terms": ["Heuristic Search", "h(n)", "goal state"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_03_heuristic.wav",
        "segment_id": 14,
        "start_sec": 22.5,
        "end_sec": 45.0,
        "ground_truth_text": "Thuật toán A* kết hợp f(n) = g(n) + h(n), cân bằng giữa chi phí thực tế và ước lượng heuristic.",
        "domain_terms": ["A*", "f(n)", "g(n)", "h(n)", "heuristic"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_03_heuristic.wav",
        "segment_id": 15,
        "start_sec": 45.5,
        "end_sec": 68.0,
        "ground_truth_text": "Một heuristic được gọi là admissible nếu h(n) không bao giờ đánh giá vượt quá chi phí thực tế true cost.",
        "domain_terms": ["admissible heuristic", "true cost"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_03_heuristic.wav",
        "segment_id": 16,
        "start_sec": 68.5,
        "end_sec": 90.0,
        "ground_truth_text": "Điều kiện consistency hay monotonicity yêu cầu h(n) <= c(n, a, n') + h(n') cho mọi bước chuyển trạng thái.",
        "domain_terms": ["consistency", "monotonicity", "trạng thái"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_03_heuristic.wav",
        "segment_id": 17,
        "start_sec": 90.5,
        "end_sec": 110.0,
        "ground_truth_text": "Phương pháp Hill Climbing Search leo đồi có thể bị kẹt ở local extrema hoặc plateau.",
        "domain_terms": ["Hill Climbing Search", "local extrema", "plateau"]
    },
    {
        "audio_path": "dataset_eval/audio/lecture_03_heuristic.wav",
        "segment_id": 18,
        "start_sec": 110.5,
        "end_sec": 130.0,
        "ground_truth_text": "Để thoát khỏi cực trị cục bộ, Simulated Annealing chấp nhận các bước đi xấu hơn với xác suất giảm dần theo nhiệt độ T.",
        "domain_terms": ["Simulated Annealing", "cực trị cục bộ", "nhiệt độ T"]
    }
]

with open(os.path.join(target_dir, "eval_asr_wer.json"), "w", encoding="utf-8") as f:
    json.dump(asr_segments, f, ensure_ascii=False, indent=2)
with open(os.path.join(eval_dir, "eval_asr_wer.json"), "w", encoding="utf-8") as f:
    json.dump(asr_segments, f, ensure_ascii=False, indent=2)
print("Saved eval_asr_wer.json: 18 items")

# ==============================================================================
# 2. eval_slide_alignment.json (Đánh giá căn chỉnh slide)
# ==============================================================================
alignment_data = [
    {"segment_id": 1, "start_sec": 0.0, "end_sec": 12.5, "true_slide_number": 1},
    {"segment_id": 2, "start_sec": 13.0, "end_sec": 28.0, "true_slide_number": 2},
    {"segment_id": 3, "start_sec": 28.5, "end_sec": 48.0, "true_slide_number": 2},
    {"segment_id": 4, "start_sec": 48.5, "end_sec": 65.0, "true_slide_number": 3},
    {"segment_id": 5, "start_sec": 65.5, "end_sec": 85.0, "true_slide_number": 4},
    {"segment_id": 6, "start_sec": 85.5, "end_sec": 105.0, "true_slide_number": 5},
    {"segment_id": 7, "start_sec": 105.5, "end_sec": 125.0, "true_slide_number": 6},
    {"segment_id": 8, "start_sec": 125.5, "end_sec": 145.0, "true_slide_number": 6},
    {"segment_id": 9, "start_sec": 145.5, "end_sec": 175.0, "true_slide_number": 7},
    {"segment_id": 10, "start_sec": 175.5, "end_sec": 205.0, "true_slide_number": 8},
    {"segment_id": 11, "start_sec": 205.5, "end_sec": 235.0, "true_slide_number": 9},
    {"segment_id": 12, "start_sec": 235.5, "end_sec": 265.0, "true_slide_number": 10},
    {"segment_id": 13, "start_sec": 265.5, "end_sec": 300.0, "true_slide_number": 11},
    {"segment_id": 14, "start_sec": 300.5, "end_sec": 335.0, "true_slide_number": 12},
    {"segment_id": 15, "start_sec": 335.5, "end_sec": 370.0, "true_slide_number": 12},
    {"segment_id": 16, "start_sec": 370.5, "end_sec": 410.0, "true_slide_number": 13},
    {"segment_id": 17, "start_sec": 410.5, "end_sec": 460.0, "true_slide_number": 14},
    {"segment_id": 18, "start_sec": 460.5, "end_sec": 510.0, "true_slide_number": 15}
]

with open(os.path.join(target_dir, "eval_slide_alignment.json"), "w", encoding="utf-8") as f:
    json.dump(alignment_data, f, ensure_ascii=False, indent=2)
with open(os.path.join(eval_dir, "eval_slide_alignment.json"), "w", encoding="utf-8") as f:
    json.dump(alignment_data, f, ensure_ascii=False, indent=2)
print("Saved eval_slide_alignment.json: 18 items")

# ==============================================================================
# 3. eval_concept_syllabus.json (Đánh giá độ bao phủ đồ thị khái niệm)
# ==============================================================================
concept_syllabus = {
    "course_code": "AI2026",
    "course_name": "Trí tuệ nhân tạo (Artificial Intelligence)",
    "syllabus_concepts": [
        {"canonical_name": "Tác tử thông minh (Intelligent Agent)", "aliases": ["Intelligent Agent", "Tác tử", "Agent"]},
        {"canonical_name": "Môi trường (Environment)", "aliases": ["Environment", "Môi trường tác tử"]},
        {"canonical_name": "Cảm biến (Sensors)", "aliases": ["Sensors", "Cảm biến"]},
        {"canonical_name": "Bộ tác động (Actuators)", "aliases": ["Actuators", "Cơ cấu tác động"]},
        {"canonical_name": "Chuỗi nhận thức (Percept Sequence)", "aliases": ["Percept Sequence", "Dãy nhận thức"]},
        {"canonical_name": "Hàm tác tử (Agent Function)", "aliases": ["Agent Function", "Hàm quyết định tác tử"]},
        {"canonical_name": "Khuôn mẫu PEAS (PEAS Framework)", "aliases": ["PEAS", "PEAS Framework"]},
        {"canonical_name": "Không gian trạng thái (State Space)", "aliases": ["State Space", "Không gian bài toán"]},
        {"canonical_name": "Tìm kiếm theo chiều rộng (Breadth-First Search)", "aliases": ["BFS", "Breadth-First Search"]},
        {"canonical_name": "Tìm kiếm theo chiều sâu (Depth-First Search)", "aliases": ["DFS", "Depth-First Search"]},
        {"canonical_name": "Tìm kiếm chi phí đồng nhất (Uniform Cost Search)", "aliases": ["UCS", "Uniform Cost Search"]},
        {"canonical_name": "Tìm kiếm Heuristic (Heuristic Search)", "aliases": ["Heuristic Search", "Hàm đánh giá"]},
        {"canonical_name": "Thuật toán A* (A* Search)", "aliases": ["A*", "A* Search", "A Star"]},
        {"canonical_name": "Heuristic chấp nhận được (Admissible Heuristic)", "aliases": ["Admissible Heuristic", "Admissible"]},
        {"canonical_name": "Heuristic nhất quán (Consistent Heuristic)", "aliases": ["Consistent Heuristic", "Monotonic Heuristic"]},
        {"canonical_name": "Tìm kiếm leo đồi (Hill Climbing)", "aliases": ["Hill Climbing", "Leo đồi"]},
        {"canonical_name": "Mô phỏng tôi thép (Simulated Annealing)", "aliases": ["Simulated Annealing", "Luyện kim"]}
    ]
}

with open(os.path.join(target_dir, "eval_concept_syllabus.json"), "w", encoding="utf-8") as f:
    json.dump(concept_syllabus, f, ensure_ascii=False, indent=2)
with open(os.path.join(eval_dir, "eval_concept_syllabus.json"), "w", encoding="utf-8") as f:
    json.dump(concept_syllabus, f, ensure_ascii=False, indent=2)
print("Saved eval_concept_syllabus.json: 17 concepts")

# ==============================================================================
# 4. eval_tasks_ground_truth.json (Đánh giá trích xuất & gộp trùng công việc)
# ==============================================================================
tasks_data = []
with open(os.path.join(eval_dir, "raw_messages.csv"), "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        is_task = (row["has_deadline"] == "1")
        msg_id = int(row["id"])
        
        canonical_cluster = None
        if "PEAS" in row["title"]:
            canonical_cluster = "cluster_peas_assignment"
        elif "A*" in row["title"] or "Heuristic" in row["title"]:
            canonical_cluster = "cluster_astar_assignment"
        elif "Chapter 2" in row["title"]:
            canonical_cluster = "cluster_chapter2_assignment"
        elif "Lab 2" in row["title"] or "Lab 3" in row["title"]:
            canonical_cluster = "cluster_lab_assignments"
        elif is_task:
            canonical_cluster = f"cluster_task_{msg_id:02d}"

        entry = {
            "source_id": f"msg_{msg_id:02d}",
            "source_channel": row["source"],
            "sent_at": row["received_at"] + ("+07:00" if "+07:00" not in row["received_at"] else ""),
            "raw_text": row["raw_text"],
            "is_task": is_task,
            "canonical_task_cluster_id": canonical_cluster if is_task else None,
            "intent": row.get("intent", "create"),
            "expected_json": {
                "title": row["title"],
                "course": row["course"],
                "due_at": row["due_iso_candidate"] if row["due_iso_candidate"] else None,
                "task_type": row["type"] if row["type"] in ["assignment", "project", "quiz", "exam", "other"] else "assignment"
            } if is_task else None
        }
        tasks_data.append(entry)

with open(os.path.join(target_dir, "eval_tasks_ground_truth.json"), "w", encoding="utf-8") as f:
    json.dump(tasks_data, f, ensure_ascii=False, indent=2)
with open(os.path.join(eval_dir, "eval_tasks_ground_truth.json"), "w", encoding="utf-8") as f:
    json.dump(tasks_data, f, ensure_ascii=False, indent=2)
print(f"Saved eval_tasks_ground_truth.json: {len(tasks_data)} items")
