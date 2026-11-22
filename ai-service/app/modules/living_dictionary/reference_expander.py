import os
import re
import asyncio
import httpx
import numpy as np
from urllib.parse import urlparse, quote
from typing import List, Dict, Any, Optional, Tuple

# Bảng trọng số uy tín tên miền học thuật & kỹ thuật (Domain Authority Weights)
TRUSTED_DOMAIN_WEIGHTS = {
    "wikipedia.org": 0.95,
    "arxiv.org": 1.00,
    "semanticscholar.org": 0.98,
    "ieee.org": 1.00,
    "acm.org": 1.00,
    "stanford.edu": 1.00,
    "mit.edu": 1.00,
    "cs50.harvard.edu": 0.98,
    "geeksforgeeks.org": 0.88,
    "viblo.asia": 0.86,
    "huggingface.co": 0.92,
    "pytorch.org": 0.92,
    "scikit-learn.org": 0.92,
    "towardsdatascience.com": 0.85,
    "machinelearningmastery.com": 0.88,
    "youtube.com": 0.82,
}

def get_domain_authority(url: str) -> Tuple[str, float]:
    try:
        host = urlparse(url).netloc.lower().replace("www.", "")
        for domain_key, weight in TRUSTED_DOMAIN_WEIGHTS.items():
            if host.endswith(domain_key):
                return host, weight
        if host.endswith(".edu") or host.endswith(".ac.vn") or host.endswith(".edu.vn"):
            return host, 0.95
        return host, 0.55
    except Exception:
        return "external.source", 0.50

# Danh sách các từ khóa hành chính / tiêu đề tổ chức môn học không phải khái niệm kỹ thuật
GENERIC_COURSE_TERMS = {
    "giới thiệu", "mục tiêu", "tổng quan", "nội dung", "kết luận", "tài liệu tham khảo",
    "bài tập", "thực hành", "ôn tập", "thời khóa biểu", "tiêu chuẩn đánh giá",
    "overview", "introduction", "objectives", "learning objectives", "course overview",
    "agenda", "summary", "conclusion", "references", "exercise", "homework", "lab", "outline", "syllabus"
}

def compute_concept_abstractness_score(
    concept_name_vi: str,
    concept_name_en: str,
    slide_raw_text: str,
    is_first_occurrence: bool = True
) -> float:
    """
    Thuật toán 1: Tính điểm độ trừu tượng & mức độ thiếu hụt giải thích trên khung sườn Slide.
    A(c, Sk) = 0.45 * SkeletonSparsity + 0.35 * TechSpec + 0.20 * FirstSeen
    """
    # 1. Kiểm tra nếu là thuật ngữ hành chính/tổ chức bài giảng thông thường (không phải khái niệm cốt lõi)
    c_vi_lower = concept_name_vi.lower().strip() if concept_name_vi else ""
    c_en_lower = concept_name_en.lower().strip() if concept_name_en else ""
    
    is_generic = any(
        gen in c_vi_lower or gen in c_en_lower or c_vi_lower.startswith(gen) or c_en_lower.startswith(gen)
        for gen in GENERIC_COURSE_TERMS
    )
    if is_generic:
        # Thuật ngữ tổ chức/mục tiêu chung: điểm trừu tượng rất thấp, không mở rộng từ điển bên ngoài
        return round(float(0.12), 4)

    # 2. Độ cô đọng trên Slide (Skeleton Sparsity)
    words_on_slide = slide_raw_text.split() if slide_raw_text else []
    explain_len = max(0, len(words_on_slide) - len(concept_name_vi.split()))
    skeleton_sparsity = 1.0 - min(1.0, explain_len / 40.0)

    # 3. Độ chuyên biệt kỹ thuật (TechSpec)
    # Kiểm tra từ viết tắt in hoa (GAN, PEAS, BFS, DFS, MDP, A*, v.v.)
    has_acronym = bool(re.search(r"\b[A-Z]{2,6}\*?\b", f"{concept_name_vi} {concept_name_en}"))
    is_acronym_score = 1.0 if has_acronym else 0.0
    
    # Có thuật ngữ song ngữ tiếng Anh chuyên ngành đối chiếu
    has_en = bool(concept_name_en and concept_name_en.strip().lower() != c_vi_lower)
    has_en_score = 0.9 if has_en else 0.2
    
    tech_spec = 0.6 * has_en_score + 0.4 * is_acronym_score

    # 4. Xuất hiện lần đầu trong môn học
    first_seen_bonus = 1.0 if is_first_occurrence else 0.3

    score = 0.45 * skeleton_sparsity + 0.35 * tech_spec + 0.20 * first_seen_bonus
    return round(float(np.clip(score, 0.0, 1.0)), 4)

