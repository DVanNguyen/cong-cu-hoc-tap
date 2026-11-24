"""
Đánh giá định lượng Tuần 5: Live Slide-Follow (Lọc giọng ECAPA-TDNN + Windowed BGE-M3 Live).
Chạy:  python evaluate_week5_live.py   (từ thư mục ai-service)
Xuất:  week5_evaluation_report.json
"""
import asyncio
import json
import os
import sys
import tempfile
import time
import warnings
from collections import Counter

warnings.filterwarnings("ignore")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ROOT = os.path.dirname(HERE)
GOLD = os.path.join(ROOT, "dataset_eval", "golden_real")

import numpy as np
import soundfile as sf

from app.api.ai_endpoints import match_live_utterance_semantic, process_live_chunk_core
from app.modules.lecture_notes.inline_block_aligner import (
    NonMonotonicSlideTracker,
    SlideBlock,
    split_slide_into_blocks,
)
from app.services.semantic_embedder import get_semantic_embedder
from app.services.slide_extractor import extract_slide_deck
from app.services.speaker_verification import get_voice_filter_service


def build_blocks_from_pdf(pdf_path: str):
    slides = extract_slide_deck(pdf_path)
    blocks = []
    for sl in slides:
        p_num = int(sl["slide_number"])
        blocks.extend(split_slide_into_blocks(p_num, sl.get("raw_text") or ""))
    return slides, blocks


def write_wav_slice(data: np.ndarray, sr: int, start_sec: float, end_sec: float) -> str:
    s0 = max(0, int(start_sec * sr))
    s1 = min(len(data), int(end_sec * sr))
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        path = f.name
    sf.write(path, data[s0:s1], sr, subtype="PCM_16")
    return path


