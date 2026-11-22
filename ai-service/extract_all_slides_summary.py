import sys
import os
import json
from pathlib import Path

if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from app.services.slide_extractor import extract_slide_deck

slides_dir = Path(r"c:\Users\Nguyen\AndroidStudioProjects\StudyAI\dataset_eval\slides")
pdf_files = list(slides_dir.glob("*.pdf"))

print(f"==================================================================")
print(f"TRÍCH XUẤT NỘI DUNG & THUẬT NGỮ TỪ {len(pdf_files)} BỘ SLIDE MÔN TRÍ TUỆ NHÂN TẠO")
print(f"==================================================================\n")

total_slides_all = 0
all_unique_terms = set()
all_bilingual_pairs = []

for pdf in sorted(pdf_files):
    slides = extract_slide_deck(str(pdf))
    total_slides_all += len(slides)
    
    file_terms = set()
    file_pairs = []
    for s in slides:
        file_terms.update(s["extracted_terms"])
        file_pairs.extend(s["bilingual_pairs"])
    
    all_unique_terms.update(file_terms)
    all_bilingual_pairs.extend(file_pairs)

    print(f"📄 File: {pdf.name}")
    print(f"   - Số trang slide: {len(slides)}")
    print(f"   - Thuật ngữ chuyên ngành rút được: {len(file_terms)} từ ({', '.join(sorted(list(file_terms))[:8])}...)")
    print(f"   - Cặp song ngữ Việt - Anh: {len(file_pairs)} cặp")
    if file_pairs:
        for p in file_pairs[:3]:
            print(f"     • {p['vi']} <---> {p['en']}")
    print()

print(f"------------------------------------------------------------------")
print(f"TỔNG KẾT: {total_slides_all} trang slide trích xuất hoàn hảo.")
print(f"TỔNG SỐ THUẬT NGỮ DUY NHẤT: {len(all_unique_terms)} thuật ngữ.")
print(f"TỔNG SỐ CẶP SONG NGỮ VIỆT - ANH: {len(all_bilingual_pairs)} cặp.")
print(f"------------------------------------------------------------------")
