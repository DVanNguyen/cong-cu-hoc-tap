import os
import json
import numpy as np
import soundfile as sf

def main():
    sr_target = 16000

    # 1. Load real teacher audio (giây 0 đến 160)
    teacher_audio, sr = sf.read("dataset_eval/my_test/audio_16k.wav", start=0, stop=160 * sr_target)
    if sr != sr_target:
        raise ValueError(f"Expected 16kHz, got {sr}")

    # 2. Load 3 different student voice clips (non-teacher)
    s1_full, _ = sf.read("dataset_eval/audio/lecture_01_agents.wav")
    s2_full, _ = sf.read("dataset_eval/audio/lecture_02_search.wav")
    s3_full, _ = sf.read("dataset_eval/audio/lecture_03_heuristic.wav")

    # Take 4-5 seconds of each student clip
    s1 = s1_full[int(1.0*sr):int(5.5*sr)]  # 4.5s
    s2 = s2_full[int(0.5*sr):int(5.0*sr)]  # 4.5s
    s3 = s3_full[int(1.0*sr):int(5.2*sr)]  # 4.2s

    # Normalize amplitude of student clips
    s1 = s1 / (np.max(np.abs(s1)) + 1e-6) * 0.25
    s2 = s2 / (np.max(np.abs(s2)) + 1e-6) * 0.25
    s3 = s3 / (np.max(np.abs(s3)) + 1e-6) * 0.25

    mixed = np.copy(teacher_audio)

    # Vị trí chèn 3 đoạn tiếng nói của sinh viên (STUDENT_NOISE):
    # Đoạn 1: giây 35.0 -> 39.5
    t1_start = 35.0
    idx1 = int(t1_start * sr)
    len1 = len(s1)
    t1_end = round(t1_start + len1 / sr, 2)
    mixed[idx1:idx1+len1] = s1

    # Đoạn 2: giây 75.0 -> 79.5
    t2_start = 75.0
    idx2 = int(t2_start * sr)
    len2 = len(s2)
    t2_end = round(t2_start + len2 / sr, 2)
    mixed[idx2:idx2+len2] = s2

    # Đoạn 3: giây 115.0 -> 119.2
    t3_start = 115.0
    idx3 = int(t3_start * sr)
    len3 = len(s3)
    t3_end = round(t3_start + len3 / sr, 2)
    mixed[idx3:idx3+len3] = s3

    # Thêm nhiễu nền phòng học (tiếng quạt / điều hòa nhẹ ~ -36 dB)
    classroom_hum = np.random.normal(0, 0.0015, len(mixed))
    mixed = mixed + classroom_hum

    out_wav = "dataset_eval/golden_real/tsql_mixed_classroom.wav"
    os.makedirs("dataset_eval/golden_real", exist_ok=True)
    sf.write(out_wav, mixed, sr)
    total_dur = round(len(mixed) / sr, 2)
    print(f"Created {out_wav} successfully! Duration: {total_dur}s")

    labels = {
        "audio_file": "tsql_mixed_classroom.wav",
        "enrollment_file": "tsql_teacher_enroll.wav",
        "total_duration_sec": total_dur,
        "intervals": [
            {
                "start_sec": 0.0,
                "end_sec": 34.8,
                "speaker": "TEACHER",
                "label": "TEACHER",
                "slide_page": 1,
                "description": "Giảng viên giới thiệu tổng quan T-SQL"
            },
            {
                "start_sec": t1_start,
                "end_sec": t1_end,
                "speaker": "STUDENT_NOISE",
                "label": "STUDENT_NOISE",
                "slide_page": 1,
                "description": "Sinh viên 1 nói chuyện chen ngang hỏi bài"
            },
            {
                "start_sec": round(t1_end + 0.2, 2),
                "end_sec": 74.8,
                "speaker": "TEACHER",
                "label": "TEACHER",
                "slide_page": 2,
                "description": "Giảng viên giảng về khai báo biến và cú pháp DECLARE"
            },
            {
                "start_sec": t2_start,
                "end_sec": t2_end,
                "speaker": "STUDENT_NOISE",
                "label": "STUDENT_NOISE",
                "slide_page": 2,
                "description": "Sinh viên 2 mượn đồ bàn tán xôn xao"
            },
            {
                "start_sec": round(t2_end + 0.2, 2),
                "end_sec": 114.8,
                "speaker": "TEACHER",
                "label": "TEACHER",
                "slide_page": 3,
                "description": "Giảng viên giải thích sự khác biệt giữa biến bảng và bảng tạm"
            },
            {
                "start_sec": t3_start,
                "end_sec": t3_end,
                "speaker": "STUDENT_NOISE",
                "label": "STUDENT_NOISE",
                "slide_page": 3,
                "description": "Sinh viên 3 hỏi đề thi cuối kỳ"
            },
            {
                "start_sec": round(t3_end + 0.2, 2),
                "end_sec": total_dur,
                "speaker": "TEACHER",
                "label": "TEACHER",
                "slide_page": 4,
                "description": "Giảng viên mở demo SQL Server Management Studio"
            }
        ]
    }

    labels_path = "dataset_eval/golden_real/tsql_mixed_classroom_labels.json"
    with open(labels_path, "w", encoding="utf-8") as f:
        json.dump(labels, f, ensure_ascii=False, indent=2)
    print(f"Saved {labels_path} successfully!")

if __name__ == "__main__":
    main()
