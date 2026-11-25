import re
import math
from typing import List, Dict, Any, Optional, Set
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

# Danh sách mẫu câu giao tiếp / câu đệm cần lọc bỏ (hỗ trợ cả Tiếng Việt & Tiếng Anh)
FILLER_PATTERNS = [
    # --- Tiếng Việt ---
    r"nghe rõ không",
    r"nhìn lên màn hình",
    r"ổn định chỗ ngồi",
    r"điểm danh",
    r"vắng mặt",
    r"lớp phó",
    r"các em trật tự",
    r"có ai hỏi gì không",
    r"thầy nhắc lại là",
    # --- Tiếng Anh (Classroom / Lecture Fillers) ---
    r"can (everyone|you all|you guys) hear me",
    r"can you see (my|the) screen",
    r"any questions (so far|about this|before we move on)",
    r"does that make sense",
    r"let'?s take a (quick |short )?break",
    r"welcome back everyone",
    r"good (morning|afternoon|evening) everyone",
    r"before we get started",
    r"let'?s get started",
    r"thank you (very much )?for coming",
    r"see you (next (week|time|class)|on (monday|tuesday|wednesday|thursday|friday))",
]

# Stopwords cơ bản giúp compute_token_overlap không bị nhiễu khi giảng full tiếng Anh hoặc tiếng Việt
STOPWORDS: Set[str] = {
    # English stopwords
    "a", "an", "the", "and", "or", "but", "if", "then", "else", "when", "at", "by",
    "for", "with", "about", "against", "between", "into", "through", "during", "before",
    "after", "above", "below", "to", "from", "up", "down", "in", "out", "on", "off",
    "over", "under", "again", "further", "once", "here", "there", "where", "why", "how",
    "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no",
    "nor", "not", "only", "own", "same", "so", "than", "too", "very", "s", "t", "can",
    "will", "just", "don", "should", "now", "is", "are", "was", "were", "be", "been",
    "being", "have", "has", "had", "do", "does", "did", "of", "this", "that", "these",
    "those", "it", "its", "we", "you", "they", "he", "she", "i", "me", "my", "our", "your",
    # Vietnamese stopwords
    "và", "là", "của", "các", "những", "một", "trong", "cho", "với", "để", "từ", "tại",
    "này", "đó", "khi", "thì", "mà", "như", "được", "có", "không", "sẽ", "đang", "đã",
    "rất", "cũng", "về", "trên", "dưới", "theo", "hay", "hoặc", "nhưng", "vì", "nên",
}

_SPOKEN_WEIGHT_STOPWORDS: Set[str] = STOPWORDS | {
    "tôi", "tui", "mình", "bạn", "em", "ta", "nó", "họ", "cái",
    "đây", "đấy", "rồi", "nha", "nhé", "ha", "hả", "ạ",
    "ờ", "à", "ừ", "ok", "nè", "nhỉ", "chứ", "thôi", "đi",
}


def is_filler_sentence(text: str) -> bool:
    """Loại bỏ câu đệm giao tiếp (VI & EN) nếu < 5 từ hoặc khớp mẫu điểm danh/xã giao."""
    cleaned = text.strip()
    if not cleaned:
        return True
    words = cleaned.split()
    if len(words) < 5:
        return True
    lower_text = cleaned.lower()
    for pattern in FILLER_PATTERNS:
        if re.search(pattern, lower_text):
            return True
    return False


def _extract_content_tokens(text: str) -> Set[str]:
    """Tách từ khóa có nghĩa (bỏ stopwords & từ 1 ký tự trừ ký hiệu toán/lập trình)."""
    raw_tokens = re.findall(r"[a-zA-Z0-9_\*\+#]+|[^\W\d_]+", text.lower())
    filtered = {
        tok for tok in raw_tokens
        if tok not in STOPWORDS and (len(tok) >= 2 or tok in {"c", "r", "x", "y", "n", "k"})
    }
    return filtered if filtered else set(raw_tokens)


def _extract_substantive_tokens(text: str) -> Set[str]:
    """Tách từ thực chất để tính trọng số bằng chứng Viterbi (câu đệm/đại từ thuần túy -> 0)."""
    raw_tokens = re.findall(r"[a-zA-Z0-9_\*\+#]+|[^\W\d_]+", text.lower())
    return {
        tok for tok in raw_tokens
        if tok not in _SPOKEN_WEIGHT_STOPWORDS and (len(tok) >= 2 or tok in {"c", "r", "x", "y", "n", "k"})
    }


def _extract_technical_ascii_tokens(text: str) -> Set[str]:
    """Chỉ lấy từ khóa kỹ thuật/ký hiệu/số ASCII để tránh từ tiếng Việt đơn lẻ làm lệch khớp song ngữ."""
    toks = re.findall(r"[a-zA-Z0-9_\*\+#@]+", text.lower())
    return {
        t for t in toks
        if t.isascii() and t not in STOPWORDS and (len(t) >= 2 or "*" in t or "#" in t or "+" in t)
    }


