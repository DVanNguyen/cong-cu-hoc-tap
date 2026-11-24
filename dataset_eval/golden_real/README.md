# Tập Kiểm Thử Vàng Thực Nghiệm (Golden Real Evaluation Set)

Tập dữ liệu này thay thế hoàn toàn các kịch bản kiểm thử giả lập, gồm các bản ghi âm bài giảng thực tế thu trực tiếp trong lớp học bằng ứng dụng StudyAI.

## 1. Cấu trúc bản ghi kiểm thử

Mỗi bài giảng kiểm thử gồm 3 thành phần chính:
1. File ghi âm bài giảng hoàn chỉnh (`.m4a`).
2. File giọng mẫu giảng viên `enrollment_teacher_20s.wav` (cắt đúng 20 giây đầu chỉ có giọng thầy nói rõ ràng).
3. File nhãn chuẩn xác `ground_truth.json` phân biệt rõ giọng giảng viên và tiếng ồn sinh viên.

## 2. Ba kịch bản thực tế bắt buộc

- `lecture_01_front_row.m4a`: Ngồi bàn đầu, giảng viên nói rõ (kiểm tra ngưỡng cơ bản).
- `lecture_02_noisy_mid.m4a`: Ngồi giữa lớp, có tiếng quạt + 2-3 đoạn sinh viên xung quanh nói chuyện hoặc hỏi bài (kiểm thử bộ lọc giọng giảng viên Tuần 2).
- `lecture_03_bilingual_slides.m4a` + `slides_en.pdf`: Slide viết tiếng Anh, giảng viên nói tiếng Việt trộn thuật ngữ (kiểm thử căn gióng BGE-M3 và chất lượng Note Tuần 3).

## 3. Cấu trúc Schema `ground_truth.json`

```json
{
  "lecture_id": "lecture_02_noisy_mid",
  "teacher_enrollment_file": "enrollment_teacher_20s.wav",
  "segments": [
    {
      "start_sec": 12.4,
      "end_sec": 26.8,
      "speaker": "TEACHER",
      "slide_page": 4,
      "reference_text": "Trong T-SQL, lệnh SELECT có thể gán nhiều biến cùng lúc từ bảng dữ liệu."
    },
    {
      "start_sec": 27.1,
      "end_sec": 31.5,
      "speaker": "STUDENT_NOISE",
      "slide_page": 4,
      "reference_text": "Ê slide mấy rồi mày ơi cho tao mượn bút."
    }
  ]
}
```
