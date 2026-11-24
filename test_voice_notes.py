#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Công cụ kiểm thử nhanh: Gửi tệp âm thanh (Voice File) và nhận vở ghi chép thông minh từ AI.
Sử dụng:
    python test_voice_notes.py [đường_dẫn_tệp_audio]
"""

import sys
import os
import requests
import json

def test_voice_notes(audio_path: str = None):
    default_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "dataset_eval", "my_test", "test_voice_30s.m4a"
    )

    target_file = audio_path or default_path
    if not os.path.exists(target_file):
        # Fallback to test_5min.m4a or TSQL_Lecture09.m4a if available
        alt_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dataset_eval", "my_test", "test_5min.m4a")
        if os.path.exists(alt_path):
            target_file = alt_path
        else:
            print(f"❌ LỖI: Không tìm thấy tệp âm thanh tại: {target_file}")
            sys.exit(1)

    print("=" * 70)
    print("🎙️ TEST STUDY-AI: GHI CHÚ BÀI GIẢNG TỪ GIỌNG NÓI (VOICE ONLY)")
    print("=" * 70)
    print(f"📁 Tệp âm thanh đầu vào: {target_file}")
    print(f"📊 Dung lượng: {os.path.getsize(target_file) / 1024:.1f} KB")
    print("⏳ Đang gửi tệp tới AI Service (Faster-Whisper ASR + Google Gemini)...")

    url = "http://127.0.0.1:8001/api/v1/pipeline/test-voice-notes"

    try:
        with open(target_file, "rb") as f:
            files = {"audio_file": (os.path.basename(target_file), f, "audio/mp4")}
            data = {"title": "Kiểm thử ghi âm bài giảng", "language": "vi"}
            resp = requests.post(url, files=files, data=data, timeout=300)

        if resp.status_code != 200:
            print(f"❌ Server trả về lỗi HTTP {resp.status_code}: {resp.text}")
            return

        res = resp.json()
        print("\n✅ XỬ LÝ THÀNH CÔNG!")
        print("=" * 70)
        print(f"📌 TIÊU ĐỀ BÀI NÓI: {res.get('title')}")
        print(f"⏱️ THỜI LƯỢNG AUDIO: {res.get('duration_sec', 0.0):.1f} giây")
        print(f"📝 TỔNG SỐ ĐOẠN THOẠI: {res.get('transcript_count', 0)}")
        print("=" * 70)

        print("\n[1. TÓM TẮT NỘI DUNG CHÍNH]")
        print(f"   {res.get('summary')}")

        print("\n[2. CÁC Ý KIẾN THỨC CỐT LÕI]")
        key_points = res.get("key_points", [])
        if not key_points:
            print("   (Không có ý bổ sung hoặc chỉ nói chuyện thông thường)")
        for idx, kp in enumerate(key_points, 1):
            print(f"   {idx}. [{kp.get('timestamp_label', '00:00')}] {kp.get('headline')} ({kp.get('category')})")
            print(f"      👉 {kp.get('content')}")
            if kp.get("evidence_quote"):
                print(f"      🔊 Bằng chứng: \"{kp.get('evidence_quote')}\"")
            print()

        terms = res.get("technical_terms", [])
        if terms:
            print("[3. THUẬT NGỮ CHUYÊN NGÀNH]")
            for t in terms:
                print(f"   • {t.get('term')}: {t.get('explanation')}")
            print()

        actions = res.get("action_items_or_tips", [])
        if actions:
            print("[4. LƯU Ý / DẶN DÒ]")
            for a in actions:
                print(f"   ⚠️ {a}")
            print()

        # Lưu kết quả markdown
        md_content = res.get("markdown_notes", "")
        if md_content:
            out_md_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "VOICE_NOTES_TEST_RESULT.md")
            with open(out_md_path, "w", encoding="utf-8") as mf:
                mf.write(md_content)
            print(f"📄 Đã xuất bản ghi chép chi tiết dạng Markdown tại: {out_md_path}")

    except Exception as e:
        print(f"❌ Lỗi kết nối tới AI Service: {e}")

if __name__ == "__main__":
    path_arg = sys.argv[1] if len(sys.argv) > 1 else None
    test_voice_notes(path_arg)
