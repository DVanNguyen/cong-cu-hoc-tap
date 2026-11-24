# Dataset Huấn Luyện & Tinh Chỉnh (Tuần 2 & Tuần 3)

Thư mục này dùng để lưu trữ các bộ dữ liệu mở phục vụ việc lọc giọng giảng viên, chống nhiễu lớp học và tinh chỉnh ASR tiếng Việt.

## 1. Cấu trúc thư mục

```
dataset_train/
├── speaker_vi/     # Dữ liệu giọng nói tiếng Việt cho Voiceprint / Speaker Verification
├── noise_aug/      # Dữ liệu tiếng ồn phòng học, tiếng nói chuyện để trộn Data Augmentation
└── asr_vi/         # Dữ liệu giọng nói có nhãn để đo WER và đánh giá ASR đa phương ngữ
```

## 2. Danh mục nguồn dữ liệu cần tải

| Thư mục | Nguồn dữ liệu | Link tải | Mục đích sử dụng |
| :--- | :--- | :--- | :--- |
| `speaker_vi/` | **Vietnam-Celeb** & **VoxVietnam** | [Vietnam-Celeb](https://huggingface.co/datasets/hustep-lab/Vietnam-Celeb) / [VoxVietnam](https://huggingface.co/datasets/hustep-lab/VoxVietnam-Dataset) | Fine-tune mô hình xác thực giọng nói SpeechBrain ECAPA-TDNN cho tiếng Việt, đo EER. |
| `noise_aug/` | **MUSAN** & **RIRS_NOISES** | [MUSAN (OpenSLR-17)](https://www.openslr.org/17/) / [RIRS_NOISES (OpenSLR-28)](https://www.openslr.org/28/) | Trộn tiếng ồn nền (tiếng quạt, tiếng xì xào, tiếng gõ bàn phím) và độ vang phòng học khi huấn luyện bộ lọc giọng. |
| `asr_vi/` | **VIVOS**, **ViMD** & **LSVSC** | [VIVOS (Zenodo)](https://zenodo.org/records/7068130) / [ViMD](https://huggingface.co/datasets/nguyendv02/ViMD_Dataset) / [LSVSC](https://huggingface.co/datasets/doof-ferb/LSVSC) | Tập đánh giá WER chuẩn cho bài giảng tiếng Việt đa phương ngữ (Bắc - Trung - Nam) với PhoWhisper và WhisperX. |
