"""
Inline Block-Level Non-Monotonic Aligner cho StudyAI (ai-service).
Đường dẫn đề xuất: ai-service/app/modules/lecture_notes/inline_block_aligner.py

Giải quyết 3 bài toán thực tế khi giảng viên nói trên lớp:
1. Giảng viên KHÔNG nói theo thứ tự slide (nhảy cóc, quay lại slide cũ, hoặc nói ngoài slide).
2. Cần biết chính xác giảng viên đang giải thích ĐOẠN/Ý (bullet/block) nào trong slide để chèn note ngay dưới đúng ý đó.
3. Phân biệt giữa "đọc lại chữ trên slide" (chỉ highlight đang giảng) và "phân tích mở rộng" (chèn inline note).
"""

import difflib
import re
from typing import Dict, List, Optional, Tuple
from pydantic import BaseModel

try:
    from rapidfuzz import fuzz as _rf_fuzz
except ImportError:
    _rf_fuzz = None


class SlideBlock(BaseModel):
    block_id: str          # VD: "s3_b2" (Slide 3, Block 2)
    slide_page: int        # Số trang slide (1-indexed)
    block_index: int       # Thứ tự ý trong trang slide
    kind: str = "bullet"   # "heading" | "bullet" | "formula" | "code"
    text: str              # Nội dung gốc lấy từ slide


class InlineNoteEvent(BaseModel):
    matched_slide_page: Optional[int]
    matched_block_id: Optional[str]
    action: str            # "highlight_only" | "attach_inline_note" | "general_note"
    confidence: float      # [0.0, 1.0]
    novelty_score: float   # [0.0, 1.0]
    transcript_text: str
    start_sec: float
    end_sec: float
    jumped_non_linear: bool = False


STOPWORDS = {
    # Tiếng Anh
    "a", "an", "the", "and", "or", "but", "if", "then", "when", "at", "by", "for",
    "with", "about", "to", "from", "in", "out", "on", "of", "is", "are", "was", "were",
    "be", "been", "have", "has", "had", "do", "does", "did", "this", "that", "these",
    "those", "it", "we", "you", "they", "i", "so", "as", "can", "will", "just",
    # Tiếng Việt
    "và", "là", "của", "các", "những", "một", "trong", "cho", "với", "để", "từ", "tại",
    "này", "đó", "khi", "thì", "mà", "như", "được", "có", "không", "sẽ", "đang", "đã",
    "rất", "cũng", "về", "trên", "dưới", "theo", "hay", "hoặc", "nhưng", "vì", "nên",
    "thầy", "cô", "em", "mình", "ta", "chúng", "bạn", "đây", "kia",
}


def extract_content_tokens(text: str) -> set:
    tokens = re.findall(r"[0-9a-zA-Z_À-ỹ\*\+#]+", text.lower())
    filtered = {t for t in tokens if t not in STOPWORDS and len(t) >= 2}
    return filtered if filtered else set(tokens)


def _fuzzy_similarity(text1: str, text2: str) -> float:
    if _rf_fuzz is not None:
        return _rf_fuzz.token_set_ratio(text1.lower(), text2.lower()) / 100.0
    t1 = " ".join(sorted(extract_content_tokens(text1)))
    t2 = " ".join(sorted(extract_content_tokens(text2)))
    return difflib.SequenceMatcher(None, t1, t2).ratio()


def split_slide_into_blocks(slide_page: int, slide_raw_text: str) -> List[SlideBlock]:
    """Tách nội dung thô của 1 trang slide thành các khối (heading, bullet, công thức)."""
    blocks: List[SlideBlock] = []
    lines = [ln.strip() for ln in slide_raw_text.splitlines() if ln.strip()]
    for idx, line in enumerate(lines, start=1):
        clean = re.sub(r"^[•\-\*\u2022\u25cf\s]+", "", line).strip()
        if not clean:
            continue
        if idx == 1 and len(clean) < 70:
            kind = "heading"
        elif any(sym in clean for sym in ("=", "∑", "∫", "p(x)", "f(x)", "->")):
            kind = "formula"
        else:
            kind = "bullet"
        blocks.append(
            SlideBlock(
                block_id=f"s{slide_page}_b{idx}",
                slide_page=slide_page,
                block_index=idx,
                kind=kind,
                text=clean,
            )
        )
    return blocks


