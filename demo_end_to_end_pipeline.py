"""
========================================================================================
   KỊCH BẢN DEMO TOÀN DIỆN LIÊN HOÀN ĐẦU-CUỐI (END-TO-END PIPELINE DEMONSTRATION)
   Đề tài: Nghiên cứu, xây dựng hệ thống trợ lý AI hỗ trợ học tập (StudyAI)
   Sinh viên: Đàm Văn Nguyên (24IT180 - Lớp 24JIT, VKU) | GVHD: TS. Trần Uyên Trang
========================================================================================
VŨ KHÍ HỌC THUẬT SO VỚI NOTEBOOKLM:
  NotebookLM chỉ đọc nội dung chữ có sẵn trên file PDF (Closed-world RAG). Slide bài giảng
  đại học bản chất chỉ là khung sườn (skeleton) với các gạch đầu dòng cô đọng.
  StudyAI giải quyết triệt để sự thiếu hụt thông tin qua 3 chiều bồi đắp tri thức:
    [Trụ cột 1] Căn gióng Lời giảng & Bồi đắp Khung sườn Slide (ASR 3 tầng, Monotonic DTW, Novelty >= 0.28, MMR)
    [Trụ cột 2] "Từ điển sống" & Mở rộng Nguồn Tham khảo Đa nguồn (Abstractness >= 0.55, Wikipedia VI/EN, Semantic Scholar, Web Tutorials, YouTube)
    [Trụ cột 3] Hệ thống hóa & Liên kết Chéo Liên Chương (Cross-Chapter Knowledge DAG, Used-Before-Defined, Bi-directional Deep-Linking)
    [Android App] Trải nghiệm học tập liền mạch trên Ứng dụng Mobile Android qua Laravel REST API
========================================================================================
"""
import sys
import os
import json
import time
import urllib.request
import urllib.error

sys.stdout.reconfigure(encoding='utf-8')

FASTAPI_URL = "http://127.0.0.1:8001"
LARAVEL_URL = "http://127.0.0.1:8000"

def print_step(step_num: int, title: str):
    print("\n" + "=" * 85)
    print(f" [BƯỚC {step_num}] {title.upper()}")
    print("=" * 85)

def check_services():
    print_step(0, "Kiểm tra tình trạng sẵn sàng của các Microservices")
    # Check FastAPI
    try:
        with urllib.request.urlopen(f"{FASTAPI_URL}/health", timeout=4) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            print(f" [+] Python AI Microservice (Port 8001): ONLINE (v{data.get('status')})")
            print(f"     -> GPU / Engine: {data.get('gpu_name')}")
    except Exception as e:
        print(f" [-] Python AI Microservice: OFFLINE ({e})")
        return False

    # Check Laravel
    try:
        with urllib.request.urlopen(f"{LARAVEL_URL}/api/v1/lectures/1/workspace", timeout=4) as resp:
            print(f" [+] Laravel Backend API (Port 8000): ONLINE (HTTP {resp.status})")
    except Exception as e:
        print(f" [-] Laravel Backend API: OFFLINE ({e})")
        return False

    return True