async def fetch_wikipedia_reference(
    client: httpx.AsyncClient,
    query_term: str,
    lang: str = "vi"
) -> List[Dict[str, Any]]:
    """
    Truy vấn Wikipedia MediaWiki API (vi/en) lấy định nghĩa chuẩn và đường link tham khảo.
    """
    if not query_term:
        return []
    api_url = f"https://{lang}.wikipedia.org/w/api.php"
    params = {
        "action": "query",
        "list": "search",
        "srsearch": query_term,
        "utf8": 1,
        "format": "json",
        "srlimit": 2,
    }
    try:
        resp = await client.get(api_url, params=params, timeout=6.0)
        data = resp.json()
        results = []
        for item in data.get("query", {}).get("search", []):
            title = item["title"]
            snippet = re.sub(r"<[^>]+>", "", item.get("snippet", "")).strip()
            page_url = f"https://{lang}.wikipedia.org/wiki/{quote(title.replace(' ', '_'))}"
            results.append({
                "title": f"{title} — Wikipedia ({lang.upper()})",
                "url": page_url,
                "source_provider": "WIKIPEDIA",
                "snippet_summary": snippet or f"Tài liệu bách khoa tổng quan về {title}.",
                "citation_badge": f"[Wiki {lang.upper()}]",
            })
        return results
    except Exception:
        return []

async def fetch_semantic_scholar_papers(
    client: httpx.AsyncClient,
    concept_en: str,
    course_domain_en: str = "Artificial Intelligence Computer Science"
) -> List[Dict[str, Any]]:
    """
    Truy vấn Semantic Scholar Academic Graph API để lấy các bài báo khoa học nền tảng.
    """
    if not concept_en:
        return []
    search_query = f"{concept_en} {course_domain_en}".strip()
    url = "https://api.semanticscholar.org/graph/v1/paper/search"
    params = {
        "query": search_query,
        "limit": 2,
        "fields": "title,url,abstract,year,citationCount,openAccessPdf"
    }
    try:
        resp = await client.get(url, params=params, timeout=7.0)
        if resp.status_code != 200:
            return []
        papers = resp.json().get("data", [])
        results = []
        for p in papers:
            pdf_info = p.get("openAccessPdf")
            target_url = (pdf_info.get("url") if pdf_info else None) or p.get("url")
            if not target_url:
                continue
            year = p.get("year") or "N/A"
            cites = p.get("citationCount") or 0
            abstract_txt = (p.get("abstract") or "")[:240]
            results.append({
                "title": f"{p.get('title')} ({year})",
                "url": target_url,
                "source_provider": "SEMANTIC_SCHOLAR",
                "snippet_summary": (
                    f"Bài báo khoa học ({cites} trích dẫn): {abstract_txt}..."
                    if abstract_txt else f"Công trình nghiên cứu học thuật về {concept_en} ({cites} trích dẫn)."
                ),
                "citation_badge": "[Paper Học thuật]",
            })
        return results
    except Exception:
        return []