class NonMonotonicSlideTracker:
    """
    Bộ theo dõi vị trí giảng bài theo từng Block trên Slide (dùng chung cho cả Live Stream & Batch Audio).
    - Không ép đi xuôi cứng nhắc như Monotonic DTW: nếu giảng viên nhảy từ Slide 5 về Slide 2
      và độ khớp vượt ngưỡng, tracker lập tức nhảy về đúng Block của Slide 2.
    - Nếu giảng viên nói ngoài lề / không khớp từ khóa slide nào (< off_slide_threshold), chuyển sang 'general_note'.
    """

    def __init__(
        self,
        blocks: List[SlideBlock],
        off_slide_threshold: float = 0.30,
        novelty_threshold: float = 0.35,
        continuity_bonus: float = 0.06,
    ):
        self.blocks = blocks
        self.off_slide_threshold = off_slide_threshold
        self.novelty_threshold = novelty_threshold
        self.continuity_bonus = continuity_bonus
        self.current_slide_page: int = blocks[0].slide_page if blocks else 1
        self._block_tokens: Dict[str, set] = {
            b.block_id: extract_content_tokens(b.text) for b in blocks
        }

    def _score_block(self, transcript_text: str, seg_tokens: set, block: SlideBlock) -> Tuple[float, float]:
        """
        Trả về (match_score, novelty_score).
        Chỉ cộng continuity_bonus khi câu nói thực sự trùng ít nhất 1 từ khóa nội dung với block,
        tránh câu tán gẫu ngoài lề bị cộng điểm oan và gắn nhầm vào slide đang mở.
        """
        blk_tokens = self._block_tokens.get(block.block_id, set())
        if not seg_tokens or not blk_tokens:
            return 0.0, 1.0

        intersection = seg_tokens.intersection(blk_tokens)
        if not intersection:
            return 0.0, 1.0

        overlap_on_block = len(intersection) / max(len(blk_tokens), 1)
        overlap_on_seg = len(intersection) / max(len(seg_tokens), 1)
        fuzzy_sim = _fuzzy_similarity(transcript_text, block.text)

        base_score = 0.45 * fuzzy_sim + 0.35 * overlap_on_block + 0.20 * overlap_on_seg

        bonus = 0.0
        jump_dist = abs(block.slide_page - self.current_slide_page)
        if base_score >= 0.22:
            if block.slide_page == self.current_slide_page:
                bonus = self.continuity_bonus
            elif block.slide_page == self.current_slide_page + 1:
                bonus = self.continuity_bonus * 0.6
            elif jump_dist > 4:
                # Chặn nhảy xa > 4 trang chỉ vì trùng 1 từ khóa đơn lẻ
                if len(intersection) < 2:
                    return 0.0, 1.0
                bonus = -0.15

        novelty = 1.0 - overlap_on_seg
        return max(0.0, min(1.0, base_score + bonus)), round(novelty, 3)

    def process_utterance(
        self,
        transcript_text: str,
        start_sec: float,
        end_sec: float,
    ) -> InlineNoteEvent:
        """Nhận 1 câu/đoạn transcript (từ Live ASR hoặc Batch ASR) và quyết định gắn vào Block nào."""
        seg_tokens = extract_content_tokens(transcript_text)
        if not self.blocks or not seg_tokens:
            return InlineNoteEvent(
                matched_slide_page=None,
                matched_block_id=None,
                action="general_note",
                confidence=0.0,
                novelty_score=1.0,
                transcript_text=transcript_text,
                start_sec=start_sec,
                end_sec=end_sec,
            )

        best_block: Optional[SlideBlock] = None
        best_score = -1.0
        best_novelty = 1.0

        for blk in self.blocks:
            score, novelty = self._score_block(transcript_text, seg_tokens, blk)
            if score > best_score:
                best_score = score
                best_novelty = novelty
                best_block = blk

        if best_block is None or best_score < self.off_slide_threshold:
            return InlineNoteEvent(
                matched_slide_page=None,
                matched_block_id=None,
                action="general_note",
                confidence=round(max(best_score, 0.0), 3),
                novelty_score=1.0,
                transcript_text=transcript_text,
                start_sec=start_sec,
                end_sec=end_sec,
            )

        jumped_non_linear = (
            best_block.slide_page < self.current_slide_page
            or best_block.slide_page > self.current_slide_page + 1
        )
        self.current_slide_page = best_block.slide_page

        action = (
            "attach_inline_note"
            if best_novelty >= self.novelty_threshold
            else "highlight_only"
        )

        return InlineNoteEvent(
            matched_slide_page=best_block.slide_page,
            matched_block_id=best_block.block_id,
            action=action,
            confidence=round(best_score, 3),
            novelty_score=best_novelty,
            transcript_text=transcript_text,
            start_sec=start_sec,
            end_sec=end_sec,
            jumped_non_linear=jumped_non_linear,
        )