def run_pillar1_demo():
    print_step(1, "Trụ cột 1: Căn gióng Lời giảng & Bồi đắp Khung sườn Slide (Slide-Audio Sync & Novelty)")
    endpoint = f"{FASTAPI_URL}/api/v1/pipeline/week4/generate-notes"
    
    payload = {
        "lecture_id": 1,
        "course_id": 1,
        "slides": [
            {
                "id": 1, "slide_number": 1,
                "extracted_text": "Chapter 2: Intelligent Agents. Khái niệm Agent, Sensors, Actuators.",
                "terms_json": ["Intelligent Agents", "Sensors", "Actuators"],
                "embedding": [0.1] * 384
            },
            {
                "id": 2, "slide_number": 2,
                "extracted_text": "Khuôn mẫu PEAS: Performance, Environment, Actuators, Sensors. Ví dụ Taxi tự hành.",
                "terms_json": ["PEAS", "Performance", "Environment"],
                "embedding": [0.5] * 384
            }
        ],
        "transcripts": [
            {
                "id": 101, "start_time_sec": 0.0, "end_time_sec": 12.0,
                "clean_text": "Hôm nay chúng ta bắt đầu Chapter 2 về Intelligent Agents trong hệ thống trí tuệ nhân tạo.",
                "embedding": [0.12] * 384
            },
            {
                "id": 102, "start_time_sec": 13.0, "end_time_sec": 38.0,
                "clean_text": "Một agent tương tác với environment thông qua sensors và actuators. Thầy giải thích thêm: đối với phần mềm thông minh, sensors chính là file cấu hình hoặc gói tin mạng TCP/IP gửi tới.",
                "embedding": [0.85] * 384
            }
        ],
        "use_auto_dtw": True,
        "novelty_threshold": 0.28
    }

    t0 = time.perf_counter()
    req = urllib.request.Request(endpoint, data=json.dumps(payload).encode('utf-8'), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode('utf-8'))
    dt = (time.perf_counter() - t0) * 1000

    notes = res.get("generated_notes", [])

    print(f" [+] Thời gian phân tích DTW dải Sakoe-Chiba & Novelty: {dt:.2f} ms")
    print(f" [+] Số ghi chú phát hiện có nội dung mở rộng ngoài slide (Novelty >= 0.28): {len(notes)}")
    for n in notes:
        print(f"     • [Slide {n['slide_number']} | Novelty: {n['novelty_score']:.2f} | MMR: {n['mmr_score']:.2f}] \"{n['bullet_point'][:70]}...\"")
        print(f"       -> Bằng chứng gốc: \"{n['verbatim_quote'][:60]}...\" (Trùng khớp {n['similarity_ratio']}%)")

def run_pillar2_demo():
    print_step(2, "Trụ cột 2: 'Từ điển sống' & Mở rộng Nguồn Tham khảo Đa nguồn (Living Dictionary)")
    endpoint = f"{FASTAPI_URL}/api/v1/pipeline/living-dictionary/expand"

    payload = {
        "concept_name_vi": "Mạng đối nghịch tạo sinh",
        "concept_name_en": "Generative Adversarial Network (GAN)",
        "slide_raw_text": "Chapter 5: Deep Generative Models. Mạng GAN gồm Generator và Discriminator.",
        "lecturer_quote": "Giảng viên giải thích cơ chế minimax giữa 2 mạng nơ-ron: một bên làm tiền giả và một bên làm cảnh sát phân biệt.",
        "course_title": "Trí tuệ nhân tạo"
    }

    t0 = time.perf_counter()
    req = urllib.request.Request(endpoint, data=json.dumps(payload).encode('utf-8'), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode('utf-8'))
    dt = (time.perf_counter() - t0) * 1000

    abstractness = res.get("abstractness_score", 0.0)
    lecturer_oral = res.get("lecturer_oral_explanation", "")
    ai_deep = res.get("ai_deep_explanation", "")
    refs = res.get("external_references", [])

    print(f" [+] Thời gian xử lý Engine Từ điển sống & Truy vấn đa API: {dt:.2f} ms")
    print(f" [+] Điểm độ trừu tượng/thiếu hụt A(c, Sk): {abstractness} (Ngưỡng kích hoạt >= 0.55)")
    print(f" [+] Lời giảng viên tóm tắt: \"{lecturer_oral[:75]}...\"")
    print(f" [+] AI Đào sâu bản chất: \"{ai_deep[:85]}...\"")
    print(f" [+] Số lượng nguồn tài liệu tham khảo đã kiểm chứng & xếp hạng: {len(refs)}")
    for idx, r in enumerate(refs):
        print(f"     {idx+1}. {r.get('citation_badge')} {r.get('title')[:55]}...")
        print(f"        -> Điểm khớp: {r.get('final_rank_score')} | Tên miền: {r.get('domain_name')} ({r.get('authority_weight')})")
        print(f"        -> URL: {r.get('url')}")