async def fetch_tavily_or_curated_tutorials(
    client: httpx.AsyncClient,
    concept_vi: str,
    concept_en: str,
    course_title: str = "Trí tuệ nhân tạo"
) -> List[Dict[str, Any]]:
    """
    Truy vấn Tavily Search API (nếu có TAVILY_API_KEY)
    hoặc tạo liên kết tra cứu sâu trực tiếp tới GeeksforGeeks, Viblo, ArXiv & YouTube bài giảng.
    """
    tavily_key = os.getenv("TAVILY_API_KEY", "").strip()
    query_str = f"{concept_vi} ({concept_en}) trong {course_title} hướng dẫn chi tiết ví dụ"
    if tavily_key:
        try:
            resp = await client.post(
                "https://api.tavily.com/search",
                json={
                    "api_key": tavily_key,
                    "query": query_str,
                    "search_depth": "basic",
                    "max_results": 3,
                },
                timeout=8.0,
            )
            if resp.status_code == 200:
                items = resp.json().get("results", [])
                return [
                    {
                        "title": it.get("title", concept_en),
                        "url": it.get("url"),
                        "source_provider": "WEB_TUTORIAL",
                        "snippet_summary": (it.get("content") or "")[:240],
                        "citation_badge": "[Bài viết Chuyên sâu]",
                    }
                    for it in items if it.get("url")
                ]
        except Exception:
            pass

    # Fallback chất lượng cao đảm bảo luôn có nguồn tham khảo đa dạng ngay cả khi offline/không có API key trả phí
    term_target = concept_en if concept_en else concept_vi
    q_encoded = quote(f"{term_target} {course_title}")
    q_en_encoded = quote(term_target)
    return [
        {
            "title": f"Hướng dẫn thuật toán & Cấu trúc chi tiết: {term_target} — GeeksforGeeks",
            "url": f"https://www.geeksforgeeks.org/search/?q={q_en_encoded}",
            "source_provider": "WEB_TUTORIAL",
            "snippet_summary": f"Phân tích từng bước nguyên lý hoạt động, độ phức tạp thuật toán và mã nguồn cài đặt mẫu cho {term_target}.",
            "citation_badge": "[Hướng dẫn & Code mẫu]",
        },
        {
            "title": f"Bài viết kỹ thuật tiếng Việt về {concept_vi} ({term_target}) — Viblo",
            "url": f"https://viblo.asia/search?q={quote(concept_vi + ' ' + term_target)}",
            "source_provider": "WEB_TUTORIAL",
            "snippet_summary": f"Tổng hợp các bài phân tích chuyên sâu bằng tiếng Việt và ví dụ minh họa thực tế về {concept_vi}.",
            "citation_badge": "[Blog Kỹ thuật VN]",
        },
        {
            "title": f"Video minh họa trực quan cơ chế hoạt động của {term_target} — YouTube Edu",
            "url": f"https://www.youtube.com/results?search_query={q_encoded}+explained+animation",
            "source_provider": "YOUTUBE_VIDEO",
            "snippet_summary": f"Video đồ họa trực quan giúp hình dung từng bước chạy của {concept_vi} ({term_target}) trong thực tế.",
            "citation_badge": "[Video Trực quan]",
        }
    ]

