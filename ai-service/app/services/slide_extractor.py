import re
import uuid
from pathlib import Path
from typing import List, Dict, Any, Optional
import fitz  # PyMuPDF
from pptx import Presentation

# Danh sách từ tiếng Việt không dấu/từ thông dụng để không nhận nhầm làm thuật ngữ tiếng Anh
VI_STOPWORDS_ASCII = {
    "va", "cua", "cac", "trong", "la", "co", "duoc", "cho", "voi", "tu", "theo",
    "mot", "nhung", "khi", "de", "khong", "tren", "nay", "den", "ve", "nhu",
    "bai", "chuong", "phan", "vi", "du", "he", "thong", "thuat", "toan", "slide",
    "page", "trang", "nam", "hoc", "khoa", "dai", "cntt", "vku",
    # Các từ tiếng Việt đơn âm phổ biến hay gặp trong slide kỹ thuật
    "khai", "dung", "nhau", "kieu", "bien", "bang", "tam", "gia", "tri", "lenh",
    "thuc", "thi", "nhap", "xuat", "kiem", "tra", "dieu", "kien", "vong", "lap",
    "chua", "biet", "chay", "xem", "doc", "viet", "xoa", "sua", "them", "moi"
}

# Các từ tiếng Anh thông dụng thường viết HOA toàn bộ trên tiêu đề slide (TODAY, TOPICS, LECTURE...)
# Cần loại để tránh nhận nhầm thành từ viết tắt chuyên ngành hoặc tự động IN HOA giữa câu transcript.
COMMON_EN_UPPER_STOPWORDS = {
    "NOTE", "NOTES", "TODAY", "WELCOME", "LECTURE", "LECTURES", "TOPICS", "TOPIC",
    "SUMMARY", "REVIEW", "EXAMPLE", "EXAMPLES", "EXERCISE", "EXERCISES",
    "AGENDA", "OVERVIEW", "OUTLINE", "ROADMAP", "PLAN", "GOALS", "GOAL", "RECAP",
    "WARNING", "TIP", "TIPS", "QUIZ", "EXAM", "HOMEWORK", "PROJECT", "PROJECTS",
    "PART", "SECTION", "CHAPTER", "SLIDE", "SLIDES", "PAGE", "INTRO", "INTRODUCTION",
    "WHAT", "WHY", "HOW", "WHEN", "WHERE", "WHO", "THIS", "THAT", "THESE", "THOSE",
    "WITH", "FROM", "INTO", "ONLY", "ALSO", "NEXT", "LAST", "FIRST", "SECOND", "THIRD",
    "GOOD", "LUCK", "THANK", "THANKS", "QUESTIONS", "QUESTION", "ANSWER", "ANSWERS",
    "READ", "WRITE", "DOING", "DONE", "MORE", "MOST", "SOME", "MANY", "MUCH", "VERY",
    "TRUE", "FALSE", "NONE", "NULL", "TEST", "CODE", "DATA", "TYPE", "TYPES", "LIST",
}

def extract_slide_terms_and_bilingual_pairs(raw_text: str) -> Dict[str, Any]:
    """
    Trích xuất:
    1. domain_terms: Các thuật ngữ chuyên ngành tiếng Anh, từ viết tắt (PEAS, BFS, DFS), ký hiệu (A*, AO*).
    2. bilingual_pairs: Các cặp thuật ngữ song ngữ dạng 'Tiếng Việt (English Term)' trên slide.
    """
    domain_terms = set()
    bilingual_pairs = []

    def _clean_and_validate_term(term: str) -> Optional[str]:
        if not term:
            return None
        # Chuẩn hóa khoảng trắng và loại bỏ ký tự xuống dòng
        cleaned = re.sub(r"\s+", " ", term).strip(" -•:;,.'\"()[]{}")
        if not cleaned or "\n" in cleaned or "\r" in cleaned:
            return None
        # Bỏ các từ nằm trong danh sách stopwords tiếng Việt không dấu
        if cleaned.lower() in VI_STOPWORDS_ASCII:
            return None
        # Với từ đơn: loại từ tiếng Anh thông dụng và từ quá ngắn (< 4 ký tự trừ acronym/A*)
        words = cleaned.split()
        if len(words) == 1:
            if cleaned.upper() in COMMON_EN_UPPER_STOPWORDS:
                return None
            if len(cleaned) < 4 and not (cleaned.isupper() and len(cleaned) >= 2) and not cleaned.endswith("*"):
                return None
        return cleaned

    all_alpha_words = re.findall(r"[A-Za-zÀ-ỹ]+", raw_text)
    ascii_word_ratio = (
        sum(1 for w in all_alpha_words if w.isascii()) / len(all_alpha_words)
        if all_alpha_words else 0.0
    )
    is_predominantly_english = ascii_word_ratio > 0.80

    # 1. Bắt các thuật toán có dấu * như A*, IDA*, AO*
    for star_term in re.findall(r"\b[A-Z]{1,4}\*(?:\s+[A-Za-z]+)?\b", raw_text):
        c = _clean_and_validate_term(star_term)
        if c:
            domain_terms.add(c)

    # 2. Bắt cặp song ngữ dạng: Cụm tiếng Việt (Cụm tiếng Anh) — chỉ áp dụng trên slide tiếng Việt/song ngữ
    if not is_predominantly_english:
        paren_pattern = re.compile(r"([A-ZÀ-Ỹa-zà-ỹ\s]{3,45})\s*\(([A-Za-z0-9\-\*\s]{2,45})\)")
        for vi_part, en_part in paren_pattern.findall(raw_text):
            vi_clean = " ".join(vi_part.split()).strip(" -•:")
            en_clean = _clean_and_validate_term(en_part)
            if en_clean and not en_clean.isdigit():
                domain_terms.add(en_clean)
                if len(vi_clean) >= 3:
                    bilingual_pairs.append({"vi": vi_clean, "en": en_clean})

    # 3. Bắt từ viết tắt chuyên ngành in hoa (giới hạn <= 6 ký tự trên slide thuần tiếng Anh)
    acronym_re = r"\b[A-Z]{2,6}\b" if is_predominantly_english else r"\b[A-Z]{2,8}\b"
    for acronym in re.findall(acronym_re, raw_text):
        c = _clean_and_validate_term(acronym)
        if c:
            domain_terms.add(c)

    # 4. Trích xuất thuật ngữ chuyên ngành / định danh code
    if is_predominantly_english:
        # Slide thuần tiếng Anh (>80% ASCII): bỏ regex cụm 3 từ tự do để tránh nhiễu câu giao tiếp;
        # chỉ giữ CamelCase, tên hàm, snake_case và thuật ngữ ghép dấu gạch nối (VD: PyTorch, T-SQL, UTF-8).
        code_patterns = [
            r"\b[A-Za-z]*[a-z][A-Z][A-Za-z0-9]*\b",
            r"\b[a-zA-Z_][a-zA-Z0-9_]*\(\)",
            r"\b[a-zA-Z]+_[a-zA-Z0-9_]+\b",
            r"\b[A-Z0-9]+-[A-Za-z0-9]+\b",
        ]
        for pat in code_patterns:
            for match in re.findall(pat, raw_text):
                cleaned_tok = match.strip()
                if cleaned_tok.endswith("()"):
                    if len(cleaned_tok) >= 4:
                        domain_terms.add(cleaned_tok)
                else:
                    c = _clean_and_validate_term(cleaned_tok)
                    if c:
                        domain_terms.add(c)
    else:
        for phrase in re.findall(r"\b[A-Za-z][A-Za-z\-]+(?:\s+[A-Za-z][A-Za-z\-]+){0,2}\b", raw_text):
            if phrase.isascii():
                c = _clean_and_validate_term(phrase)
                if c:
                    domain_terms.add(c)

    return {
        "domain_terms": sorted(domain_terms),
        "bilingual_pairs": bilingual_pairs
    }

