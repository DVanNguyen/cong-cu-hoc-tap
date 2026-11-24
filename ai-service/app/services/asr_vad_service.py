import os
import re
import logging
from typing import Any, Dict, List, Optional, Tuple
from faster_whisper import WhisperModel

logger = logging.getLogger("asr_vad_service")

# Bảng ánh xạ ngữ âm xác định cho các thuật ngữ phổ biến khi giảng viên đọc bằng tiếng Việt
PHONETIC_TERM_ALIASES: Dict[str, List[str]] = {
    "A*": ["a sao", "a star", "ây sao", "ây star"],
    "A* search": ["tìm kiếm a sao", "a sao search", "a star search"],
    "PEAS": ["p e a s", "pi i ây ét", "peas"],
    "BFS": ["b f s", "bi ép ét", "ờ b f s"],
    "DFS": ["d f s", "đi ép ét"],
    "UCS": ["u c s", "iu xi ét"],
    "IDS": ["i d s", "ai đi ét"],
    "heuristic": ["hiu rit tich", "hơ rit tich", "hyu rit tich"],
    "admissible heuristic": ["át mit xơ bồ hiu rit tich", "admissible hiu rit tich"],
    "Simulated Annealing": ["si miu lây tựt ơ ni linh", "simulated ơ ni linh"],
}

# Ngôn ngữ được hỗ trợ tường minh; None hoặc "auto" sẽ để Whisper tự động nhận diện
SUPPORTED_LANGUAGES = {"vi", "en"}


