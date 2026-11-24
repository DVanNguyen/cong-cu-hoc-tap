import os
import tempfile
import subprocess
import logging
import shutil
from typing import Dict, List, Optional, Tuple, Any
import numpy as np
import torch
import torchaudio

logger = logging.getLogger("speaker_verification")


def get_ffmpeg_binary() -> str:
    """Tìm đường dẫn binary ffmpeg: ưu tiên PATH hệ thống, sau đó imageio_ffmpeg."""
    sys_ffmpeg = shutil.which("ffmpeg")
    if sys_ffmpeg:
        return sys_ffmpeg
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


def convert_to_16k_mono_wav(input_path: str) -> str:
    """Chuẩn hóa mọi định dạng (.m4a, .mp3, .mp4, .wav) về WAV 16kHz mono PCM16 cho ECAPA & pyannote."""
    tmp = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    tmp.close()
    ffmpeg_bin = get_ffmpeg_binary()
    cmd = [
        ffmpeg_bin, "-y", "-i", input_path,
        "-ac", "1", "-ar", "16000", "-sample_fmt", "s16",
        tmp.name
    ]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    return tmp.name


def load_audio_tensor(wav_path: str) -> Tuple[torch.Tensor, int]:
    """Tải waveform thành torch.Tensor 2D (channels, time) an toàn trên Windows bằng soundfile."""
    try:
        import soundfile as sf
        data, sr = sf.read(wav_path, dtype="float32")
        if data.ndim == 1:
            tensor = torch.from_numpy(data).unsqueeze(0)
        else:
            tensor = torch.from_numpy(data.T)
        return tensor, int(sr)
    except Exception:
        import torchaudio
        return torchaudio.load(wav_path)


