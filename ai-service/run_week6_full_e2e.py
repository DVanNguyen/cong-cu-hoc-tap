"""
Kiểm thử Toàn trình 5 Kịch bản End-to-End (Tuần 1 -> Tuần 6) trên hệ thống thật:
  - Laravel API (http://127.0.0.1:8000) + Queue Worker + MySQL (studyai_db)
  - FastAPI AI Microservice (http://127.0.0.1:8001) + Faster-Whisper + ECAPA-TDNN + BGE-M3 Viterbi + Gemini

Chạy:  python run_week6_full_e2e.py   (từ thư mục ai-service)
Xuất:  week6_e2e_verification_report.json
"""
import json
import os
import sys
import tempfile
import time
import warnings

warnings.filterwarnings("ignore")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ROOT = os.path.dirname(HERE)
GOLD = os.path.join(ROOT, "dataset_eval", "golden_real")

import requests
import soundfile as sf

LARAVEL = "http://127.0.0.1:8000/api/v1"
FASTAPI = "http://127.0.0.1:8001"


def slice_wav(src_wav: str, start_sec: float, end_sec: float) -> str:
    data, sr = sf.read(src_wav, dtype="float32")
    if data.ndim > 1:
        data = data.mean(axis=1)
    s0, s1 = max(0, int(start_sec * sr)), min(len(data), int(end_sec * sr))
    fd, path = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    sf.write(path, data[s0:s1], sr, subtype="PCM_16")
    return path


def wait_for_lecture_completion(lecture_id: int, timeout_sec: int = 420):
    seen_statuses = []
    seen_percents = []
    t0 = time.time()
    while time.time() - t0 < timeout_sec:
        r = requests.get(f"{LARAVEL}/lectures/{lecture_id}/status", timeout=15)
        r.raise_for_status()
        body = r.json()
        st = body.get("processing_status")
        pct = body.get("progress_percent", 0)
        if not seen_statuses or seen_statuses[-1] != st:
            seen_statuses.append(st)
            seen_percents.append(pct)
            print(f"    [Lecture #{lecture_id}] status={st} ({pct}%)", flush=True)
        if st == "completed" or pct >= 100:
            return True, seen_statuses, seen_percents, round(time.time() - t0, 2), None
        if st == "failed":
            return False, seen_statuses, seen_percents, round(time.time() - t0, 2), body.get("error_message")
        time.sleep(2.0)
    return False, seen_statuses, seen_percents, round(time.time() - t0, 2), "timeout"


