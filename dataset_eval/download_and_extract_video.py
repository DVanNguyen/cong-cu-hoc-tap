import os
import sys
import imageio_ffmpeg
import subprocess
import yt_dlp

sys.stdout.reconfigure(encoding='utf-8')

ffmpeg_exe = os.path.join(os.path.dirname(imageio_ffmpeg.get_ffmpeg_exe()), "ffmpeg.exe")
bin_dir = os.path.dirname(ffmpeg_exe)
os.environ["PATH"] = bin_dir + os.pathsep + os.environ.get("PATH", "")
print(f"Using FFmpeg: {ffmpeg_exe}")

video_url = "https://www.youtube.com/watch?v=NG3MHjcvk2A"
output_dir = "dataset_eval/video"
os.makedirs(output_dir, exist_ok=True)
video_path = os.path.join(output_dir, "cs188_sp24_lec01.mp4")
audio_path = os.path.join("dataset_eval/audio", "cs188_sp24_lec01.wav")
os.makedirs("dataset_eval/audio", exist_ok=True)

print(f"Downloading video from {video_url} (first 6 minutes for test)...")

ydl_opts = {
    'format': 'bestvideo[height<=480]+bestaudio/best[height<=480]/best',
    'outtmpl': os.path.join(output_dir, 'cs188_sp24_lec01.%(ext)s'),
    'download_ranges': yt_dlp.utils.download_range_func(None, [(0, 360)]), # first 6 minutes (360 seconds)
    'ffmpeg_location': bin_dir,
    'force_keyframes_at_cuts': True,
    'quiet': False
}

try:
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([video_url])
    print("[+] Video download complete!")
except Exception as e:
    print(f"[-] Error downloading video: {e}")

# Find downloaded video file
downloaded_files = [os.path.join(output_dir, f) for f in os.listdir(output_dir) if f.startswith("cs188_sp24_lec01")]
if downloaded_files:
    actual_video = downloaded_files[0]
    print(f"[+] Downloaded video file: {actual_video} (Size: {os.path.getsize(actual_video):,} bytes)")

    # Extract audio using FFmpeg to 16kHz mono WAV (standard for Whisper/DTW)
    print(f"Extracting audio to {audio_path}...")
    cmd = [
        ffmpeg_exe,
        "-y",
        "-i", actual_video,
        "-vn",
        "-acodec", "pcm_s16le",
        "-ar", "16000",
        "-ac", "1",
        audio_path
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode == 0 and os.path.exists(audio_path):
        print(f"[+] Audio extracted successfully: {audio_path} (Size: {os.path.getsize(audio_path):,} bytes)")
    else:
        print(f"[-] FFmpeg error: {res.stderr}")
else:
    print("[-] No video file found to extract audio from.")