class SlideAwareASREngine:
    def __init__(
        self,
        model_name_or_path: Optional[str] = None,
        device: Optional[str] = None,
        compute_type: Optional[str] = None,
    ):
        """
        Hỗ trợ đa ngôn ngữ (Việt, Anh, Việt-Anh trộn):
        - Multilingual Whisper: 'small', 'medium', 'large-v3-turbo', 'base'
        - PhoWhisper CTranslate2 (chỉ dùng cho bài giảng tiếng Việt): 'qbsmlabs/PhoWhisper-small'
        Có thể cấu hình qua biến môi trường: WHISPER_MODEL, WHISPER_DEVICE, WHISPER_COMPUTE_TYPE.
        """
        self.model_name = model_name_or_path or os.getenv("WHISPER_MODEL", "small")
        self.device = device or os.getenv("WHISPER_DEVICE", "cpu")
        self.compute_type = compute_type or os.getenv("WHISPER_COMPUTE_TYPE", "int8")
        self.model = WhisperModel(
            self.model_name,
            device=self.device,
            compute_type=self.compute_type,
        )

    @staticmethod
    def normalize_language(language: Optional[str]) -> Optional[str]:
        """Chuẩn hóa mã ngôn ngữ: 'auto' / '' / None -> None (tự động nhận diện)."""
        if not language:
            return None
        cleaned = language.strip().lower()
        if cleaned in {"auto", "detect", "none"}:
            return None
        return cleaned

    @staticmethod
    def build_prompt_and_hotwords(
        slide_terms: List[str],
        course_title: Optional[str] = None,
        language: Optional[str] = None,
    ) -> Dict[str, Optional[str]]:
        """
        Thiết kế initial_prompt (< 224 tokens) và hotwords phù hợp với ngôn ngữ bài giảng.
        - language='en': Prompt tiếng Anh chuẩn để tránh kéo Whisper sang tiếng Việt.
        - language='vi': Prompt tiếng Việt hỗ trợ trộn thuật ngữ tiếng Anh.
        - language=None (auto): Prompt trung tính ngắn gọn để không làm lệch bước nhận diện ngôn ngữ.
        """
        if not slide_terms:
            return {"initial_prompt": None, "hotwords": None}

        # Lọc trùng, chỉ giữ các từ/cụm từ ngắn (<= 4 từ, <= 40 ký tự) để không làm tràn context của Whisper
        unique_terms = [
            t.strip() for t in slide_terms
            if t and t.strip() and len(t.split()) <= 4 and len(t) <= 40
        ]
        unique_terms = list(dict.fromkeys(unique_terms))[:20]
        if not unique_terms:
            return {"initial_prompt": None, "hotwords": None}

        # Lấy tối đa 10 thuật ngữ quan trọng nhất và giới hạn chiều dài prompt để không bao giờ vượt ngưỡng 448 tokens
        terms_csv = ", ".join(unique_terms[:10])
        lang = SlideAwareASREngine.normalize_language(language)

        if lang == "en":
            topic = (course_title or "Computer Science")[:40]
            initial_prompt = f"Lecture on {topic}. Terms: {terms_csv}."
        elif lang == "vi":
            topic = (course_title or "Khoa học máy tính")[:40]
            initial_prompt = f"Bài giảng môn {topic}. Thuật ngữ: {terms_csv}."
        else:
            prefix = f"{course_title[:40]}. " if course_title else ""
            initial_prompt = f"{prefix}Key terms: {terms_csv}."

        # Giới hạn an toàn tuyệt đối 160 ký tự để prompt chỉ chiếm ~40-80 tokens
        if len(initial_prompt) > 160:
            initial_prompt = initial_prompt[:157] + "..."

        hotwords = " ".join(unique_terms[:10])
        if len(hotwords) > 160:
            hotwords = hotwords[:160]

        return {
            "initial_prompt": initial_prompt,
            "hotwords": hotwords,
        }

    @staticmethod
    def recover_slide_domain_terms(
        text: str,
        active_slide_terms: List[str],
        detected_language: Optional[str] = "vi",
    ) -> str:
        """
        Bước hậu xử lý xác định:
        1. Chỉ áp dụng bảng sửa lỗi phiên âm tiếng Việt (PHONETIC_TERM_ALIASES) khi bài giảng
           là tiếng Việt hoặc không rõ ngôn ngữ (tránh thay nhầm trên bài giảng full tiếng Anh).
           Sắp xếp cụm dài trước để tránh 'a sao' thay đè trước 'tìm kiếm a sao'.
        2. Luôn chuẩn hóa chữ hoa/thường theo đúng thuật ngữ gốc trên Slide (VD: 'bfs' -> 'BFS').
        """
        if not active_slide_terms or not text:
            return text

        corrected = text
        active_lower_map = {
            t.strip().lower(): t.strip() for t in active_slide_terms if t and t.strip()
        }

        # 1. Chỉ chạy khôi phục phiên âm Việt-Anh khi ngôn ngữ không phải full tiếng Anh
        if detected_language != "en":
            replacement_pairs: List[Tuple[str, str]] = []
            for canonical_term, phonetic_variants in PHONETIC_TERM_ALIASES.items():
                if canonical_term.lower() in active_lower_map or any(
                    canonical_term.lower() in s.lower() for s in active_slide_terms
                ):
                    for variant in phonetic_variants:
                        replacement_pairs.append((variant, canonical_term))

            # Sắp xếp cụm dài hơn thay thế trước để tránh xung đột tiền tố/hậu tố
            replacement_pairs.sort(key=lambda item: len(item[0]), reverse=True)
            for variant, canonical_term in replacement_pairs:
                pattern = re.compile(rf"\b{re.escape(variant)}\b", flags=re.IGNORECASE)
                corrected = pattern.sub(canonical_term, corrected)

        # 2. Chuẩn hóa chữ hoa/thường theo đúng từ gốc trên Slide (an toàn cho cả VI và EN)
        for lower_term, exact_slide_term in active_lower_map.items():
            if len(exact_slide_term) >= 2 and not exact_slide_term.islower():
                pattern = re.compile(
                    rf"\b{re.escape(lower_term)}\b", flags=re.IGNORECASE
                )
                corrected = pattern.sub(exact_slide_term, corrected)

        return corrected

    def transcribe_with_metadata(
        self,
        audio_path: str,
        slide_terms: Optional[List[str]] = None,
        course_title: Optional[str] = None,
        language: Optional[str] = None,
        use_vad: bool = True,
        apply_glossary_post_fix: bool = True,
    ) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
        """
        Chạy toàn bộ pipeline: Silero VAD -> Faster-Whisper (Auto/VI/EN) -> Glossary Recovery.
        Trả về (output_segments, metadata) gồm ngôn ngữ nhận diện và độ tin cậy.
        """
        normalized_lang = self.normalize_language(language)
        bias = self.build_prompt_and_hotwords(
            slide_terms or [],
            course_title=course_title,
            language=normalized_lang,
        )

        segments_iter, info = self.model.transcribe(
            audio_path,
            language=normalized_lang,  # None => Whisper tự động nhận diện (hỗ trợ full EN & VI)
            task="transcribe",
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
            condition_on_previous_text=False,
            word_timestamps=True,
            beam_size=5,
            temperature=0.0,
            max_new_tokens=224,
            repetition_penalty=1.15,
            no_repeat_ngram_size=3,
            hallucination_silence_threshold=2.0,
        )

        detected_lang = getattr(info, "language", normalized_lang or "unknown")
        lang_prob = round(float(getattr(info, "language_probability", 1.0)), 4)

        output_segments: List[Dict[str, Any]] = []
        try:
            for idx, seg in enumerate(segments_iter, start=1):
                raw_text = " ".join(seg.text.split()).strip()
                if not raw_text:
                    continue

                final_text = (
                    self.recover_slide_domain_terms(
                        raw_text,
                        slide_terms or [],
                        detected_language=detected_lang,
                    )
                    if (apply_glossary_post_fix and slide_terms)
                    else raw_text
                )

                output_segments.append(
                    {
                        "segment_id": idx,
                        "start_sec": round(float(seg.start), 2),
                        "end_sec": round(float(seg.end), 2),
                        "language": detected_lang,
                        "raw_asr_text": raw_text,
                        "text": final_text,
                    }
                )
        except RuntimeError as e:
            if "position" in str(e).lower() and "448" in str(e):
                logger.warning(f"Whisper token positional limit (448) reached on long utterance: {e}. Preserved {len(output_segments)} segments.")
            else:
                logger.error(f"RuntimeError in ASR stream: {e}")
        except Exception as e:
            logger.error(f"Unexpected error in ASR stream: {e}")

        metadata = {
            "requested_language": normalized_lang or "auto",
            "detected_language": detected_lang,
            "language_probability": lang_prob,
            "duration_sec": round(float(getattr(info, "duration", 0.0)), 2),
            "model_name": self.model_name,
        }
        return output_segments, metadata

    def transcribe(
        self,
        audio_path: str,
        slide_terms: Optional[List[str]] = None,
        course_title: Optional[str] = None,
        language: Optional[str] = None,
        use_vad: bool = True,
        apply_glossary_post_fix: bool = True,
    ) -> List[Dict[str, Any]]:
        """
        Giữ nguyên chữ ký tương thích ngược (trả về List[Dict]) cho các caller cũ,
        nhưng mặc định language=None để hỗ trợ cả bài giảng full tiếng Anh lẫn tiếng Việt.
        """
        segments, _ = self.transcribe_with_metadata(
            audio_path=audio_path,
            slide_terms=slide_terms,
            course_title=course_title,
            language=language,
            use_vad=use_vad,
            apply_glossary_post_fix=apply_glossary_post_fix,
        )
        return segments