# BÁO CÁO TỔNG KẾT TUẦN 1 (13/10 – 18/10/2026)
**Đề tài:** Trợ lý học tập ứng dụng AI hỗ trợ ghi chú bài giảng bám theo slide, hệ thống hóa kiến thức liên bài và tổng hợp deadline từ nhiều nguồn.  
**Sinh viên thực hiện:** Đàm Văn Nguyên (MSV: 24IT180 - Lớp 24JIT)  
**Giảng viên hướng dẫn:** TS. Trần Uyên Trang  

---

### 1. Dữ liệu đã có (Data Inventory)
* **Môn học chính thức:** Trí tuệ nhân tạo (*Artificial Intelligence*) - tài liệu tại `C:\AI`.
* **Tài liệu slide:** 5 file PDF giáo trình chuẩn (192 trang tổng cộng), bao gồm: Chapter 1 & 2 (Intelligent Agents, PEAS), Chapter 3 (Solving Problems by Searching, BFS, DFS, A*), Chapter 4 (Search in Complex Environments).
* **Dữ liệu bài giảng:** Đã xây dựng bộ dữ liệu chép tay chuẩn Ground-truth 10 phút (21 phân đoạn âm thanh 5-20s, gắn 4 nhãn: *giảng thêm, đọc slide, nói chuyện ngoài lề, hành chính*, định vị mốc thời gian và liên kết slide).
* **Dữ liệu tin nhắn:** Đã thu thập và gán nhãn chi tiết **32 tin nhắn** đa dạng (email, Telegram, lời nhắc miệng trong lớp; có đủ hạn rõ ràng, ngày tương đối, tin dời hạn, tin hủy, tin nhiễu).
* **Bảng thuật ngữ:** 20 cặp thuật ngữ kỹ thuật Việt – Anh cốt lõi của môn học kèm quan hệ tiền đề.

---

### 2. Kết quả WER Baseline, Tốc độ chạy & Phân tích lỗi
* **Mô hình thử nghiệm:** `faster-whisper` (model `small`, `float16` trên GPU NVIDIA GeForce RTX 3050 4GB).
* **Tốc độ thực thi (RTF - Real-Time Factor):** Đạt $\approx 0.064$, tức 10 phút âm thanh (600 giây) được xử lý trong khoảng **38.5 giây** trên GPU rời.
* **Độ chính xác:**
  * WER cơ sở (Word Error Rate): $\approx 11.2\%$ trên ngữ cảnh tiếng Việt trộn thuật ngữ Anh.
  * CER (Character Error Rate): $\approx 4.8\%$.
* **Phân loại lỗi chính:**
  1. *Lỗi phiên âm từ mượn/tiếng Anh:* Các từ như `PEAS`, `Stochastic`, `Admissible` nếu không có prompt mồi dễ bị mô hình cố ép dịch sang từ đồng âm tiếng Việt.
  2. *Lỗi thanh điệu & ngắt từ tiếng Việt:* Các cụm từ nói nhanh hoặc tiếng ồn phòng học khiến từ ghép bị tách đôi.
  3. *Khoảng lặng phòng học:* Chiếm 25-35% thời lượng khi giảng viên ghi bảng, cần lọc triệt để bằng Silero VAD.

---

### 3. Vấn đề kỹ thuật gặp phải & Cách khắc phục
* **Docker trên máy phát triển:** Lệnh `docker` chưa có trong biến môi trường Windows. Đã áp dụng chiến lược chạy native trực tiếp: Python 3.11 chạy FastAPI trên cổng `8001`, Qdrant chạy chế độ in-memory/disk qua `qdrant-client`, MySQL 8.0 chạy trực tiếp qua service `MySQL91`. Endpoint `/health` đã thông suốt.
* **Xung đột mã hóa ký tự (cp1252 vs UTF-8):** Bàn điều khiển PowerShell mặc định dùng cp1252 gây lỗi font chữ tiếng Việt có dấu khi in stdout; đã khắc phục bằng cách cấu hình `$env:PYTHONIOENCODING="utf-8"` và chuẩn hóa Unicode NFC ở tầng backend.

---

### 4. Quyết định kỹ thuật cho Tuần 2
1. **Thiết kế kiến trúc Tuần 2:** Chốt 4 tầng theo sơ đồ: App Android (FocusBoard/StudyAI) $\leftrightarrow$ Laravel Gateway (MySQL + Queue) $\leftrightarrow$ Python AI Service (FastAPI + Worker) $\leftrightarrow$ Lưu trữ (MySQL, Qdrant, File).
2. **Kế thừa mã nguồn:** Kế thừa hệ thống xác thực Sanctum, cơ chế Server-Sent Events (SSE) và bảng Tasks sẵn có của [`focusboard-api`](file:///c:/Users/Nguyen/AndroidStudioProjects/focusboard-api).
3. **Mô hình ASR:** Sử dụng `faster-whisper small` kết hợp `vad_filter=True` và mồi thuật ngữ trích xuất từ slide vào `initial_prompt`.

---

### 5. Rủi ro còn lại & Biện pháp giảm thiểu
* **Chất lượng ghi âm thực tế tại giảng đường:** Tiếng vang (reverb) và tiếng ồn sinh viên nói chuyện. *Biện pháp:* Khuyên sinh viên đặt điện thoại gần bục giảng, áp dụng bộ lọc VAD và chuẩn hóa âm lượng trước khi đưa vào Whisper.
* **Sự không chắc chắn của LLM khi đọc ngày tương đối:** Cụm từ như *"thứ 6 tuần sau"* có thể bị hiểu sai múi giờ. *Biện pháp:* Bắt buộc truyền `received_at` và múi giờ `Asia/Ho_Chi_Minh`, dùng thư viện quy đổi ngày xác định bằng code để đối chiếu chéo (cross-check), nếu lệch thì hạ độ tin cậy và chuyển sang Hộp xác nhận cho người dùng duyệt.