def _compute_technical_ascii_overlap(segment_text: str, slide_text: str) -> float:
    tu = _extract_technical_ascii_tokens(segment_text)
    if not tu:
        return 0.0
    ts = _extract_technical_ascii_tokens(slide_text)
    return len(tu & ts) / len(tu)


def compute_token_overlap(segment_text: str, slide_text: str) -> float:
    """
    Tính tỉ lệ từ khóa chuyên ngành trùng nhau giữa đoạn transcript và slide:
    |ContentTokens(u_t) ∩ ContentTokens(s_k)| / |ContentTokens(u_t)|
    Giúp DTW căn gióng chính xác hơn trên bài giảng full tiếng Anh (tránh nhiễu bởi 'the', 'is', 'of'...).
    """
    tokens_seg = _extract_content_tokens(segment_text)
    if not tokens_seg:
        return 0.0
    tokens_slide = _extract_content_tokens(slide_text)
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
    gamma_next: float = 0.015,
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
    Đã vá lỗi 'Hố đen Mục lục':
    - Giảm gamma_next xuống 0.015 giúp bước chuyển slide mượt mà.
    - Thêm overview_penalty = 0.12 cho các slide mục lục/tổng quan sau phút đầu tiên (start_sec > 60s).
    """
    T = len(transcript_segments)
    K = len(slides)
    if T == 0 or K == 0:
        return []

    # Nhận diện các slide mục lục / tổng quan
    overview_keywords = [
        "tổng quan", "mục lục", "nội dung bài học", "nội dung chính", 
        "agenda", "overview", "table of contents", "outline"
    ]
    is_overview_slide = [False] * K
    for k in range(K):
        s_title = (slides[k].get("title") or "").lower()
        s_text = (slides[k].get("raw_text") or slides[k].get("slide_text") or "").lower()
        if any(kw in s_title for kw in overview_keywords) or any(kw in s_text[:120] for kw in overview_keywords):
            is_overview_slide[k] = True

    # 1. Ma trận chi phí C[t][k] = 1 - CosineSim(u_t, s_k) + overview_penalty
    C = [[1.0 for _ in range(K)] for _ in range(T)]
    for t in range(T):
        u_text = transcript_segments[t].get("text", "")
        start_sec = float(transcript_segments[t].get("start_sec", transcript_segments[t].get("start_time_sec", 0.0)))
        for k in range(K):
            s_text = slides[k].get("raw_text") or slides[k].get("slide_text") or ""
            sim = compute_semantic_similarity(u_text, s_text)
            cost = 1.0 - sim
            if start_sec > 60.0 and is_overview_slide[k]:
                cost += 0.12
            C[t][k] = cost

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


# ==============================================================================
# TUẦN 3: Windowed Non-Monotonic Viterbi trên vector đa ngôn ngữ (BGE-M3 / e5)
# ==============================================================================
_OVERVIEW_KEYWORDS = [
    "tổng quan", "mục lục", "nội dung bài học", "nội dung chính",
    "agenda", "overview", "table of contents", "outline",
]


def _viterbi_transition_matrix(K: int):
    import numpy as np
    TR = np.zeros((K, K), dtype=np.float64)
    for j in range(K):
        for k in range(K):
            d = k - j
            if d == 0:
                c = 0.0
            elif d == 1:
                c = 0.06
            elif d == 2:
                c = 0.17
            elif d > 2:
                c = 0.30 + 0.025 * d
            elif d == -1:
                c = 0.27
            elif d == -2:
                c = 0.32
            else:
                c = 0.32 + 0.025 * abs(d)
            TR[j, k] = c
    return TR


def viterbi_from_scores(
    score_mat,
    start_secs: List[float],
    is_overview: List[bool],
    overview_penalty: float = 0.15,
    weights=None,
) -> List[int]:
    """Chạy Viterbi trên ma trận điểm khớp (T, K). Trả về chỉ số trang 0-indexed."""
    import numpy as np
    T, K = score_mat.shape
    C = 1.0 - score_mat.astype(np.float64)
    if weights is not None:
        # Câu ít thông tin (ờ, rồi, ok...) có bằng chứng yếu: giữ nguyên trang đang giảng
        C = C * np.asarray(weights, dtype=np.float64)[:, None]
    ov = np.array(is_overview, dtype=bool)
    for t in range(T):
        if start_secs[t] > 60.0:
            C[t, ov] += overview_penalty

    TR = _viterbi_transition_matrix(K)
    D = np.zeros((T, K), dtype=np.float64)
    bt = np.zeros((T, K), dtype=np.int32)
    D[0] = C[0] + 0.04 * np.arange(K)
    for t in range(1, T):
        cand = D[t - 1][:, None] + TR          # (j, k)
        bt[t] = np.argmin(cand, axis=0)
        D[t] = C[t] + cand[bt[t], np.arange(K)]

    path = [0] * T
    k = int(np.argmin(D[T - 1]))
    for t in range(T - 1, -1, -1):
        path[t] = k
        if t > 0:
            k = int(bt[t, k])
    return path


def _slide_code_density(raw_text: str) -> float:
    """Tỷ lệ dòng thân slide chứa ký hiệu cú pháp lập trình/toán học (code-heavy slide)."""
    lines = [ln.strip() for ln in (raw_text or "").splitlines() if ln.strip()]
    body = lines[1:] if len(lines) > 1 else lines
    if not body:
        return 0.0
    code_chars = set("()[]{};=@<>+-*/")
    code_lines = sum(1 for ln in body if sum(1 for ch in ln if ch in code_chars) >= 2 or ln.isupper())
    return code_lines / len(body)


def compute_hybrid_score_matrix(
    transcript_segments: List[Dict[str, Any]],
    slides: List[Dict[str, Any]],
    semantic_weight: float = 0.75,
):
    import numpy as np
    from app.services.semantic_embedder import get_semantic_embedder

    embedder = get_semantic_embedder()
    u_texts = [seg.get("text", "") for seg in transcript_segments]
    s_texts = [(sl.get("raw_text") or sl.get("slide_text") or "") for sl in slides]
    s_titles = [
        ((sl.get("title") or "").strip() or (s_texts[i].splitlines()[0].strip() if s_texts[i] else ""))
        for i, sl in enumerate(slides)
    ]
    sim_full = embedder.similarity_matrix(u_texts, s_texts)
    sim_title = embedder.similarity_matrix(u_texts, s_titles)
    # Chỉ bù ngữ nghĩa tiêu đề cho các slide nặng code mẫu (tỷ lệ dòng code >= 0.45) để tránh tiêu đề ngắn gây nhiễu
    code_mask = np.array([0.35 if _slide_code_density(txt) >= 0.45 else 0.0 for txt in s_texts], dtype=np.float64)[None, :]
    sim = sim_full + code_mask * np.maximum(0.0, sim_title - sim_full)

    lex_cap = min(0.12, max(0.0, 1.0 - semantic_weight))
    score = np.zeros(sim.shape, dtype=np.float64)
    for t, u in enumerate(u_texts):
        ov_weight = lex_cap if _extract_technical_ascii_tokens(u) else 0.0
        for k, s in enumerate(s_texts):
            score[t, k] = (1.0 - ov_weight) * float(sim[t, k]) + ov_weight * _compute_technical_ascii_overlap(u, s)
    return score


def slide_is_overview(slide: Dict[str, Any]) -> bool:
    title = (slide.get("title") or "").lower()
    body = (slide.get("raw_text") or slide.get("slide_text") or "").lower()[:120]
    return any(kw in title or kw in body for kw in _OVERVIEW_KEYWORDS)


def viterbi_semantic_alignment(
    transcript_segments: List[Dict[str, Any]],
    slides: List[Dict[str, Any]],
    semantic_weight: float = 0.75,
) -> List[Dict[str, Any]]:
    """
    Căn gióng cấp trang bằng Windowed Non-Monotonic Viterbi.
    Trả về list dict: slide_page (1-indexed), semantic_score, jumped_non_linear.
    """
    import numpy as np
    T, K = len(transcript_segments), len(slides)
    if T == 0 or K == 0:
        return []
    raw_score = compute_hybrid_score_matrix(transcript_segments, slides, semantic_weight)
    score = raw_score.copy()
    weights = np.array(
        [min(1.0, len(_extract_substantive_tokens(seg.get("text", ""))) / 3.0) for seg in transcript_segments],
        dtype=np.float64,
    )
    subst_mask = weights >= 0.66
    if subst_mask.sum() >= 20 and K >= 5:
        # Khử hubness chỉ trên các câu có nội dung thực (tránh câu đệm 1 từ như 'Rồi', 'OK' làm lệch phân vị)
        bg = np.percentile(score[subst_mask], 40, axis=0, keepdims=True)
        score = score - bg + bg.mean()
    elif T >= 30:
        bg = np.percentile(score, 25, axis=0, keepdims=True)
        score = score - bg + bg.mean()

    starts = [
        float(s.get("start_sec", s.get("start_time_sec", 0.0))) for s in transcript_segments
    ]
    ends = [
        float(s.get("end_sec", s.get("end_time_sec", starts[i]))) for i, s in enumerate(transcript_segments)
    ]
    # Làm mượt ngữ cảnh cục bộ giữa các mệnh đề liền kề (< 3.5s) để câu cụt không kéo trôi trang
    smoothed = score.copy()
    for t in range(T):
        w_sum = 0.65
        acc = 0.65 * score[t]
        if t > 0 and (starts[t] - ends[t - 1]) <= 3.5 and weights[t - 1] > 0.3:
            acc += 0.175 * score[t - 1]
            w_sum += 0.175
        if t + 1 < T and (starts[t + 1] - ends[t]) <= 3.5 and weights[t + 1] > 0.3:
            acc += 0.175 * score[t + 1]
            w_sum += 0.175
        smoothed[t] = acc / w_sum

    path = viterbi_from_scores(smoothed, starts, [slide_is_overview(sl) for sl in slides], weights=weights.tolist())
    results = []
    for t in range(T):
        k = path[t]
        jumped = t > 0 and (path[t] < path[t - 1] or path[t] > path[t - 1] + 1)
        results.append({
            "slide_page": k + 1,
            "semantic_score": round(float(raw_score[t, k]), 4),
            "jumped_non_linear": bool(jumped),
        })
    return results
