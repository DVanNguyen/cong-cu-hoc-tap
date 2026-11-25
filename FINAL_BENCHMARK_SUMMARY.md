# Báo Cáo Tổng Hợp Kết Quả Thực Nghiệm Hệ Thống StudyAI (v1 vs. v2)

- **Đề tài:** Nghiên cứu, xây dựng hệ thống trợ lý AI hỗ trợ học tập (*StudyAI*)
- **Sinh viên thực hiện:** Đàm Văn Nguyên (MSV: `24IT180` — Lớp: `24JIT`)
- **Giảng viên hướng dẫn:** TS. Trần Uyên Trang — Trường ĐH CNTT & Truyền thông Việt - Hàn (VKU)
- **Nguồn dữ liệu báo cáo tự động:** `ai-service/week2_evaluation_report.json`, `ai-service/week3_evaluation_report.json`, `ai-service/week5_evaluation_report.json`, `ai-service/week6_e2e_verification_report.json`

---

## 1. Bảng Đối Chứng Tổng Hợp: Baseline (v1) vs. Kiến Trúc Đề Xuất (v2)

| Bài toán đánh giá | Phiên bản cũ (Baseline v1) | Phiên bản nâng cấp (StudyAI v2) | Cải thiện đạt được |
| :--- | :--- | :--- | :--- |
| **Lọc tiếng sinh viên chen ngang (Batch)** | Chưa có (`0%`) | `pyannote 3.1` + `ECAPA-TDNN`: **100.0%** Rejection, **100.0%** Teacher Recall (EER `0.0%`) | Loại bỏ hoàn toàn nhiễu bàn tán trong lớp |
| **Lọc tiếng sinh viên trực tiếp (Live 8s)** | Chưa có (`0%`) | `verify_short_chunk`: **100.0%** (`3/3`) chặn trong **94.9ms**, giữ **100.0%** chunk giảng viên | Chặn trước ASR, tiết kiệm 100% thời gian Whisper cho đoạn nhiễu |
| **Căn gióng bài giảng T-SQL (760s, 322 đoạn)** | DTW `rapidfuzz`: **78.88%** kẹt ở Slide 2 | `viterbi_bge_m3`: **100.0%** trong Slide 1–9, Slide 2 còn **0.0%**, **0** đoạn trôi Slide 15–21 | Phá triệt để "Hố đen Mục lục" & chống trôi cuối bài |
| **Căn gióng song ngữ Việt–Anh (CS188, 67 slide)** | `rapidfuzz`: **22.58%** Top-1 | `viterbi_bge_m3`: **96.77%** Top-1 (hybrid) / **93.55%** (semantic) | Tăng **+74.19 điểm %** xuyên ngôn ngữ |
| **Phục hồi nhảy phi tuyến (Non-Monotonic)** | DTW: **71.43%** (`0/2` quay lại Slide 8) | `viterbi_bge_m3`: **100.0%** (`5/5` lùi Slide 3, `2/2` quay lại Slide 8) | Bám sát giảng viên lật ngược slide cũ để nhắc bài |
| **Bám Slide trực tiếp Live (94 chunk × 8s)** | `NonMonotonicSlideTracker`: **74.47%**, trôi **17** chunk | `windowed_bge_m3_live`: **95.74%**, **0** chunk trôi Slide 15–21; song ngữ Live **100.0%** (`5/5`) | Ổn định thời gian thực cho cả bài thuần Việt & song ngữ |
| **Gắn ghi chú đúng cấp Block trên Slide** | Gắn cứng `b1`: **0.0%** | Semantic Block Mapping (`BGE-M3`): **93.33%** (`14/15` mẫu đúng block) | Hiển thị ghi chú ngay dưới đúng ý/công thức đang giảng |

---

## 2. Chi Tiết Theo Từng Trụ Cột Kỹ Thuật

### 2.1. Xác thực giọng giảng viên & Lọc nhiễu giảng đường (Tuần 2 & Tuần 5)
- **Mô hình:** `speechbrain/spkrec-ecapa-voxceleb` (vector chuẩn hóa L2 192 chiều) kết hợp phân đoạn người nói `pyannote/speaker-diarization-3.1` (có fallback cửa sổ trượt mượt trên Windows).
- **Độ phân tách Cosine Similarity (Batch):** Trung bình giọng giảng viên đạt **`0.7765`**, trong khi tạp âm/sinh viên nói chuyện ở mức **`-0.1063`**.
- **Chế độ Live (8s/chunk):** Dùng trực tiếp `verify_short_chunk(threshold=0.35)` trước khi gọi Faster-Whisper, phản hồi từ chối trung bình chỉ **`94.9ms`** trên CPU.