async def expand_concept_living_dictionary(
    concept_vi: str,
    concept_en: str,
    slide_raw_text: str = "",
    lecturer_quote: str = "",
    embedder=None,
    course_title: str = "Trí tuệ nhân tạo"
) -> Dict[str, Any]:
    """
    Hàm tổng hợp cho Trụ cột 2 ("Từ điển sống"):
    1. Chấm điểm độ trừu tượng (Abstractness Score).
    2. Thu thập tài liệu tham khảo bất đồng bộ từ Wikipedia VI/EN, Semantic Scholar & Web Tutorials.
    3. Xếp hạng từng link theo công thức: 0.65 * ContextSim + 0.25 * Authority + 0.10 * PedagogicalScore.
    4. Tạo thẻ Từ điển sống (Định nghĩa cốt lõi + Lời giảng trên lớp + Giải thích đào sâu + Top Links).
    """
    abstractness = compute_concept_abstractness_score(concept_vi, concept_en, slide_raw_text)

    async with httpx.AsyncClient(headers={"User-Agent": "StudyAI-VKU-ResearchBot/2.0"}) as client:
        wiki_vi_task = fetch_wikipedia_reference(client, f"{concept_vi} khoa học máy tính", lang="vi")
        wiki_en_task = fetch_wikipedia_reference(client, f"{concept_en or concept_vi} artificial intelligence", lang="en")
        scholar_task = fetch_semantic_scholar_papers(client, concept_en or concept_vi)
        tutorial_task = fetch_tavily_or_curated_tutorials(client, concept_vi, concept_en, course_title)

        gathered = await asyncio.gather(wiki_vi_task, wiki_en_task, scholar_task, tutorial_task, return_exceptions=True)

    raw_candidates = []
    for item in gathered:
        if isinstance(item, list):
            raw_candidates.extend(item)

    # Ngữ cảnh chuẩn của bài học để tính Cosine Similarity khử nhập nhằng
    context_str = f"{course_title}. {concept_vi} ({concept_en}). {slide_raw_text} {lecturer_quote}"
    context_vec = embedder.encode(context_str, normalize_embeddings=True) if embedder else None

    ranked_references = []
    seen_urls = set()

    for cand in raw_candidates:
        url = cand["url"]
        if url in seen_urls:
            continue
        seen_urls.add(url)

        domain_name, auth_weight = get_domain_authority(url)
        cand_text = f"{cand['title']}. {cand['snippet_summary']}"

        if embedder is not None and context_vec is not None:
            cand_vec = embedder.encode(cand_text, normalize_embeddings=True)
            context_sim = float(np.clip(np.dot(context_vec, cand_vec), 0.0, 1.0))
        else:
            context_sim = 0.85

        pedagogical_score = min(1.0, len(cand["snippet_summary"].split()) / 25.0)
        final_score = round(0.65 * context_sim + 0.25 * auth_weight + 0.10 * pedagogical_score, 4)

        ranked_references.append({
            "title": cand["title"],
            "url": url,
            "source_provider": cand["source_provider"],
            "domain_name": domain_name,
            "snippet_summary": cand["snippet_summary"],
            "context_similarity": round(context_sim, 4),
            "authority_weight": auth_weight,
            "final_rank_score": final_score,
            "citation_badge": cand["citation_badge"],
        })

    # Sắp xếp giảm dần theo final_rank_score và giữ Top 5 liên kết chất lượng nhất
    ranked_references.sort(key=lambda x: x["final_rank_score"], reverse=True)
    top_refs = ranked_references[:5]

    # Tổng hợp nội dung "Từ điển sống"
    wiki_summary = next((r["snippet_summary"] for r in top_refs if r["source_provider"] == "WIKIPEDIA"), "")
    ai_deep_explanation = (
        f"• Bản chất cốt lõi: {concept_vi} ({concept_en}) là một khái niệm trọng tâm trong {course_title}. "
        f"{wiki_summary}\n"
        f"• Vì sao cần nắm vững trên khung sườn Slide này: Slide bài giảng chỉ tóm tắt tên gọi/công thức ngắn gọn, "
        f"bạn cần kết hợp phần giải thích miệng của giảng viên và các tài liệu tham khảo bên dưới để hiểu rõ cơ chế hoạt động."
    )

    return {
        "concept_name_vi": concept_vi,
        "concept_name_en": concept_en,
        "abstractness_score": abstractness,
        "lecturer_oral_explanation": lecturer_quote or "Giảng viên nhắc tới thuật ngữ này trên khung sườn slide.",
        "ai_deep_explanation": ai_deep_explanation,
        "external_references": top_refs,
    }