def extract_pdf_slide_deck(pdf_path: str) -> List[Dict[str, Any]]:
    """Đọc file PDF và tách cấu trúc từng trang slide."""
    doc = fitz.open(pdf_path)
    slides = []

    for page_idx, page in enumerate(doc, start=1):
        # Sắp xếp các block theo thứ tự từ trên xuống dưới (y0, x0)
        blocks = sorted(page.get_text("blocks"), key=lambda b: (round(b[1], 1), round(b[0], 1)))
        lines = []
        for b in blocks:
            if b[6] == 0:  # Chỉ lấy text block (bỏ qua image block)
                for line in b[4].splitlines():
                    cleaned = " ".join(line.split()).strip()
                    if cleaned and not cleaned.isdigit():  # Bỏ số trang đơn lẻ ở footer
                        lines.append(cleaned)

        title = lines[0] if lines else f"Slide {page_idx}"
        bullets = lines[1:] if len(lines) > 1 else lines
        raw_text = "\n".join(lines)
        term_info = extract_slide_terms_and_bilingual_pairs(raw_text)

        slides.append({
            "slide_number": page_idx,
            "title": title[:500],
            "raw_text": raw_text,
            "bullets": bullets,
            "extracted_terms": term_info["domain_terms"],
            "bilingual_pairs": term_info["bilingual_pairs"],
            "qdrant_point_id": str(uuid.uuid4())
        })

    doc.close()
    return slides

def extract_pptx_slide_deck(pptx_path: str) -> List[Dict[str, Any]]:
    """Đọc file PPTX và tách cấu trúc từng trang slide."""
    prs = Presentation(pptx_path)
    slides = []

    for page_idx, slide in enumerate(prs.slides, start=1):
        title = (
            slide.shapes.title.text.strip()
            if (slide.shapes.title and slide.shapes.title.text)
            else f"Slide {page_idx}"
        )
        lines = []
        for shape in slide.shapes:
            if shape.has_text_frame:
                for para in shape.text_frame.paragraphs:
                    cleaned = " ".join(para.text.split()).strip()
                    if cleaned and not cleaned.isdigit():
                        lines.append(cleaned)

        bullets = [l for l in lines if l != title] or lines
        raw_text = "\n".join(lines)
        term_info = extract_slide_terms_and_bilingual_pairs(raw_text)

        slides.append({
            "slide_number": page_idx,
            "title": title[:500],
            "raw_text": raw_text,
            "bullets": bullets,
            "extracted_terms": term_info["domain_terms"],
            "bilingual_pairs": term_info["bilingual_pairs"],
            "qdrant_point_id": str(uuid.uuid4())
        })

    return slides

def extract_slide_deck(file_path: str) -> List[Dict[str, Any]]:
    ext = Path(file_path).suffix.lower()
    if ext == ".pdf":
        return extract_pdf_slide_deck(file_path)
    elif ext == ".pptx":
        return extract_pptx_slide_deck(file_path)
    raise ValueError(f"Định dạng không hỗ trợ: {ext}")