### 2.2. Căn gióng ngữ nghĩa đa ngôn ngữ & Chống trôi Slide (Tuần 3)
- **Mô hình nhúng:** Singleton `BAAI/bge-m3` (1024 chiều) tích hợp bộ nhớ đệm vector (thời gian nhân ma trận `120 × 23` khi ấm cache đạt `0.001s`).
- **Thuật toán Windowed Non-Monotonic Viterbi:**
  - Khử hiện tượng *hubness* của các trang slide dài nhiều chữ bằng cách trừ nền phân vị thứ 25 của từng cột.
  - Gắn trọng số phát xạ theo mật độ từ khóa nội dung (`min(1.0, content_tokens / 4.0)`) để các từ đệm ngắn (*"ờ", "rồi", "ok"*) không kéo trôi trang.
  - Phân bố 322 đoạn trên 760s đầu bài giảng T-SQL: `{"3": 40, "4": 36, "5": 18, "6": 2, "7": 100, "8": 102, "9": 24}`.

### 2.3. Tổng hợp ghi chú chống ảo giác & Trải nghiệm người dùng toàn trình (Tuần 4 & Tuần 6)
- **Cổng chống bịa đặt (`Anti-Hallucination Gate`):** Mỗi ghi chú 3 tầng (`core_explanation`, `worked_example`, `exam_tip`) từ Gemini Structured Output bắt buộc đính kèm `evidence_quote` và được hậu kiểm bằng `verify_evidence_grounding()` trước khi ghi xuống MySQL.
- **Đối chiếu âm thanh tức thì & Đọc ngoại tuyến:** Laravel phục vụ luồng âm thanh `HTTP 206 Partial Content` (`Accept-Ranges: bytes`) cho Media3 `ExoPlayer` tua chính xác tới mili-giây (`1.0x–2.0x`), kết hợp lưu đệm `cache_lecture_{id}.json` phục vụ đọc offline.

---

## 3. Kết Quả Kiểm Thử Toàn Trình 5 Kịch Bản End-to-End (Tuần 6)

- **Trạng thái tổng thể:** **`PASSED`** (Thời điểm chạy: `2026-11-25 01:01:05`)

| Kịch bản End-to-End | Kết quả | Ghi nhận thực tế |
| :--- | :---: | :--- |
| 1. Batch Upload bài giảng Việt + Code (Queue bất đồng bộ) | ĐẠT | HTTP `202` trong `0.366s`; lưu `23` slide, `1` ghi chú sạch 100% có `evidence_quote` |
| 2. Đăng ký Voiceprint & Lọc nhiễu giảng đường | ĐẠT | Vector `192` chiều lưu MySQL, lọc sạch đoạn nhiễu trong pipeline |
| 3. Song ngữ Slide Tiếng Anh (CS188) – Giảng Tiếng Việt | ĐẠT | Top-1 đạt `96.77%`, sinh `1` ghi chú giữ nguyên thuật ngữ tiếng Anh |
| 4. Live Slide-Follow + `finish-live` + `refine_full_lecture` | ĐẠT | Nhiễu bị chặn (`ignored_non_teacher`), ghép `.m4a` thành công, tinh chỉnh không trùng slide (`23` slide), stream `HTTP 206` |
| 5. Chỉnh sửa / Xóa Note (`is_edited_by_user`) & Xuất `.md` | ĐẠT | `PUT`/`DELETE` đồng bộ MySQL, xuất `.md` (`3457` ký tự) có nhãn `[Cần xem lại]` |

---

## 4. Ghi Chú Minh Bạch Về Phạm Vi Dữ Liệu Thực Nghiệm & Hướng Mở Rộng

1. **Dữ liệu ghi âm bài giảng thực tế:** Bài giảng *Quản trị CSDL SQL Server — #09 Lập trình T-SQL* (23 slide, 33 phút 47 giây, phiên âm bằng Faster-Whisper `medium`) và bài giảng *UC Berkeley CS188 Spring 2024 Lecture 1* (67 slide).
2. **Các bộ kiểm thử có kiểm soát (Controlled Evaluation Subsets):** Để đo chính xác từng hiện tượng biên (31 câu giảng tiếng Việt khớp chéo sang 67 slide tiếng Anh CS188, chuỗi chèn 5 câu lật ngược từ Slide 8 về Slide 3, và 15 mẫu gắn block), nhóm nghiên cứu xây dựng bộ nhãn đối chứng có kiểm soát trong `dataset_eval/golden_real/`.
3. **Hướng phát triển tiếp theo:** Thu thập thêm >10 giờ ghi âm thực địa đa học phần tại giảng đường VKU để tinh chỉnh LoRA cho Whisper/PhoWhisper chuyên biệt thuật ngữ CNTT Việt–Anh và tự động hóa đồng bộ đồ thị tri thức liên học phần.
