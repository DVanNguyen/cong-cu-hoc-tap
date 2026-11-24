"""Phiên âm 760s đầu của TSQL_Lecture09 bằng faster-whisper medium (GPU nếu có) để làm tập đánh giá căn slide."""
import json
import os
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")
from faster_whisper import WhisperModel

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WAV = os.path.join(ROOT, "dataset_eval", "my_test", "audio_16k.wav")
OUT = os.path.join(ROOT, "dataset_eval", "golden_real", "tsql_first760s_transcript.json")
LIMIT = 760.0

import soundfile as sf
data, sr = sf.read(WAV, dtype="float32")
if data.ndim > 1:
    data = data.mean(axis=1)
data = data[: int(LIMIT * sr)]

try:
    model = WhisperModel("medium", device="cpu", compute_type="int8")
    dev = "cuda"
except Exception as e:
    print("cuda failed:", e)
    model = WhisperModel("medium", device="cpu", compute_type="int8")
    dev = "cpu"

t0 = time.time()
segs, _ = model.transcribe(
    data, language="vi", vad_filter=True,
    vad_parameters={"threshold": 0.35, "min_silence_duration_ms": 700, "speech_pad_ms": 400},
    initial_prompt="Bài giảng T-SQL: BEGIN END, DECLARE, SET, SELECT, biến, kiểu dữ liệu, getdate, table variable.",
)
out = [{"start_sec": round(s.start, 2), "end_sec": round(s.end, 2), "text": s.text.strip()} for s in segs if s.text.strip()]
json.dump({"device": dev, "model": "medium", "seconds": round(time.time() - t0, 1), "segments": out},
          open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(dev, len(out), "segments in", round(time.time() - t0, 1), "s")

