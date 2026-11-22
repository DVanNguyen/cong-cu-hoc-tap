import os
from typing import List, Dict
import pypdf
import unicodedata

def extract_pdf_slides(pdf_path: str) -> List[Dict[str, any]]:
    """
    Trích xuất văn bản từ từng trang slide PDF (ví dụ từ C:\\AI).
    Trả về danh sách: [{'page_number': 1, 'text': '...', 'char_count': ...}]
    """
    if not os.path.exists(pdf_path):
        raise FileNotFoundError(f"Không tìm thấy file slide tại: {pdf_path}")
        
    reader = pypdf.PdfReader(pdf_path)
    slides = []
    
    for idx, page in enumerate(reader.pages):
        raw_text = page.extract_text() or ""
        clean_text = unicodedata.normalize('NFC', raw_text).strip()
        slides.append({
            "page_number": idx + 1,
            "text": clean_text,
            "char_count": len(clean_text)
        })
        
    return slides
