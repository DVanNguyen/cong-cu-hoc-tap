import re
import math
from typing import List, Dict, Any, Optional
from pydantic import BaseModel
from rapidfuzz import fuzz

class AlignedNoteItem(BaseModel):
    slide_page: int
    content: str
    evidence_quote: str
    timestamp_start_sec: float
    timestamp_end_sec: float
    type: str  # "addition", "term", "exam_hint"
    novelty_score: float
    term_original: Optional[str] = None
    term_vietnamese: Optional[str] = None

# Danh sách mẫu câu giao tiếp / câu đệm cần lọc bỏ (Filler filtering)
FILLER_PATTERNS = [
    r"nghe rõ không",
    r"nhìn lên màn hình",
    r"ổn định chỗ ngồi",
    r"điểm danh",
    r"vắng mặt",
    r"lớp phó",
    r"các em trật tự",
    r"có ai hỏi gì không",
    r"thầy nhắc lại là"
]

def is_filler_sentence(text: str) -> bool:
    """Loại bỏ câu đệm giao tiếp nếu < 5 từ hoặc khớp các mẫu cảm thán/điểm danh."""
    words = text.strip().split()
    if len(words) < 5:
        return True
    lower_text = text.lower()
    for pattern in FILLER_PATTERNS:
        if re.search(pattern, lower_text):
            return True
    return False

def compute_token_overlap(segment_text: str, slide_text: str) -> float:
    """Tính tỉ lệ từ vựng trùng nhau: |Tokens(u_t) ∩ Tokens(s_k)| / |Tokens(u_t)|."""
    tokens_seg = set(re.findall(r'\w+', segment_text.lower()))
    if not tokens_seg:
        return 0.0
    tokens_slide = set(re.findall(r'\w+', slide_text.lower()))
    overlap = tokens_seg.intersection(tokens_slide)
    return len(overlap) / len(tokens_seg)

def compute_semantic_similarity(text1: str, text2: str) -> float:
    """
    Tính tương đồng ngữ nghĩa: dùng RapidFuzz token_set_ratio chuẩn hóa về [0.0, 1.0].
    (Tương đương cosine sim khi không có GPU embedding trực tiếp).
    """
    if not text1 or not text2:
        return 0.0
    return fuzz.token_set_ratio(text1.lower(), text2.lower()) / 100.0

def compute_novelty_score(
    segment_text: str, 
    slide_text: str, 
    slide_bullets: Optional[List[str]] = None
) -> float:
    """
    Tính điểm độ mới:
    Novelty(u_t | s_k) = 0.7 * (1 - max CosineSim) + 0.3 * (1 - TokenOverlap)
    Ngưỡng: >= 0.28 là giảng viên nói thêm mở rộng.
    """
    if is_filler_sentence(segment_text):
        return 0.0

    bullets = slide_bullets if slide_bullets else [b.strip() for b in slide_text.split('\n') if b.strip()]
    if not bullets:
        bullets = [slide_text]

    max_sim = 0.0
    for bullet in bullets:
        sim = compute_semantic_similarity(segment_text, bullet)
        if sim > max_sim:
            max_sim = sim

    token_overlap = compute_token_overlap(segment_text, slide_text)
    novelty = 0.7 * (1.0 - max_sim) + 0.3 * (1.0 - token_overlap)
    return max(0.0, min(1.0, novelty))

