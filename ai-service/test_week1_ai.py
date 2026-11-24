import sys
import os
import json

# Setup path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.modules.lecture_notes.note_synthesizer import (
    synthesize_slide_notes,
    SlideSynthesisResult,
    StructuredNoteBlock
)
from app.schemas import verify_evidence_grounding

def run_tests():
    print("=== BẮT ĐẦU KIỂM THỬ TUẦN 1 (AI MODULE) ===")

    # ---------------------------------------------------------
    # TEST 2: Anti-Hallucination Gate (Kiểm tra chống bịa đặt)
    # ---------------------------------------------------------
    print("\n--- TEST 2: Anti-Hallucination Gate Verification ---")
    real_source = "Trong T-SQL, câu lệnh SELECT có thể dùng để gán giá trị cho nhiều biến cùng một lúc từ bảng dữ liệu."
    real_quote = "câu lệnh SELECT có thể dùng để gán giá trị cho nhiều biến"
    fake_quote = "Thầy yêu cầu các em nộp bài tập lớn vào lúc 23h59 chủ nhật tuần này qua MS Teams"

    grounded_real = verify_evidence_grounding(real_quote, real_source, threshold=70.0)
    grounded_fake = verify_evidence_grounding(fake_quote, real_source, threshold=70.0)

    print(f"Quote thật '{real_quote}': Grounded = {grounded_real}")
    print(f"Quote bịa đặt '{fake_quote}': Grounded = {grounded_fake}")

    assert grounded_real is True, "FAIL: Real quote must be recognized as grounded!"
    assert grounded_fake is False, "FAIL: Fake quote must be rejected as ungrounded!"
    print(">>> TEST 2 PASSED: verify_evidence_grounding() chặn đứng câu bịa đặt thành công!")

    # ---------------------------------------------------------
    # TEST 1: Real LLM Test (Gemini LLM thật)
    # ---------------------------------------------------------
    print("\n--- TEST 1: Real LLM Call with Gemini (Slide Synthesis) ---")
    slide_text = """
    Chương 9: LẬP TRÌNH T-SQL CĂN BẢN
    1. Khai báo biến: DECLARE @TenBien KieuDuLieu
    2. Gán giá trị: SET @TenBien = GiaTri hoặc SELECT @TenBien = Cot FROM Bang
    3. Cấu trúc rẽ nhánh: IF ... ELSE ...
    4. Vòng lặp: WHILE ... BEGIN ... END
    """

    transcript_segments = [
        {
            "start_sec": 120.5,
            "end_sec": 135.2,
            "text": "Các bạn lưu ý nhé, khi dùng SELECT để gán biến thì có thể gán nhiều biến cùng một dòng, còn SET thì mỗi dòng chỉ gán được đúng một biến thôi."
        },
        {
            "start_sec": 140.0,
            "end_sec": 158.0,
            "text": "Chỗ này đi thi rất hay ra bẫy trắc nghiệm: nếu câu SELECT trả về nhiều hơn một dòng dữ liệu thì biến sẽ nhận giá trị của dòng cuối cùng."
        }
    ]

    print("Calling synthesize_slide_notes() with real Gemini client...")
    result: SlideSynthesisResult = synthesize_slide_notes(
        slide_number=9,
        slide_text=slide_text,
        transcript_segments=transcript_segments,
        course_title="Cơ sở dữ liệu nâng cao - T-SQL"
    )

    print("\nKết quả tổng hợp từ LLM:")
    print(f"Slide number: {result.slide_number}")
    print(f"Cleaned bullets: {result.cleaned_slide_bullets}")
    print(f"Number of notes generated: {len(result.notes)}")

    # Assertions
    assert isinstance(result, SlideSynthesisResult), "FAIL: Result is not a SlideSynthesisResult"
    assert result.slide_number == 9, "FAIL: Slide number mismatch"
    
    # Assert no mock data
    mock_indicator = "Bài tập chương 2: Intelligent Agents"
    for note in result.notes:
        print(f"  [{note.category.upper()}] {note.headline}: {note.content}")
        print(f"    Evidence quote: \"{note.evidence_quote}\" ({note.start_time_sec}s - {note.end_time_sec}s)")
        assert mock_indicator not in note.headline, "FAIL: Found mock data in headline"
        assert mock_indicator not in note.content, "FAIL: Found mock data in content"
        # Verify evidence grounding
        source_raw = " ".join(s["text"] for s in transcript_segments)
        is_valid = verify_evidence_grounding(note.evidence_quote, source_raw, threshold=60.0)
        assert is_valid, f"FAIL: Evidence quote '{note.evidence_quote}' is not grounded in transcript!"

    print(">>> TEST 1 PASSED: Gemini LLM thật sinh StructuredNoteBlock chuẩn xác, không dùng mock data, đã qua kiểm chứng grounding!")

    print("\n==============================================")
    print("ALL TESTS (TEST 1 & TEST 2) COMPLETED SUCCESSFULLY!")
    print("==============================================")

if __name__ == "__main__":
    run_tests()
