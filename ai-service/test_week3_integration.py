import sys
import os

if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def run_tests():
    # 1. Test /health
    res = client.get("/health")
    assert res.status_code == 200, f"Health failed: {res.text}"
    print(f"1. Health check OK: {res.json()['status']}")

    # 2. Test /api/v1/week3/process-slide-and-asr
    slide_path = r"c:\Users\Nguyen\AndroidStudioProjects\StudyAI\dataset_eval\slides\2026.Chapter 2. INTELLIGENT AGENTS (1).pdf"
    payload = {
        "lecture_id": 1,
        "course_id": 1,
        "slide_file_path": slide_path,
        "audio_file_path": None,
        "manual_transitions": [
            {"slide_number": 1, "start_sec": 0.0, "end_sec": 25.0},
            {"slide_number": 2, "start_sec": 25.0, "end_sec": 60.0},
            {"slide_number": 3, "start_sec": 60.0, "end_sec": 110.0}
        ]
    }

    res2 = client.post("/api/v1/week3/process-slide-and-asr", json=payload)
    assert res2.status_code == 200, f"Week3 endpoint failed: {res2.text}"
    data = res2.json()
    print("2. Week 3 Process Endpoint OK!")
    print(f"   Total Slides: {data['total_slides']}")
    print(f"   Total Extracted Terms: {data['total_extracted_terms']}")
    print(f"   Total Aligned Transcript Segments: {len(data['aligned_transcript_segments'])}")
    for seg in data["aligned_transcript_segments"]:
        print(f"   - Segment {seg['segment_id']}: Aligned Slide {seg['aligned_slide_number']} (Confidence: {seg['alignment_confidence']})")

    print("\n=======================================================")
    print("ALL WEEK 3 INTEGRATION TESTS PASSED SUCCESSFULLY!")
    print("=======================================================")

if __name__ == "__main__":
    run_tests()
