import re
from typing import List, Dict, Any, Optional
from faster_whisper import WhisperModel

# Bảng ánh xạ ngữ âm xác định cho các thuật ngữ phổ biến khi giảng viên đọc bằng tiếng Việt
PHONETIC_TERM_ALIASES = {
    "A*": ["a sao", "a star", "ây sao", "ây star"],
    "A* search": ["tìm kiếm a sao", "a sao search", "a star search"],
    "PEAS": ["p e a s", "pi i ây ét", "peas"],
    "BFS": ["b f s", "bi ép ét", "ờ b f s"],
    "DFS": ["d f s", "đi ép ét"],
    "UCS": ["u c s", "iu xi ét"],
    "IDS": ["i d s", "ai đi ét"],
    "heuristic": ["hiu rít tích", "hơ rít tích", "hyu rít tích"],
    "admissible heuristic": ["át mít xơ bồ hiu rít tích", "admissible hiu rít tích"],
    "Simulated Annealing": ["si miu lây tựt ơ ni linh", "simulated ơ ni linh"],
}

class SlideAwareASREngine:
    def __init__(
        self,
        model_name_or_path: str = "small",
        device: str = "cpu",
        compute_type: str = "int8"
    ):
        """
        Hỗ trợ cả:
        - Multilingual Whisper: 'small', 'medium', 'base'
        - PhoWhisper CTranslate2: 'qbsmlabs/PhoWhisper-small' hoặc đường dẫn local
        """
        self.model = WhisperModel(model_name_or_path, device=device, compute_type=compute_type)

    @staticmethod
    def build_prompt_and_hotwords(
        slide_terms: List[str],
        course_title: str = "Trí tuệ nhân tạo"
    ) -> Dict[str, Optional[str]]:
        """
        Thiết kế initial_prompt (< 224 tokens) và chuỗi hotwords tối ưu cho faster-whisper.
        """
        if not slide_terms:
            return {"initial_prompt": None, "hotwords": None}

        # Lọc trùng và giữ tối đa 45 thuật ngữ quan trọng nhất để không vượt 224 tokens
        unique_terms = list(dict.fromkeys(slide_terms))[:45]
        terms_csv = ", ".join(unique_terms)

        # initial_prompt viết dưới dạng câu giảng mẫu để định hình phong cách viết hoa & trộn Việt-Anh
        initial_prompt = (
            f"Bài giảng môn {course_title}. "
            f"Trong slide này chúng ta xét các thuật ngữ: {terms_csv}."
        )
        # hotwords truyền trực tiếp vào Beam Search scorer ở mọi cửa sổ âm thanh
        hotwords = " ".join(unique_terms)

        return {
            "initial_prompt": initial_prompt,
            "hotwords": hotwords
        }

    @staticmethod
    def recover_slide_domain_terms(text: str, active_slide_terms: List[str]) -> str:
        """
        Bước hậu xử lý xác định: Chỉ chuẩn hóa lại thuật ngữ nếu thuật ngữ đó
        CÓ MẶT trong danh sách thuật ngữ trích xuất từ Slide của bài học.
        """
        if not active_slide_terms or not text:
            return text

        corrected = text
        active_lower_map = {t.lower(): t for t in active_slide_terms}

        # 1. Khôi phục từ bảng ngữ âm nếu thuật ngữ gốc có trên slide
        for canonical_term, phonetic_variants in PHONETIC_TERM_ALIASES.items():
            if canonical_term.lower() in active_lower_map or any(
                canonical_term.lower() in s.lower() for s in active_slide_terms
            ):
                for variant in phonetic_variants:
                    pattern = re.compile(rf"\b{re.escape(variant)}\b", flags=re.IGNORECASE)
                    corrected = pattern.sub(canonical_term, corrected)

        # 2. Chuẩn hóa chữ hoa/thường theo đúng từ gốc trên Slide (VD: 'peas' -> 'PEAS', 'bfs' -> 'BFS')
        for lower_term, exact_slide_term in active_lower_map.items():
            if len(exact_slide_term) >= 2 and not exact_slide_term.islower():
                pattern = re.compile(rf"\b{re.escape(lower_term)}\b", flags=re.IGNORECASE)
                corrected = pattern.sub(exact_slide_term, corrected)

        return corrected

    def transcribe(
        self,
        audio_path: str,
        slide_terms: Optional[List[str]] = None,
        use_vad: bool = True,
        apply_glossary_post_fix: bool = True
    ) -> List[Dict[str, Any]]:
        """
        Chạy toàn bộ pipeline: Silero VAD -> Faster-Whisper (Prompt + Hotwords) -> Glossary Recovery.
        """
        bias = self.build_prompt_and_hotwords(slide_terms or [])

        segments_iter, _ = self.model.transcribe(
            audio_path,
            language="vi",
            initial_prompt=bias["initial_prompt"],
            hotwords=bias["hotwords"],
            vad_filter=use_vad,
            vad_parameters=dict(
                threshold=0.5,
                min_speech_duration_ms=250,
                max_speech_duration_s=28,
                min_silence_duration_ms=500,
                speech_pad_ms=250,
            ),
            condition_on_previous_text=False,  # Ngăn lặp từ và giúp áp dụng prompt/hotwords xuyên suốt
            word_timestamps=True,
            beam_size=5,
            temperature=0.0,
        )

        output_segments = []
        for idx, seg in enumerate(segments_iter, start=1):
            raw_text = " ".join(seg.text.split()).strip()
            if not raw_text:
                continue

            final_text = (
                self.recover_slide_domain_terms(raw_text, slide_terms)
                if (apply_glossary_post_fix and slide_terms)
                else raw_text
            )

            output_segments.append({
                "segment_id": idx,
                "start_sec": round(float(seg.start), 2),
                "end_sec": round(float(seg.end), 2),
                "raw_asr_text": raw_text,
                "text": final_text,
            })

        return output_segments
