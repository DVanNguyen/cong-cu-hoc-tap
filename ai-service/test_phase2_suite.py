import unittest
from app.modules.lecture_notes.inline_block_aligner import (
    SlideBlock,
    split_slide_into_blocks,
    NonMonotonicSlideTracker,
)
from app.services.asr_vad_service import SlideAwareASREngine

class TestPhase2Suite(unittest.TestCase):
    def setUp(self):
        # Thiết lập 5 slide mẫu
        self.blocks = [
            SlideBlock(block_id="s1_b1", slide_page=1, block_index=1, kind="heading", text="Tổng quan Trí tuệ Nhân tạo"),
            SlideBlock(block_id="s1_b2", slide_page=1, block_index=2, kind="bullet", text="Tác tử thông minh Intelligent Agent hoạt động trong môi trường PEAS"),
            SlideBlock(block_id="s2_b1", slide_page=2, block_index=1, kind="heading", text="Tìm kiếm mù Uninformed Search"),
            SlideBlock(block_id="s2_b2", slide_page=2, block_index=2, kind="bullet", text="Thuật toán BFS duyệt theo chiều rộng hàng đợi FIFO"),
            SlideBlock(block_id="s5_b1", slide_page=5, block_index=1, kind="heading", text="Tìm kiếm Heuristic và Giải thuật A*"),
            SlideBlock(block_id="s5_b2", slide_page=5, block_index=2, kind="bullet", text="Hàm đánh giá f(n) = g(n) + h(n) với heuristic chấp nhận được admissible"),
        ]
        self.tracker = NonMonotonicSlideTracker(self.blocks, off_slide_threshold=0.25, novelty_threshold=0.35)

    def test_case_a_expansion_attach_inline_note(self):
        # (a) Giảng mở rộng đúng ý -> attach_inline_note
        ev = self.tracker.process_utterance(
            transcript_text="Thầy phân tích thêm về tác tử thông minh trong môi trường PEAS khi thi cuối kỳ sẽ có bài tập phần này",
            start_sec=10.0,
            end_sec=18.0
        )
        self.assertEqual(ev.matched_slide_page, 1)
        self.assertEqual(ev.matched_block_id, "s1_b2")
        self.assertEqual(ev.action, "attach_inline_note")
        self.assertFalse(ev.jumped_non_linear)

    def test_case_b_jump_forward_s1_to_s5(self):
        # (b) Nhảy cóc từ Slide 1 sang Slide 5 -> jumped_non_linear=True
        self.tracker.current_slide_page = 1
        ev = self.tracker.process_utterance(
            transcript_text="Bây giờ thầy chuyển nhanh sang giải thuật A* với hàm đánh giá f bằng g cộng h heuristic",
            start_sec=45.0,
            end_sec=53.0
        )
        self.assertEqual(ev.matched_slide_page, 5)
        self.assertIn(ev.matched_block_id, ["s5_b1", "s5_b2"])
        self.assertTrue(ev.jumped_non_linear)

    def test_case_c_jump_backward_s5_to_s1(self):
        # (c) Quay ngược từ Slide 5 về Slide 1 -> jumped_non_linear=True
        self.tracker.current_slide_page = 5
        ev = self.tracker.process_utterance(
            transcript_text="Các em lưu ý quay lại khái niệm tác tử thông minh intelligent agent ở đầu chương",
            start_sec=90.0,
            end_sec=98.0
        )
        self.assertEqual(ev.matched_slide_page, 1)
        self.assertEqual(ev.matched_block_id, "s1_b2")
        self.assertTrue(ev.jumped_non_linear)

    def test_case_d_off_slide_banter_general_note(self):
        # (d) Câu nói ngoài lề -> general_note (matched_slide_page=None)
        ev = self.tracker.process_utterance(
            transcript_text="Hôm nay trời mưa to quá bạn nào chưa nộp học phí nhớ hoàn thành trước thứ sáu",
            start_sec=120.0,
            end_sec=128.0
        )
        self.assertIsNone(ev.matched_slide_page)
        self.assertIsNone(ev.matched_block_id)
        self.assertEqual(ev.action, "general_note")

    def test_asr_language_prompts(self):
        # Kiểm tra ASR ngôn ngữ VI, EN, AUTO
        bias_vi = SlideAwareASREngine.build_prompt_and_hotwords(["BFS", "A*"], language="vi")
        self.assertIn("Bài giảng", bias_vi["initial_prompt"])

        bias_en = SlideAwareASREngine.build_prompt_and_hotwords(["BFS", "A*"], language="en")
        self.assertIn("University lecture", bias_en["initial_prompt"])

        bias_auto = SlideAwareASREngine.build_prompt_and_hotwords(["BFS", "A*"], language="auto")
        self.assertIn("Key terms", bias_auto["initial_prompt"])

if __name__ == "__main__":
    unittest.main()