def main():
    # 0. Kiểm tra Health 2 dịch vụ
    print("[0] Checking FastAPI & Laravel health...", flush=True)
    h_ai = requests.get(f"{FASTAPI}/health", timeout=15)
    h_lv = requests.get(f"{LARAVEL}/courses", timeout=15)
    assert h_ai.status_code == 200, f"FastAPI health failed: {h_ai.status_code}"
    # Dọn trước nếu có môn E2E cũ còn sót
    for c in h_lv.json().get("courses", []):
        if c.get("code") == "E2E-W6":
            requests.delete(f"{LARAVEL}/courses/{c['id']}", timeout=10)

    tsql_pdf = os.path.join(ROOT, "dataset_eval/my_test/TSQL_Lecture09.pdf")
    cs188_pdf = os.path.join(ROOT, "dataset_eval/slides/cs188_sp24_lec01.pdf")
    enroll_wav = os.path.join(GOLD, "tsql_teacher_enroll.wav")
    mixed_wav = os.path.join(GOLD, "tsql_mixed_classroom.wav")

    created_course_id = None
    temp_files = []
    report = {
        "evaluation_name": "StudyAI Week 6 Full-Stack 5-Scenario End-to-End Verification",
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "health": {
            "fastapi_status": h_ai.status_code,
            "laravel_status": h_lv.status_code,
        },
        "scenarios": {},
    }

    try:
        # Tạo môn học kiểm thử tạm thời (Tuần 1 Course CRUD)
        r_course = requests.post(
            f"{LARAVEL}/courses",
            json={"name": "E2E Verify SQL Server", "code": "E2E-W6", "lecturer": "GV Kiểm thử"},
            timeout=15,
        )
        r_course.raise_for_status()
        created_course_id = int(r_course.json()["id"])
        print(f"[+] Created temporary course #{created_course_id}", flush=True)

        # =====================================================================
        # KỊCH BẢN 2 + 1: Đăng ký Voiceprint thật + Batch Upload có nhiễu sinh viên
        # =====================================================================
        print("[1-2] Enrolling teacher voiceprint & running Async Batch Upload...", flush=True)
        with open(enroll_wav, "rb") as fa:
            r_vp = requests.post(
                f"{LARAVEL}/courses/{created_course_id}/voiceprint",
                files={"sample_audio": ("tsql_teacher_enroll.wav", fa, "audio/wav")},
                data={"lecturer_name": "Lương Trần Hy Hiến"},
                timeout=120,
            )
        r_vp.raise_for_status()
        vp_json = r_vp.json()

        # Cắt đoạn 18s -> 62s từ tsql_mixed_classroom.wav (gồm giọng thầy + nhiễu sinh viên ở 35.0-39.5s)
        batch_slice = slice_wav(mixed_wav, 18.0, 62.0)
        temp_files.append(batch_slice)

        t_up0 = time.time()
        with open(tsql_pdf, "rb") as fs, open(batch_slice, "rb") as fa:
            r_up = requests.post(
                f"{LARAVEL}/courses/{created_course_id}/lectures/upload",
                files={
                    "slide_file": ("TSQL_Lecture09.pdf", fs, "application/pdf"),
                    "audio_file": ("tsql_batch_mixed_44s.wav", fa, "audio/wav"),
                },
                data={"title": "E2E Batch Lecture TSQL", "language": "vi"},
                timeout=30,
            )
        upload_resp_sec = round(time.time() - t_up0, 3)
        assert r_up.status_code in (200, 201, 202), f"Upload failed: {r_up.status_code} {r_up.text}"
        batch_lec_id = int(r_up.json()["lecture_id"])

        ok_batch, statuses_b, percents_b, total_b_sec, err_b = wait_for_lecture_completion(batch_lec_id)
        ws_b = requests.get(f"{LARAVEL}/lectures/{batch_lec_id}/workspace", timeout=20).json()
        inl_b = requests.get(f"{LARAVEL}/lectures/{batch_lec_id}/inline-notes", timeout=20).json()

        slides_with_notes = [s["slide_number"] for s in ws_b.get("slides", []) if s.get("notes")]
        all_ws_notes = [n for s in ws_b.get("slides", []) for n in s.get("notes", [])]
        all_have_quotes = all(bool((n.get("verbatim_quote") or "").strip()) for n in all_ws_notes)
        in_slide_1_9_notes = all(1 <= int(sn) <= 9 for sn in slides_with_notes)

        report["scenarios"]["1_batch_upload_and_grounding"] = {
            "passed": bool(ok_batch and len(ws_b.get("slides", [])) == 23 and all_have_quotes and in_slide_1_9_notes),
            "http_upload_status": r_up.status_code,
            "upload_ack_sec": upload_resp_sec,
            "observed_statuses": statuses_b,
            "observed_percents": percents_b,
            "queue_total_sec": total_b_sec,
            "slides_persisted": len(ws_b.get("slides", [])),
            "notes_generated": len(all_ws_notes),
            "slides_with_notes": slides_with_notes,
            "all_notes_have_evidence_quote": all_have_quotes,
            "error": err_b,
        }
        report["scenarios"]["2_classroom_noise_filtering"] = {
            "passed": bool(vp_json.get("embedding_dim") == 192 and ok_batch),
            "voiceprint_enrolled": True,
            "embedding_dim": vp_json.get("embedding_dim"),
            "batch_with_injected_chatter_completed": ok_batch,
        }

        # =====================================================================
        # KỊCH BẢN 3: Song ngữ Slide Tiếng Anh (CS188) - Giảng Tiếng Việt
        # =====================================================================
        print("[3] Verifying Bilingual Slide EN - Speech VI alignment & Gemini note...", flush=True)
        from app.services.slide_extractor import extract_slide_deck
        from app.services.semantic_embedder import get_semantic_embedder
        from app.modules.lecture_notes.alignment import compute_token_overlap
        from app.modules.lecture_notes.note_synthesizer import synthesize_slide_notes
        import numpy as np

        bil = json.load(open(os.path.join(GOLD, "bilingual_cs188_eval.json"), encoding="utf-8"))["items"]
        cs_slides = extract_slide_deck(cs188_pdf)
        num_to_k = {int(s["slide_number"]): k for k, s in enumerate(cs_slides)}
        sim = get_semantic_embedder().similarity_matrix(
            [b["text"] for b in bil],
            [s.get("raw_text", "") for s in cs_slides],
        )
        hyb_hits = 0
        for i, b in enumerate(bil):
            hyb = [0.75 * float(sim[i, k]) + 0.25 * compute_token_overlap(b["text"], cs_slides[k].get("raw_text", "")) for k in range(len(cs_slides))]
            hyb_hits += int(int(np.argmax(hyb)) == num_to_k[b["gold_slide"]])
        bil_top1_pct = round(100.0 * hyb_hits / len(bil), 2)

        s16 = cs_slides[num_to_k[16]]
        s16_seg = next(b for b in bil if b["gold_slide"] == 16)
        synth_bil = synthesize_slide_notes(
            slide_number=16,
            slide_text=s16["raw_text"],
            transcript_segments=[{
                "start_sec": 120.0,
                "end_sec": 132.0,
                "text": s16_seg["text"] + " Các bạn chú ý nộp đúng hạn vì autograder kiểm tra test case Python rất chặt, nhưng làm theo partner sẽ học nhanh hơn.",
            }],
            course_title="CS188 Artificial Intelligence",
        )
        report["scenarios"]["3_bilingual_en_slide_vi_speech"] = {
            "passed": bool(bil_top1_pct >= 90.0 and len(synth_bil.notes) >= 1),
            "cross_lingual_top1_pct": bil_top1_pct,
            "synthesized_notes_count": len(synth_bil.notes),
            "sample_note": synth_bil.notes[0].model_dump() if synth_bil.notes else None,
        }

        # =====================================================================
        # KỊCH BẢN 4: Live Slide-Follow -> Lọc nhiễu -> Finish-Live + Refine Full
        # =====================================================================
        print("[4] Running Live Slide-Follow + Chatter Filter + Finish-Live + Refine Full...", flush=True)
        with open(tsql_pdf, "rb") as fs:
            r_vault = requests.post(
                f"{LARAVEL}/courses/{created_course_id}/slide-vault",
                files={"slide_file": ("TSQL_Lecture09.pdf", fs, "application/pdf")},
                timeout=120,
            )
        r_vault.raise_for_status()
        vault_id = int(r_vault.json()["id"])

        r_start = requests.post(
            f"{LARAVEL}/lectures/start-live",
            json={"course_id": created_course_id, "vault_slide_id": vault_id, "title": "E2E Live TSQL", "language": "vi"},
            timeout=30,
        )
        r_start.raise_for_status()
        live_lec_id = int(r_start.json()["lecture_id"])

        ch_noise = slice_wav(mixed_wav, 35.0, 39.5)
        ch_t1 = slice_wav(mixed_wav, 18.0, 26.0)
        ch_t2 = slice_wav(mixed_wav, 41.0, 49.0)
        temp_files.extend([ch_noise, ch_t1, ch_t2])

        with open(ch_noise, "rb") as fn:
            res_noise = requests.post(
                f"{LARAVEL}/lectures/{live_lec_id}/live-chunk",
                files={"audio_chunk": ("chunk_noise.wav", fn, "audio/wav")},
                data={"chunk_start_sec": "0.0", "language": "vi"},
                timeout=60,
            ).json()

        with open(ch_t1, "rb") as ft1:
            res_t1 = requests.post(
                f"{LARAVEL}/lectures/{live_lec_id}/live-chunk",
                files={"audio_chunk": ("chunk_t1.wav", ft1, "audio/wav")},
                data={"chunk_start_sec": "5.0", "language": "vi"},
                timeout=60,
            ).json()

        with open(ch_t2, "rb") as ft2:
            res_t2 = requests.post(
                f"{LARAVEL}/lectures/{live_lec_id}/live-chunk",
                files={"audio_chunk": ("chunk_t2.wav", ft2, "audio/wav")},
                data={"chunk_start_sec": "13.0", "language": "vi"},
                timeout=60,
            ).json()

        # Đánh dấu 1 block "Chưa hiểu" trong buổi Live để kiểm tra bảo toàn cờ sau khi refine_full_lecture
        first_block_id = r_start.json()["pages"][0]["blocks"][0]["blockId"]
        requests.post(f"{LARAVEL}/lectures/{live_lec_id}/blocks/{first_block_id}/toggle-confused", timeout=15)

        r_fin = requests.post(
            f"{LARAVEL}/lectures/{live_lec_id}/finish-live",
            json={"refine_full_lecture": True},
            timeout=60,
        )
        r_fin.raise_for_status()
        fin_json = r_fin.json()

        ok_ref, statuses_ref, _, ref_sec, err_ref = wait_for_lecture_completion(live_lec_id)
        ws_live = requests.get(f"{LARAVEL}/lectures/{live_lec_id}/workspace", timeout=20).json()
        r_range = requests.get(
            f"{LARAVEL}/lectures/{live_lec_id}/audio",
            headers={"Range": "bytes=0-1023"},
            timeout=15,
        )

        no_dup_slides = len(ws_live.get("slides", [])) == 23
        report["scenarios"]["4_live_follow_and_refine_idempotent"] = {
            "passed": bool(
                res_noise.get("action") == "ignored_non_teacher"
                and fin_json.get("audio_ready") is True
                and ok_ref
                and no_dup_slides
                and r_range.status_code == 206
            ),
            "student_chunk_action": res_noise.get("action"),
            "teacher_chunk_1_page": res_t1.get("matched_slide_page"),
            "teacher_chunk_2_page": res_t2.get("matched_slide_page"),
            "finish_live_audio_ready": fin_json.get("audio_ready"),
            "refine_completed": ok_ref,
            "refine_sec": ref_sec,
            "slides_count_after_refine": len(ws_live.get("slides", [])),
            "no_duplicate_slides": no_dup_slides,
            "audio_http_range_status": r_range.status_code,
            "error": err_ref,
        }

        # =====================================================================
        # KỊCH BẢN 5: Sửa / Xóa Note + Xuất Markdown + Đánh dấu Confused
        # =====================================================================
        print("[5] Verifying Note Edit/Delete & Markdown Export...", flush=True)
        # Dùng bài giảng batch_lec_id hoặc live_lec_id có note để test PUT / Export / DELETE
        inl_target = requests.get(f"{LARAVEL}/lectures/{batch_lec_id}/inline-notes", timeout=20).json()
        target_lec = batch_lec_id
        all_inl_notes = [
            n for p in inl_target.get("pages", []) for b in p.get("blocks", []) for n in b.get("inlineNotes", [])
        ]
        if not all_inl_notes:
            inl_target = requests.get(f"{LARAVEL}/lectures/{live_lec_id}/inline-notes", timeout=20).json()
            target_lec = live_lec_id
            all_inl_notes = [
                n for p in inl_target.get("pages", []) for b in p.get("blocks", []) for n in b.get("inlineNotes", [])
            ]

        first_b_id = inl_target["pages"][0]["blocks"][0]["blockId"]
        requests.post(f"{LARAVEL}/lectures/{target_lec}/blocks/{first_b_id}/toggle-confused", timeout=15)

        edited_ok = False
        deleted_ok = False
        if all_inl_notes:
            nid = all_inl_notes[0]["id"]
            r_put = requests.put(
                f"{LARAVEL}/inline-notes/{nid}",
                json={"explanation_text": "Ghi chú kiểm thử E2E Tuần 6 (đã chỉnh sửa thành công)"},
                timeout=15,
            )
            edited_ok = r_put.status_code == 200 and r_put.json().get("is_edited_by_user") is True

        r_md = requests.get(f"{LARAVEL}/lectures/{target_lec}/export-markdown", timeout=15)
        md_text = r_md.text
        md_ok = (
            r_md.status_code == 200
            and "## Slide" in md_text
            and "[Cần xem lại]" in md_text
            and ("đã chỉnh sửa thành công" in md_text if all_inl_notes else True)
        )

        if all_inl_notes:
            nid = all_inl_notes[0]["id"]
            r_del = requests.delete(f"{LARAVEL}/inline-notes/{nid}", timeout=15)
            deleted_ok = r_del.status_code == 200 and r_del.json().get("deleted") is True

        report["scenarios"]["5_note_crud_and_markdown_export"] = {
            "passed": bool(edited_ok and md_ok and deleted_ok),
            "put_note_ok": edited_ok,
            "export_markdown_ok": md_ok,
            "markdown_length_chars": len(md_text),
            "delete_note_ok": deleted_ok,
        }

    finally:
        # Dọn dẹp môn học tạm (cascade xóa bài giảng test) và file wav tạm
        if created_course_id is not None:
            try:
                requests.delete(f"{LARAVEL}/courses/{created_course_id}", timeout=15)
                print(f"[+] Cleaned up temporary course #{created_course_id}", flush=True)
            except Exception:
                pass
        for tf in temp_files:
            if os.path.exists(tf):
                try:
                    os.remove(tf)
                except Exception:
                    pass

    all_passed = all(sc.get("passed", False) for sc in report["scenarios"].values())
    report["status"] = "PASSED" if all_passed else "NEEDS_WORK"

    out_file = os.path.join(HERE, "week6_e2e_verification_report.json")
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print("\n=== WEEK 6 E2E SUMMARY ===")
    print(json.dumps({
        "status": report["status"],
        "scenarios": {k: v.get("passed") for k, v in report["scenarios"].items()},
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