def group_transcript_into_8s_chunks(segments, chunk_sec: float = 8.0):
    buckets = {}
    for seg in segments:
        txt = (seg.get("text") or "").strip()
        if not txt:
            continue
        b_idx = int(float(seg.get("start_sec", 0.0)) // chunk_sec)
        buckets.setdefault(b_idx, []).append(seg)
    chunks = []
    for b_idx in sorted(buckets.keys()):
        segs = buckets[b_idx]
        chunks.append({
            "chunk_idx": b_idx,
            "start_sec": round(b_idx * chunk_sec, 1),
            "end_sec": round((b_idx + 1) * chunk_sec, 1),
            "text": " ".join(s["text"].strip() for s in segs),
        })
    return chunks


async def main():
    voice_svc = get_voice_filter_service()
    embedder = get_semantic_embedder()

    tsql_pdf = os.path.join(ROOT, "dataset_eval/my_test/TSQL_Lecture09.pdf")
    cs188_pdf = os.path.join(ROOT, "dataset_eval/slides/cs188_sp24_lec01.pdf")
    enroll_wav = os.path.join(GOLD, "tsql_teacher_enroll.wav")
    mixed_wav = os.path.join(GOLD, "tsql_mixed_classroom.wav")
    labels_json = os.path.join(GOLD, "tsql_mixed_classroom_labels.json")

    _, tsql_blocks = build_blocks_from_pdf(tsql_pdf)
    _, cs188_blocks = build_blocks_from_pdf(cs188_pdf)

    # Warmup slide block cache trong SemanticEmbedderService (mô phỏng phiên Live đã khởi tạo slide)
    embedder.encode([b.text for b in tsql_blocks], is_query=False)
    tsql_page_map = {}
    for b in tsql_blocks:
        tsql_page_map.setdefault(b.slide_page, []).append(b.text)
    embedder.encode(["\n".join(tsql_page_map[p]) for p in sorted(tsql_page_map)], is_query=False)

    # =========================================================================
    # 1. LỌC GIỌNG LIVE (ECAPA-TDNN verify_short_chunk TRƯỚC WHISPER)
    # =========================================================================
    teacher_emb = voice_svc.enroll_teacher(enroll_wav)["embedding_vector"]
    audio_data, sr = sf.read(mixed_wav, dtype="float32")
    if audio_data.ndim > 1:
        audio_data = audio_data.mean(axis=1)
    labels = json.load(open(labels_json, encoding="utf-8"))["intervals"]

    # 1A. Các đoạn thuần giọng sinh viên nói chen (3 đoạn trong labels) -> test chặn trước Whisper qua process_live_chunk_core
    student_intervals = [iv for iv in labels if iv["speaker"] == "STUDENT_NOISE"]
    student_rejected = 0
    student_latencies_ms = []
    student_details = []
    for iv in student_intervals:
        path = write_wav_slice(audio_data, sr, iv["start_sec"], iv["end_sec"])
        try:
            t0 = time.perf_counter()
            res = await process_live_chunk_core(
                audio_path=path,
                blocks=tsql_blocks,
                current_slide_page=iv["slide_page"],
                language="vi",
                chunk_start_sec=iv["start_sec"],
                teacher_embedding=teacher_emb,
                voice_threshold=0.35,
                resolve_highlights=False,
            )
            dt_ms = (time.perf_counter() - t0) * 1000.0
            student_latencies_ms.append(dt_ms)
            rejected = res.get("action") == "ignored_non_teacher"
            student_rejected += int(rejected)
            student_details.append({
                "interval": f"{iv['start_sec']}-{iv['end_sec']}s",
                "action": res.get("action"),
                "similarity": (res.get("voice_verification") or {}).get("similarity"),
                "latency_ms": round(dt_ms, 1),
            })
        finally:
            os.remove(path)

    # 1B. Cắt liên tiếp toàn bộ 160s thành 20 chunk 8 giây để đo verify_short_chunk latency & teacher acceptance
    # Lưu ý: 0:00-0:16 của TSQL_Lecture09 là nhạc mở đầu/im lặng trước khi thầy cất tiếng ở 0:16
    pure_teacher_total = 0
    pure_teacher_kept = 0
    active_teacher_total = 0
    active_teacher_kept = 0
    chunk_verify_ms = []
    stream_20_summary = []
    for idx in range(20):
        c0, c1 = idx * 8.0, (idx + 1) * 8.0
        has_noise = any(min(c1, iv["end_sec"]) - max(c0, iv["start_sec"]) > 0.2 for iv in student_intervals)
        path = write_wav_slice(audio_data, sr, c0, c1)
        try:
            t0 = time.perf_counter()
            vr = voice_svc.verify_short_chunk(path, teacher_emb, threshold=0.35)
            dt_ms = (time.perf_counter() - t0) * 1000.0
            chunk_verify_ms.append(dt_ms)
            if not has_noise:
                pure_teacher_total += 1
                pure_teacher_kept += int(vr["is_teacher"])
                if c0 >= 16.0:
                    active_teacher_total += 1
                    active_teacher_kept += int(vr["is_teacher"])
            stream_20_summary.append({
                "chunk": f"{int(c0):03d}-{int(c1):03d}s",
                "has_student_noise": has_noise,
                "is_teacher": vr["is_teacher"],
                "similarity": vr["similarity"],
                "verify_ms": round(dt_ms, 1),
            })
        finally:
            os.remove(path)

    student_rej_pct = round(100.0 * student_rejected / max(len(student_intervals), 1), 2)
    teacher_keep_pct = round(100.0 * pure_teacher_kept / max(pure_teacher_total, 1), 2)
    active_teacher_keep_pct = round(100.0 * active_teacher_kept / max(active_teacher_total, 1), 2)
    avg_verify_ms = round(float(np.mean(chunk_verify_ms)), 1)

    # =========================================================================
    # 2. BÁM SLIDE TUẦN TỰ TRÊN LUỒNG CHUNK 8s (TSQL 760s)
    # =========================================================================
    tr760 = json.load(open(os.path.join(GOLD, "tsql_first760s_transcript.json"), encoding="utf-8"))["segments"]
    live_chunks = group_transcript_into_8s_chunks(tr760, chunk_sec=8.0)

    # So sánh: Windowed BGE-M3 Live (truyền current_slide_page nối tiếp) vs NonMonotonicSlideTracker cũ
    curr_page_new = 1
    pages_new = []
    match_latencies_ms = []
    for ch in live_chunks:
        t0 = time.perf_counter()
        ev = match_live_utterance_semantic(
            transcript_text=ch["text"],
            blocks=tsql_blocks,
            current_slide_page=curr_page_new,
            start_sec=ch["start_sec"],
            end_sec=ch["end_sec"],
        )
        match_latencies_ms.append((time.perf_counter() - t0) * 1000.0)
        if ev.matched_slide_page is not None:
            curr_page_new = ev.matched_slide_page
        pages_new.append(curr_page_new)

    tracker_old = NonMonotonicSlideTracker(tsql_blocks)
    pages_old = []
    for ch in live_chunks:
        ev_old = tracker_old.process_utterance(ch["text"], ch["start_sec"], ch["end_sec"])
        pages_old.append(ev_old.matched_slide_page or tracker_old.current_slide_page)

    n_live = len(pages_new)
    new_in_1_9 = round(100.0 * sum(1 for p in pages_new if 1 <= p <= 9) / n_live, 2)
    new_slide2 = round(100.0 * sum(1 for p in pages_new if p == 2) / n_live, 2)
    new_drift_15_21 = sum(1 for p in pages_new if 15 <= p <= 21)

    old_in_1_9 = round(100.0 * sum(1 for p in pages_old if 1 <= p <= 9) / n_live, 2)
    old_drift_15_21 = sum(1 for p in pages_old if 15 <= p <= 21)
    avg_match_ms = round(float(np.mean(match_latencies_ms)), 1)

    # =========================================================================
    # 3. BÁM SLIDE SONG NGỮ Ở CHẾ ĐỘ LIVE (5 CHUNK TIẾNG VIỆT -> SLIDE ANH CS188)
    # =========================================================================
    bil_all = json.load(open(os.path.join(GOLD, "bilingual_cs188_eval.json"), encoding="utf-8"))["items"]
    # Chọn 5 chunk tiến dần tự nhiên từ tập CS188: slide 8 -> 9 -> 12 -> 14 -> 16
    target_pages = [8, 9, 12, 14, 16]
    bil_5 = [next(x for x in bil_all if x["gold_slide"] == tp) for tp in target_pages]

    # Warmup cache slide CS188
    embedder.encode([b.text for b in cs188_blocks], is_query=False)
    cs_page_map = {}
    for b in cs188_blocks:
        cs_page_map.setdefault(b.slide_page, []).append(b.text)
    embedder.encode(["\n".join(cs_page_map[p]) for p in sorted(cs_page_map)], is_query=False)

    curr_cs = 8
    bil_hits = 0
    bil_details = []
    for i, item in enumerate(bil_5):
        ev = match_live_utterance_semantic(
            transcript_text=item["text"],
            blocks=cs188_blocks,
            current_slide_page=curr_cs,
            start_sec=float(i * 10 + 10),
            end_sec=float(i * 10 + 18),
        )
        pred = ev.matched_slide_page
        if pred is not None:
            curr_cs = pred
        hit = pred == item["gold_slide"]
        bil_hits += int(hit)
        bil_details.append({
            "gold_slide": item["gold_slide"],
            "predicted_slide": pred,
            "matched_block_id": ev.matched_block_id,
            "hit": hit,
            "text": item["text"][:70],
        })
    bil_acc_pct = round(100.0 * bil_hits / len(bil_5), 2)

    checks = {
        "1_dynamic_current_slide_progression": len(set(pages_new)) >= 4 and pages_new[-1] > pages_new[0] and new_drift_15_21 == 0,
        "2_student_chatter_rejected_100pct_before_whisper": student_rej_pct == 100.0,
        "3_bilingual_live_5_chunks_ge_80pct": bil_acc_pct >= 80.0,
        "4_live_slide_tracking_ge_85pct_and_zero_drift_15_21": new_in_1_9 >= 85.0 and new_drift_15_21 == 0,
        "5_active_teacher_chunks_preserved_ge_90pct": active_teacher_keep_pct >= 90.0,
    }

    report = {
        "status": "PASSED" if all(checks.values()) else "NEEDS_WORK",
        "evaluation_name": "StudyAI Week 5 Live Slide-Follow & Real-Time Voice Filter Benchmark",
        "metrics": {
            "live_voice_filter": {
                "student_chatter_rejection_pct": student_rej_pct,
                "student_rejected_count": f"{student_rejected}/{len(student_intervals)}",
                "active_teacher_chunk_keep_pct": active_teacher_keep_pct,
                "active_teacher_kept_count": f"{active_teacher_kept}/{active_teacher_total}",
                "all_non_noise_chunks_keep_pct_incl_intro": teacher_keep_pct,
                "intro_note": "0:00-0:16 là nhạc/im lặng mở đầu bài giảng trước khi giảng viên bắt đầu nói ở 0:16.",
                "avg_verify_short_chunk_ms": avg_verify_ms,
                "avg_student_reject_total_ms": round(float(np.mean(student_latencies_ms)), 1),
                "student_details": student_details,
                "stream_20_chunks": stream_20_summary,
            },
            "live_slide_tracking_tsql_8s_chunks": {
                "total_8s_chunks": n_live,
                "windowed_bge_m3_live": {
                    "in_slide_1_9_pct": new_in_1_9,
                    "slide_2_pct": new_slide2,
                    "drift_to_slide_15_21": new_drift_15_21,
                    "avg_semantic_match_ms_per_chunk": avg_match_ms,
                    "distribution": {str(k): v for k, v in sorted(Counter(pages_new).items())},
                },
                "legacy_rapidfuzz_tracker": {
                    "in_slide_1_9_pct": old_in_1_9,
                    "drift_to_slide_15_21": old_drift_15_21,
                    "distribution": {str(k): v for k, v in sorted(Counter(pages_old).items())},
                },
            },
            "bilingual_live_cs188_5_chunks": {
                "accuracy_pct": bil_acc_pct,
                "hits": f"{bil_hits}/{len(bil_5)}",
                "details": bil_details,
            },
        },
        "exit_criteria_checklist": checks,
    }

    out_path = os.path.join(HERE, "week5_evaluation_report.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(json.dumps({"status": report["status"], "exit_criteria_checklist": checks}, ensure_ascii=False, indent=2))
    print("Voice filter:", {k: v for k, v in report["metrics"]["live_voice_filter"].items() if k not in ("student_details", "stream_20_chunks")})
    print("Live TSQL tracking NEW:", report["metrics"]["live_slide_tracking_tsql_8s_chunks"]["windowed_bge_m3_live"])
    print("Live TSQL tracking OLD:", report["metrics"]["live_slide_tracking_tsql_8s_chunks"]["legacy_rapidfuzz_tracker"])
    print("Bilingual Live 5 chunks:", report["metrics"]["bilingual_live_cs188_5_chunks"])


if __name__ == "__main__":
    asyncio.run(main())
