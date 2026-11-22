# Tài Liệu Nghiên Cứu Lý Thuyết & Quyết Định Kỹ Thuật (Tuần 1)

**Người thực hiện:** Đàm Văn Nguyên (24IT180 - Lớp 24JIT)  
**Đồ án:** Trợ lý học tập ứng dụng AI hỗ trợ ghi chú bám slide, đồ thị kiến thức & tổng hợp deadline.  
**Ngày hoàn thành:** 13/10/2026  

---

## 1. Bài Báo Whisper (Radford et al., 2022) & Giới Hạn Với Tiếng Việt
* **Cơ chế hoạt động:** Whisper là kiến trúc Sequence-to-Sequence Transformer được huấn luyện trên 680,000 giờ dữ liệu âm thanh đa ngôn ngữ thu thập từ web.
* **Đặc thù với tiếng Việt:** Tiếng Việt là ngôn ngữ đơn lập, có thanh điệu (tonal language) và quy tắc tạo từ ghép. Dữ liệu tiếng Việt trong tập huấn luyện của Whisper chiếm tỉ lệ khiêm tốn (~1.2%).
* **Hiện tượng trộn ngữ (Code-switching Việt – Anh):** Khi giảng viên nói tiếng Việt chèn thuật ngữ tiếng Anh (*"hàm heuristic", "search tree", "tác tử duy lý"*), Whisper bản tiêu chuẩn dễ phiên âm sai thuật ngữ tiếng Anh sang từ tượng thanh tiếng Việt (ví dụ: *"PEAS"* thành *"pi ai ét"*, *"Admissible"* thành *"át mít si bồ"*).
* **Kết luận:** Bắt buộc phải có giải pháp đưa từ khóa kỹ thuật vào `initial_prompt` để dẫn hướng giải mã (prompt conditioning).

---

## 2. Thư Viện faster-whisper & Hai Kỹ Thuật Trọng Tâm
`faster-whisper` là bản cài đặt lại của OpenAI Whisper sử dụng engine **CTranslate2** (tối ưu hóa trên C++ và CUDA):
* **Tốc độ:** Nhanh gấp 4 lần so với bản gốc của OpenAI trên GPU và giảm 50% bộ nhớ VRAM nhờ lượng tử hóa 8-bit (`int8` / `float16`).
* **`vad_filter=True` (Voice Activity Detection với Silero VAD):**
  * Tự động phát hiện và loại bỏ các khoảng lặng, tiếng thở, tiếng ồn phòng học khi giảng viên dừng viết bảng.
  * Giảm 30% - 50% thời gian xử lý ASR, đồng thời ngăn chặn hiện tượng Whisper bị lặp từ (hallucination loop) khi âm thanh chỉ có tiếng ồn nền.
* **`initial_prompt`:**
  * Whisper nhận chuỗi prompt tối đa 224 tokens trước khi bắt đầu dịch đoạn đầu tiên.
  * Khi trích xuất các từ khóa từ Slide bài giảng (ví dụ từ `eval/glossary_ai.csv`) đưa vào `initial_prompt`, mô hình sẽ ưu tiên các token này trong không gian tìm kiếm beam search, giúp tỉ lệ nhận dạng đúng thuật ngữ chuyên ngành tăng vọt từ 65% lên hơn 90%.

---

## 3. Định Nghĩa Độ Đo WER (Word Error Rate)
* **Công thức chuẩn:**
  $$\text{WER} = \frac{S + D + I}{N} = \frac{S + D + I}{S + D + C}$$
  * $S$ (Substitutions): Số từ bị nhận dạng sai thành từ khác.
  * $D$ (Deletions): Số từ bị bỏ sót (mô hình không nghe ra).
  * $I$ (Insertions): Số từ bị chèn thêm (mô hình tự "vẽ" ra).
  * $C$ (Correct): Số từ nhận dạng đúng.
  * $N$: Tổng số từ trong bản chép chuẩn (Ground Truth).
* **Lưu ý đánh giá thực nghiệm:**
  * WER bị ảnh hưởng nặng bởi chuẩn hóa chữ hoa/thường và dấu câu. Do đó, trước khi tính WER bắt buộc phải chuẩn hóa văn bản qua hàm `normalize_text` (chuyển chữ thường, bỏ dấu chấm phẩy, chuẩn hóa khoảng trắng và Unicode NFC).

---

## 4. Tóm Tắt Sentence-BERT & Đo Độ Tương Đồng Ngữ Nghĩa
* **Vấn đề:** BERT truyền thống cần cặp câu $(A, B)$ để chấm tương đồng qua Cross-Encoder, độ phức tạp $O(n^2)$, quá chậm khi so từng đoạn lời giảng với hàng chục slide.
* **Giải pháp Sentence-BERT (Reimers & Gurevych, 2019):**
  * Sử dụng mạng Siamese để tạo vector embedding có kích thước cố định (ví dụ 384 hoặc 768 chiều) cho từng câu/slide.
  * Tính độ tương đồng ngữ nghĩa bằng khoảng cách Cosine (Cosine Similarity) trong tích tắc:
    $$\text{Cosine Similarity} = \frac{u \cdot v}{\|u\| \|v\|}$$
* **Mô hình khuyến nghị cho tiếng Việt:** `bkai-foundation-models/vietnamese-bi-encoder` hoặc mô hình đa ngôn ngữ `BAAI/bge-m3` / `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`.

---

## 5. Quyết Định Kỹ Thuật Đạt Được Cho Tuần 3
1. **Lựa chọn mô hình ASR:** Dùng `faster-whisper` với model **`small`** chạy trên GPU **NVIDIA GeForce RTX 3050 (4GB VRAM)** với kiểu tính toán `float16`. Model `small` cân bằng hoàn hảo giữa độ chính xác tiếng Việt và tốc độ xử lý Real-Time Factor (RTF $\approx 0.05 - 0.08$).
2. **Kỹ thuật bắt buộc:**
   * Bật `vad_filter=True` để lọc khoảng lặng phòng học.
   * **Bắt buộc dùng `initial_prompt`** lấy danh sách thuật ngữ trích xuất tự động từ slide để ép Whisper nhận diện chính xác các từ chuyên ngành tiếng Anh.