class TeacherVoiceFilterService:
    """
    Singleton service kết hợp:
    - SpeechBrain ECAPA-TDNN (192-d embedding) cho Speaker Verification
    - pyannote/speaker-diarization-3.1 cho Speaker Diarization
    - Fallback: Direct Segment Verification bằng ECAPA-TDNN khi chưa nạp pyannote token
    """
    def __init__(self, device: Optional[str] = None):
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        self._ecapa = None
        self._diarizer = None

    def _load_ecapa(self):
        if self._ecapa is None:
            from speechbrain.inference.speaker import EncoderClassifier
            from speechbrain.utils.fetching import LocalStrategy
            models_dir = os.path.join(
                os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                "pretrained_models", "ecapa_voxceleb"
            )
            os.makedirs(models_dir, exist_ok=True)
            self._ecapa = EncoderClassifier.from_hparams(
                source="speechbrain/spkrec-ecapa-voxceleb",
                savedir=models_dir,
                run_opts={"device": self.device},
                local_strategy=LocalStrategy.COPY,
            )
            logger.info(f"Loaded ECAPA-TDNN on {self.device}")
        return self._ecapa

    def _load_diarizer(self):
        if self._diarizer is None:
            from pyannote.audio import Pipeline
            hf_token = os.getenv("HF_TOKEN", "")
            if not hf_token:
                try:
                    from dotenv import load_dotenv
                    load_dotenv()
                    hf_token = os.getenv("HF_TOKEN", "")
                except Exception:
                    pass
            if not hf_token:
                try:
                    from huggingface_hub import get_token
                    hf_token = get_token() or ""
                except Exception:
                    pass
            if not hf_token:
                raise ValueError("Thiếu HF_TOKEN trong .env để tải pyannote/speaker-diarization-3.1")
            
            try:
                from huggingface_hub import login
                login(token=hf_token, add_to_git_credential=False)
            except Exception as e:
                logger.warning(f"huggingface_hub login warning: {e}")

            try:
                self._diarizer = Pipeline.from_pretrained(
                    "pyannote/speaker-diarization-3.1",
                    token=hf_token,
                )
            except TypeError:
                self._diarizer = Pipeline.from_pretrained(
                    "pyannote/speaker-diarization-3.1",
                    use_auth_token=hf_token,
                )
            self._diarizer.to(torch.device(self.device))
            logger.info(f"Loaded pyannote speaker-diarization-3.1 on {self.device}")
        return self._diarizer

    @staticmethod
    def _l2_normalize(vec: np.ndarray) -> np.ndarray:
        norm = float(np.linalg.norm(vec))
        return vec / norm if norm > 1e-12 else vec

    @staticmethod
    def cosine_similarity(v1: np.ndarray, v2: np.ndarray) -> float:
        return float(np.dot(
            TeacherVoiceFilterService._l2_normalize(v1),
            TeacherVoiceFilterService._l2_normalize(v2),
        ))

    def extract_embedding_from_tensor(self, waveform: torch.Tensor) -> np.ndarray:
        ecapa = self._load_ecapa()
        if waveform.dim() == 1:
            waveform = waveform.unsqueeze(0)
        with torch.no_grad():
            emb = ecapa.encode_batch(waveform.to(self.device))
        vec = emb.squeeze().cpu().numpy().astype(np.float32)
        return self._l2_normalize(vec)

    def enroll_teacher(self, audio_path: str) -> Dict[str, Any]:
        """Trích xuất vector 192 chiều chuẩn hóa L2 từ file mẫu 15-30s giọng giảng viên."""
        wav_path = convert_to_16k_mono_wav(audio_path)
        try:
            waveform, sr = load_audio_tensor(wav_path)
            duration_sec = waveform.shape[1] / float(sr)
            if duration_sec < 5.0:
                raise ValueError("Mẫu giọng giảng viên quá ngắn (< 5 giây). Cần ít nhất 15-30 giây.")
            vec = self.extract_embedding_from_tensor(waveform)
            return {
                "embedding_dim": len(vec),
                "duration_sec": round(duration_sec, 2),
                "embedding_vector": vec.tolist(),
            }
        finally:
            if os.path.exists(wav_path):
                try:
                    os.remove(wav_path)
                except Exception:
                    pass

    def verify_short_chunk(
        self,
        audio_path: str,
        teacher_embedding: List[float],
        threshold: float = 0.35,
    ) -> Dict[str, Any]:
        """
        Xác thực nhanh 1 chunk ngắn (5-10s) cho Live Slide-Follow bằng ECAPA-TDNN (~25ms).
        Dùng ngưỡng 0.35 (nhẹ hơn 0.38 một chút để tránh cắt nhầm câu chuyển ý ngắn).
        """
        wav_path = None
        should_remove = False
        try:
            # Nếu file đầu vào đã là WAV 16kHz mono thì đọc trực tiếp không cần gọi ffmpeg
            if audio_path.lower().endswith(".wav"):
                try:
                    import soundfile as sf
                    info = sf.info(audio_path)
                    if info.samplerate == 16000 and info.channels == 1:
                        wav_path = audio_path
                except Exception:
                    wav_path = None

            if wav_path is None:
                wav_path = convert_to_16k_mono_wav(audio_path)
                should_remove = True

            waveform, sr = load_audio_tensor(wav_path)
            duration_sec = waveform.shape[1] / float(sr)
            if duration_sec < 1.0:
                return {"is_teacher": False, "similarity": 0.0, "duration_sec": round(duration_sec, 2), "reason": "too_short"}

            rms = float(torch.sqrt(torch.mean(waveform ** 2)).item())
            if rms < 1e-4:
                return {"is_teacher": False, "similarity": 0.0, "duration_sec": round(duration_sec, 2), "reason": "silence"}

            vec = self.extract_embedding_from_tensor(waveform)
            ref = self._l2_normalize(np.array(teacher_embedding, dtype=np.float32))
            sim = self.cosine_similarity(vec, ref)
            return {
                "is_teacher": bool(sim >= threshold),
                "similarity": round(sim, 4),
                "duration_sec": round(duration_sec, 2),
                "reason": "verified_teacher" if sim >= threshold else "non_teacher_chatter",
            }
        finally:
            if should_remove and wav_path and os.path.exists(wav_path):
                try:
                    os.remove(wav_path)
                except Exception:
                    pass

    def detect_teacher_intervals(
        self,
        audio_path: str,
        teacher_embedding: List[float],
        threshold: float = 0.38,
    ) -> Dict[str, Any]:
        """
        Chạy diarization toàn bộ file bài giảng và xác thực cụm nào là giảng viên.
        Trả về danh sách khoảng thời gian TEACHER và điểm similarity từng speaker.
        Có cơ chế fallback sang Segment-Level ECAPA Verification nếu pyannote chưa sẵn sàng.
        """
        ref_vec = self._l2_normalize(np.array(teacher_embedding, dtype=np.float32))
        wav_path = convert_to_16k_mono_wav(audio_path)
        try:
            waveform, sr = load_audio_tensor(wav_path)
            try:
                diarizer = self._load_diarizer()
                diarization = diarizer(wav_path)

                # 1. Gom các khoảng thời gian theo từng speaker label
                speaker_turns: Dict[str, List[Tuple[float, float]]] = {}
                for turn, _, speaker in diarization.itertracks(yield_label=True):
                    if (turn.end - turn.start) < 0.6:
                        continue
                    speaker_turns.setdefault(speaker, []).append((float(turn.start), float(turn.end)))

                # 2. Tính embedding đại diện cho từng speaker (tối đa 30 giây âm thanh ghép lại)
                speaker_scores: Dict[str, float] = {}
                teacher_speakers = set()

                for spk, turns in speaker_turns.items():
                    chunks = []
                    total_samples = 0
                    max_samples = 30 * sr
                    for s_sec, e_sec in turns:
                        s_idx = max(0, int(s_sec * sr))
                        e_idx = min(waveform.shape[1], int(e_sec * sr))
                        if e_idx - s_idx < int(0.8 * sr):
                            continue
                        chunks.append(waveform[:, s_idx:e_idx])
                        total_samples += (e_idx - s_idx)
                        if total_samples >= max_samples:
                            break
                    if not chunks:
                        continue
                    spk_wave = torch.cat(chunks, dim=1)
                    spk_vec = self.extract_embedding_from_tensor(spk_wave)
                    sim = self.cosine_similarity(spk_vec, ref_vec)
                    speaker_scores[spk] = round(sim, 4)
                    if sim >= threshold:
                        teacher_speakers.add(spk)

                # 3. Xuất danh sách khoảng thời gian chỉ của giảng viên
                teacher_intervals = []
                for spk in teacher_speakers:
                    for s_sec, e_sec in speaker_turns.get(spk, []):
                        teacher_intervals.append({
                            "start_sec": round(s_sec, 2),
                            "end_sec": round(e_sec, 2),
                            "speaker": spk,
                            "similarity": speaker_scores[spk],
                        })
                teacher_intervals.sort(key=lambda x: x["start_sec"])

                return {
                    "teacher_speakers": sorted(list(teacher_speakers)),
                    "speaker_scores": speaker_scores,
                    "teacher_intervals": teacher_intervals,
                    "threshold_used": threshold,
                    "method": "pyannote_diarization"
                }

            except Exception as diar_err:
                logger.warning(f"Pyannote diarization unavailable ({diar_err}), using ECAPA sliding fallback")
                # Fallback: Trích xuất các lát cắt 2.5s với độ trượt 1.0s bằng chính ECAPA-TDNN
                duration_sec = waveform.shape[1] / float(sr)
                window_size = 2.5
                step_size = 1.0
                teacher_intervals = []
                scores = []
                
                curr = 0.0
                while curr < duration_sec:
                    e_sec = min(duration_sec, curr + window_size)
                    s_idx = int(curr * sr)
                    e_idx = int(e_sec * sr)
                    if (e_idx - s_idx) >= int(0.8 * sr):
                        chunk = waveform[:, s_idx:e_idx]
                        vec = self.extract_embedding_from_tensor(chunk)
                        sim = self.cosine_similarity(vec, ref_vec)
                        scores.append(sim)
                        if sim >= threshold:
                            teacher_intervals.append({
                                "start_sec": round(curr, 2),
                                "end_sec": round(e_sec, 2),
                                "speaker": "TEACHER_WINDOW",
                                "similarity": round(sim, 4),
                            })
                    curr += step_size

                return {
                    "teacher_speakers": ["TEACHER_WINDOW"] if teacher_intervals else [],
                    "speaker_scores": {"TEACHER_WINDOW": round(float(np.mean(scores)), 4) if scores else 0.0},
                    "teacher_intervals": teacher_intervals,
                    "threshold_used": threshold,
                    "method": "ecapa_sliding_fallback"
                }

        finally:
            if os.path.exists(wav_path):
                try:
                    os.remove(wav_path)
                except Exception:
                    pass

    @staticmethod
    def merge_intervals(intervals: List[dict]) -> List[Tuple[float, float]]:
        """Hợp nhất các khoảng thời gian bị chồng lấn trước khi tính diện tích giao thoa."""
        if not intervals:
            return []
        sorted_ivs = sorted(
            [(float(iv["start_sec"]), float(iv["end_sec"])) for iv in intervals],
            key=lambda x: x[0]
        )
        merged = [sorted_ivs[0]]
        for curr_s, curr_e in sorted_ivs[1:]:
            prev_s, prev_e = merged[-1]
            if curr_s <= prev_e:
                merged[-1] = (prev_s, max(prev_e, curr_e))
            else:
                merged.append((curr_s, curr_e))
        return merged

    @staticmethod
    def filter_asr_segments(
        asr_segments: List[dict],
        teacher_intervals: List[dict],
        min_overlap_ratio: float = 0.55,
    ) -> Tuple[List[dict], List[dict]]:
        """Tách các đoạn ASR thành (kept_teacher_segments, rejected_noise_segments)."""
        kept, rejected = [], []
        merged_ivs = TeacherVoiceFilterService.merge_intervals(teacher_intervals)
        for seg in asr_segments:
            u_start = float(seg.get("start_sec", seg.get("start_time_sec", 0.0)))
            u_end = float(seg.get("end_sec", seg.get("end_time_sec", u_start + 0.1)))
            u_dur = max(0.01, u_end - u_start)

            overlap = 0.0
            for m_start, m_end in merged_ivs:
                overlap += max(0.0, min(u_end, m_end) - max(u_start, m_start))
            ratio = round(min(1.0, overlap / u_dur), 3)

            enriched = dict(seg)
            enriched["teacher_overlap_ratio"] = ratio
            if ratio >= min_overlap_ratio:
                enriched["speaker_label"] = "TEACHER"
                kept.append(enriched)
            else:
                enriched["speaker_label"] = "NON_TEACHER"
                rejected.append(enriched)
        return kept, rejected


_VOICE_SERVICE_SINGLETON: Optional[TeacherVoiceFilterService] = None

def get_voice_filter_service() -> TeacherVoiceFilterService:
    global _VOICE_SERVICE_SINGLETON
    if _VOICE_SERVICE_SINGLETON is None:
        _VOICE_SERVICE_SINGLETON = TeacherVoiceFilterService()
    return _VOICE_SERVICE_SINGLETON