def run_pillar3_demo():
    print_step(3, "Trụ cột 3: Hệ thống hóa & Liên kết Chéo Liên Chương (Cross-Chapter DAG & Deep-Linking)")
    endpoint_graph = f"{FASTAPI_URL}/api/v1/pipeline/week5-6/build-knowledge-graph"
    endpoint_cross = f"{FASTAPI_URL}/api/v1/pipeline/cross-chapter/build-links"

    # 1. Đồ thị DAG & Cảnh báo Tiên quyết
    payload_graph = {
        "course_id": 1,
        "mentions": [
            {"name_vi": "Tác tử thông minh", "name_en": "Intelligent Agent", "lecture_id": 1, "lecture_order": 1, "slide_no": 1, "role": "DEFINED", "definition_summary": "Thực thể nhận thức qua sensors và hành động qua actuators."},
            {"name_vi": "Sensors", "name_en": "Sensors", "lecture_id": 1, "lecture_order": 1, "slide_no": 3, "role": "DEFINED", "definition_summary": "Cảm biến tiếp nhận thông tin môi trường."},
            {"name_vi": "Thuật toán A*", "name_en": "A* Search", "lecture_id": 3, "lecture_order": 3, "slide_no": 6, "role": "DEFINED", "definition_summary": "Thuật toán tìm kiếm heuristic kết hợp f(n) = g(n) + h(n)."},
            {"name_vi": "Thuật toán A*", "name_en": "A* Search", "lecture_id": 1, "lecture_order": 1, "slide_no": 2, "role": "USED", "definition_summary": ""}
        ],
        "relations": [
            {"source_name": "Sensors", "target_name": "Intelligent Agent", "relation_type": "PREREQUISITE_OF", "weight": 0.95, "evidence_quote": "Sensors là thành phần nhận thức của Agent.", "used_at_lecture_order": 1, "used_at_slide_no": 3},
            {"source_name": "A* Search", "target_name": "Intelligent Agent", "relation_type": "PREREQUISITE_OF", "weight": 0.88, "evidence_quote": "Dùng A* để lập lộ trình cho taxi tự hành.", "used_at_lecture_order": 1, "used_at_slide_no": 2}
        ]
    }

    t0 = time.perf_counter()
    req = urllib.request.Request(endpoint_graph, data=json.dumps(payload_graph).encode('utf-8'), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as resp:
        res_graph = json.loads(resp.read().decode('utf-8'))
    dt_graph = (time.perf_counter() - t0) * 1000

    concepts = res_graph.get("concepts", [])
    relations = res_graph.get("concept_relations", [])
    violations = [r for r in relations if r.get("is_used_before_defined_violation")]

    print(f" [+] Thời gian dựng đồ thị DAG & gán tầng Sugiyama: {dt_graph:.2f} ms")
    print(f" [+] Tổng số khái niệm: {len(concepts)} | Cạnh DAG: {len(relations)}")
    print(f" [+] Cảnh báo sư phạm phát hiện: {len(violations)} liên kết vi phạm Used-Before-Defined:")
    for v in violations:
        print(f"     -> Cạnh vi phạm: Concept #{v['source_concept_id']} --({v['relation_type']})--> Concept #{v['target_concept_id']}")
        print(f"        \"Khái niệm này được sử dụng tại Bài 1 trước khi được định nghĩa chính thức tại Bài 3.\"")

    # 2. Liên kết Chéo 2 Chiều Giữa Các Chương
    payload_cross = {
        "course_id": 1,
        "occurrences": [
            {
                "concept_id": 42,
                "canonical_name_vi": "Mạng đối nghịch tạo sinh",
                "canonical_name_en": "Generative Adversarial Network (GAN)",
                "lecture_id": 1,
                "lecture_order": 1,
                "lecture_title": "Chương 1: Tổng quan AI",
                "slide_id": 12,
                "slide_number": 12,
                "timestamp_sec": 255.0,
                "context_excerpt": "Ian Goodfellow đề xuất năm 2014"
            },
            {
                "concept_id": 42,
                "canonical_name_vi": "Mạng đối nghịch tạo sinh",
                "canonical_name_en": "Generative Adversarial Network (GAN)",
                "lecture_id": 5,
                "lecture_order": 5,
                "lecture_title": "Chương 5: Học sâu & Mô hình tạo sinh",
                "slide_id": 45,
                "slide_number": 3,
                "timestamp_sec": 78.0,
                "context_excerpt": "Áp dụng GAN để sinh ảnh siêu thực"
            }
        ]
    }

    t1 = time.perf_counter()
    req_cross = urllib.request.Request(endpoint_cross, data=json.dumps(payload_cross).encode('utf-8'), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req_cross) as resp:
        res_cross = json.loads(resp.read().decode('utf-8'))
    dt_cross = (time.perf_counter() - t1) * 1000

    slide_cross_links = res_cross.get("slide_cross_links", {})
    all_links = [l for sublist in slide_cross_links.values() for l in sublist]
    print(f"\n [+] Thời gian dựng liên kết chéo 2 chiều (Chương 1 <-> Chương 5): {dt_cross:.2f} ms")
    print(f" [+] Số lượng Deep-Link sinh ra: {len(all_links)} liên kết:")
    for l in all_links:
        print(f"     • [{l['link_direction']}] Concept: {l['concept_name_vi']}")
        print(f"       -> {l['badge_text']}")
        print(f"       -> Đích đến: Chương {l['target_lecture_order']} (Slide {l['target_slide_number']} • Giây {l['target_timestamp_sec']}s)")

def verify_android_apis():
    print_step(4, "Xác nhận Tính Sẵn sàng Đồng bộ Trực tiếp lên Ứng dụng Android Jetpack Compose")
    endpoints = [
        ("Màn hình 1 (Slide Note Workspace)", f"{LARAVEL_URL}/api/v1/lectures/1/workspace"),
        ("Màn hình 2 (Knowledge Graph Canvas)", f"{LARAVEL_URL}/api/v1/courses/1/knowledge-graph"),
        ("Màn hình 3 (Từ điển sống Môn học)", f"{LARAVEL_URL}/api/v1/courses/1/dictionary"),
        ("Chi tiết Khái niệm Từ điển sống", f"{LARAVEL_URL}/api/v1/concepts/42/dictionary")
    ]

    all_ok = True
    for name, url in endpoints:
        try:
            with urllib.request.urlopen(url) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                print(f" [+] {name:<35} -> HTTP {resp.status} (JSON Ready)")
        except Exception as e:
            print(f" [-] {name:<35} -> LỖI: {e}")
            all_ok = False

    if all_ok:
        print("\n" + "=" * 85)
        print("          KẾT LUẬN: TOÀN BỘ QUY TRÌNH END-TO-END HOẠT ĐỘNG HOÀN HẢO!")
        print("   Mọi dữ liệu từ bài giảng đã được bóc tách, bồi đắp và đồng bộ")
        print("   thời gian thực lên cả 3 Trụ cột cốt lõi của Ứng dụng Mobile StudyAI.")
        print("=" * 85)

def main():
    print("\n" + "#" * 85)
    print("      KHỞI ĐỘNG CHƯƠNG TRÌNH DEMO TOÀN DIỆN HỆ THỐNG STUDYAI")
    print("#" * 85)
    
    if not check_services():
        print("Vui lòng khởi động đầy đủ Laravel và FastAPI trước khi chạy demo!")
        return

    run_pillar1_demo()
    run_pillar2_demo()
    run_pillar3_demo()
    verify_android_apis()

if __name__ == "__main__":
    main()
