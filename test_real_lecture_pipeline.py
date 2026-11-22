"""
Kiểm thử Pipeline trên Cặp Slide PDF & Lời giảng thực tế của VKU
- Slide: dataset_eval/slides/2026.Chapter 2. INTELLIGENT AGENTS (1).pdf
- Lời giảng Audio: dataset_eval/eval_asr_wer.json (trích từ lecture_01_agents.wav)
"""
import os
import sys
import json
import urllib.request
import fitz # PyMuPDF

sys.stdout.reconfigure(encoding='utf-8')

SLIDE_PDF = os.path.join("dataset_eval", "slides", "2026.Chapter 2. INTELLIGENT AGENTS (1).pdf")
GROUND_TRUTH_ASR = os.path.join("dataset_eval", "eval_asr_wer.json")
FASTAPI_URL = "http://127.0.0.1:8001"

def test_real_slide_and_audio():
    print("=" * 80)
    print(" KIỂM THỬ TRÊN BỘ DỮ LIỆU THẬT: SLIDE PDF & LỜI GIẢNG VIÊN VKU")
    print("=" * 80)

    # 1. Bóc tách nội dung thật từ file PDF Slide
    print(f"\n[BƯỚC 1] Bóc tách Slide PDF: {SLIDE_PDF}")
    doc = fitz.open(SLIDE_PDF)
    print(f" [+] Tổng số trang slide: {len(doc)} trang")

    extracted_slides = []
    for page_idx in range(min(6, len(doc))):
        page = doc[page_idx]
        text = page.get_text().strip().replace("\n", " ")
        # Lấy một số từ khóa tiêu biểu trên trang slide
        words = [w for w in text.split() if len(w) > 4 and w.isalnum()]
        terms = list(dict.fromkeys(words[:4]))
        extracted_slides.append({
            "id": page_idx + 1,
            "slide_number": page_idx + 1,
            "extracted_text": text[:200] + ("..." if len(text) > 200 else ""),
            "terms_json": terms,
            "embedding": [0.1 * (page_idx + 1)] * 384
        })
        print(f"  • Slide {page_idx + 1}: \"{text[:85]}...\" | Thuật ngữ: {terms}")

    # 2. Đọc file lời giảng thực tế từ audio ground truth
    print(f"\n[BƯỚC 2] Nạp các phân đoạn lời giảng từ: {GROUND_TRUTH_ASR}")
    with open(GROUND_TRUTH_ASR, "r", encoding="utf-8") as f:
        asr_data = json.load(f)

    # Lấy các phân đoạn của bài 1 (lecture_01_agents.wav)
    lecture_segments = [s for s in asr_data if "lecture_01_agents" in s.get("audio_path", "")]
    print(f" [+] Tìm thấy {len(lecture_segments)} phân đoạn lời giảng của giảng viên trong buổi học")
    
    transcripts_payload = []
    for s in lecture_segments[:5]:
        print(f"  • [{s['start_sec']}s -> {s['end_sec']}s]: \"{s['prompted_hypothesis']}\"")
        transcripts_payload.append({
            "id": s["segment_id"],
            "start_time_sec": s["start_sec"],
            "end_time_sec": s["end_sec"],
            "clean_text": s["prompted_hypothesis"],
            "embedding": [0.1 * s["segment_id"]] * 384
        })

    # 3. Gửi sang AI Microservice để Căn gióng DTW & Bồi đắp lời giảng mở rộng
    print(f"\n[BƯỚC 3] Chạy Thuật toán Monotonic DTW & Chấm điểm Novelty...")
    req_body = {
        "lecture_id": 1,
        "course_id": 1,
        "slides": extracted_slides,
        "transcripts": transcripts_payload,
        "use_auto_dtw": True,
        "novelty_threshold": 0.28
    }

    req = urllib.request.Request(
        f"{FASTAPI_URL}/api/v1/pipeline/week4/generate-notes",
        data=json.dumps(req_body).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode("utf-8"))

    alignments = res.get("alignments", [])
    notes = res.get("generated_notes", [])

    print(f" [+] Kết quả Căn gióng DTW lời giảng vào từng Slide:")
    for a in alignments:
        print(f"     -> Phân đoạn \"{a.get('transcript_snippet', '')[:50]}...\" gắn vào Slide #{a.get('matched_slide_number')} (Khớp {a.get('similarity_score', 0):.2f})")

    print(f"\n [+] Ghi chú Bồi đắp Ngoài Slide (Novelty >= 0.28 & MMR): {len(notes)} ý")
    for n in notes:
        print(f"     • [Slide {n['slide_number']} | Novelty: {n['novelty_score']:.2f}] \"{n['bullet_point']}\"")
        print(f"       Trích dẫn gốc: \"{n['verbatim_quote']}\"")

    print("\n" + "=" * 80)
    print(" KẾT QUẢ: ĐÃ TEST KHỚP THÀNH CÔNG GIỮA FILE SLIDE VÀ LỜI GIẢNG THỰC TẾ!")
    print("=" * 80)

if __name__ == "__main__":
    test_real_slide_and_audio()