def monotonic_dtw_alignment(
    transcript_segments: List[Dict[str, Any]], 
    slides: List[Dict[str, Any]],
    gamma_next: float = 0.08,
    gamma_skip: float = 0.25,
    gamma_back: float = 0.30
) -> List[int]:
    """
    Căn tự động bằng Quy hoạch động Đơn điệu (Monotonic DTW):
    D(t, k) = C(t, k) + min(
        D(t-1, k),                  # Giữ nguyên slide k
        D(t-1, k-1) + gamma_next,   # Sang slide kế tiếp k-1 -> k
        D(t-1, k-2) + gamma_skip,   # Nhảy cóc 1 slide
        D(t-1, k+1) + gamma_back    # Lùi lại 1 slide
    )
    """
    T = len(transcript_segments)
    K = len(slides)
    if T == 0 or K == 0:
        return []

    # 1. Ma trận chi phí C[t][k] = 1 - CosineSim(u_t, s_k)
    C = [[1.0 for _ in range(K)] for _ in range(T)]
    for t in range(T):
        u_text = transcript_segments[t].get("text", "")
        for k in range(K):
            s_text = slides[k].get("raw_text") or slides[k].get("slide_text") or ""
            sim = compute_semantic_similarity(u_text, s_text)
            C[t][k] = 1.0 - sim

    # 2. Khởi tạo bảng quy hoạch động D[t][k] và Backtrack pointer
    INF = 1e9
    D = [[INF for _ in range(K)] for _ in range(T)]
    backtrack = [[0 for _ in range(K)] for _ in range(T)]

    # D(0, 0) = C(0, 0); D(0, k) = INF với k > 0
    D[0][0] = C[0][0]
    for k in range(1, K):
        D[0][k] = C[0][k] + (k * gamma_next)

    # 3. Điền bảng quy hoạch động
    for t in range(1, T):
        for k in range(K):
            # Các bước chuyển khả dĩ
            candidates = []
            
            # Giữ nguyên slide
            candidates.append((D[t-1][k], k))
            
            # Sang slide kế tiếp
            if k - 1 >= 0:
                candidates.append((D[t-1][k-1] + gamma_next, k - 1))
            
            # Nhảy cóc 1 slide
            if k - 2 >= 0:
                candidates.append((D[t-1][k-2] + gamma_skip, k - 2))
                
            # Lùi lại 1 slide
            if k + 1 < K:
                candidates.append((D[t-1][k+1] + gamma_back, k + 1))

            best_cost, best_prev_k = min(candidates, key=lambda x: x[0])
            D[t][k] = C[t][k] + best_cost
            backtrack[t][k] = best_prev_k

    # 4. Truy vết ngược từ t = T-1 về t = 0
    optimal_path = [0] * T
    # Tìm k kết thúc có cost nhỏ nhất ở hàng T-1
    best_last_k = min(range(K), key=lambda k: D[T-1][k])
    curr_k = best_last_k

    for t in range(T - 1, -1, -1):
        optimal_path[t] = curr_k + 1  # 1-indexed slide page
        if t > 0:
            curr_k = backtrack[t][curr_k]

    return optimal_path

def align_transcript_to_slides(
    transcript_segments: List[Dict[str, Any]], 
    slides: List[Dict[str, Any]],
    user_slide_clicks: Optional[Dict[int, float]] = None
) -> List[AlignedNoteItem]:
    """
    Toàn bộ quy trình căn chỉnh và tính điểm độ mới:
    - Nếu có user_slide_clicks: Ánh xạ xác định theo sự kiện chuyển slide.
    - Nếu không: Dùng Monotonic DTW.
    - Tính Novelty score, áp dụng ngưỡng 0.28 để sinh ghi chú bổ sung.
    """
    if not transcript_segments or not slides:
        return []

    assigned_pages: List[int] = []
    
    # Chế độ A: Căn theo sự kiện người dùng
    if user_slide_clicks and len(user_slide_clicks) > 0:
        sorted_clicks = sorted(user_slide_clicks.items(), key=lambda x: x[1])
        for seg in transcript_segments:
            t_start = seg.get("start_sec", 0.0)
            t_end = seg.get("end_sec", t_start + 1.0)
            
            best_page = sorted_clicks[0][0]
            max_overlap = -1.0
            
            for i in range(len(sorted_clicks)):
                page, tau_start = sorted_clicks[i]
                tau_end = sorted_clicks[i+1][1] if i + 1 < len(sorted_clicks) else 1e9
                overlap = max(0.0, min(t_end, tau_end) - max(t_start, tau_start))
                if overlap > max_overlap:
                    max_overlap = overlap
                    best_page = page
            assigned_pages.append(best_page)
    else:
        # Chế độ B: Monotonic DTW
        assigned_pages = monotonic_dtw_alignment(transcript_segments, slides)

    aligned_notes: List[AlignedNoteItem] = []

    for idx, seg in enumerate(transcript_segments):
        text = seg.get("text", "")
        start_sec = seg.get("start_sec", 0.0)
        end_sec = seg.get("end_sec", 0.0)
        page = assigned_pages[idx] if idx < len(assigned_pages) else 1

        # Lấy nội dung slide tương ứng
        slide_obj = next((s for s in slides if s.get("page_number", s.get("slide_number")) == page), {})
        slide_text = slide_obj.get("raw_text") or slide_obj.get("slide_text") or ""

        novelty = compute_novelty_score(text, slide_text)

        # Ngưỡng 0.28
        if novelty >= 0.28:
            note_type = "addition"
            if any(k in text.lower() for k in ["kiểm tra", "thi", "lưu ý", "quan trọng"]):
                note_type = "exam_hint"
            elif any(k in text.lower() for k in ["nghĩa là", "định nghĩa", "khái niệm", "gọi là"]):
                note_type = "term"

            aligned_notes.append(AlignedNoteItem(
                slide_page=page,
                content=text,
                evidence_quote=text,
                timestamp_start_sec=start_sec,
                timestamp_end_sec=end_sec,
                type=note_type,
                novelty_score=round(novelty, 3)
            ))

    return aligned_notes
