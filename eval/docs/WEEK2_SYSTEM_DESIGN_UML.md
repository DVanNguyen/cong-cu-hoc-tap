# THIẾT KẾ KIẾN TRÚC & HỆ THỐNG UML (TUẦN 2: 19/10 – 25/10)
**Đề tài:** Xây dựng trợ lý học tập ứng dụng AI hỗ trợ ghi chú bài giảng bám theo slide, hệ thống hóa kiến thức liên bài và tổng hợp deadline từ nhiều nguồn.  
**Sinh viên thực hiện:** Đàm Văn Nguyên (MSV: 24IT180, Lớp: 24JIT - VKU)  
**Giảng viên hướng dẫn:** TS. Trần Uyên Trang  

---

## 1. SƠ ĐỒ USE CASE TỔNG QUAN (USE CASE DIAGRAM)

```mermaid
flowchart LR
    subgraph Actors [Tác nhân]
        SV["👤 Sinh viên (Android App)"]
        SYS["⚙️ Hệ thống Nền (Queue Worker / Scheduler)"]
        GV["👨‍🏫 Nguồn bên ngoài (Email / Telegram / Slide)"]
    end

    subgraph CoreSystem [Hệ thống Trợ lý Học tập StudyAI]
        UC1["UC-01: Ghi chú bài giảng bám Slide"]
        UC2["UC-02: Bắt Deadline nói miệng trên lớp"]
        UC3["UC-03: Xem & Tương tác Đồ thị Khái niệm"]
        UC4["UC-04: Tra cứu Khái niệm & Cảnh báo Dùng trước Định nghĩa"]
        UC5["UC-05: Thu thập & Gộp trùng Deadline đa nguồn"]
        UC6["UC-06: Xác nhận Deadline mơ hồ (Pending Confirmation)"]
        UC7["UC-07: Xem Mức độ Cấp bách & Lộ trình Chia nhỏ (Milestones)"]
    end

    SV --> UC1
    SV --> UC3
    SV --> UC4
    SV --> UC6
    SV --> UC7

    GV -.->|Gửi Slide, Audio, Email, Telegram| SYS
    SYS --> UC1
    SYS --> UC2
    SYS --> UC5
    SYS --> UC7
```

---

## 2. BA SƠ ĐỒ TRÌNH TỰ CHI TIẾT (SEQUENCE DIAGRAMS)

### 2.1. Sơ đồ Sequence 1: Luồng Ghi chú bài giảng bám Slide & Bắt Deadline nói miệng

```mermaid
sequenceDiagram
    autonumber
    actor SV as Sinh viên (Android App)
    participant API as Máy chủ Laravel
    participant Redis as Redis Queue / PubSub
    participant AI as Dịch vụ Python AI
    participant DB as MySQL & Qdrant

    SV->>API: Upload Slide (PDF/PPTX) + Audio + Mốc chuyển slide
    API->>DB: Lưu metadata Lecture (status = pending)
    API->>Redis: Đẩy Job process_lecture (kèm idempotency_key)
    API-->>SV: 202 Accepted (Mở kết nối SSE nhận tiến độ)

    Redis->>AI: Worker nhận Job process_lecture
    AI->>AI: 1. Trích xuất văn bản & thuật ngữ từ từng Slide
    AI->>Redis: Publish SSE (25%: Đã trích xuất Slide)
    AI->>AI: 2. Lọc khoảng lặng (Silero VAD) + Whisper (kèm Glossary Prompt)
    AI->>Redis: Publish SSE (55%: Đã nhận dạng giọng nói)
    AI->>AI: 3. Căn lời giảng vào Slide (Sự kiện người dùng / Monotonic DTW)
    AI->>AI: 4. Tính điểm độ mới Novelty(u_t | s_k) >= 0.28
    AI->>AI: 5. LLM sinh ghi chú bổ sung + Giải thích thuật ngữ + Bắt deadline nói miệng
    AI->>AI: 6. Kiểm tra xác định (verify_evidence_grounding & Timestamps)
    AI->>API: POST /api/internal/ai-webhook (Kết quả JSON + HMAC SHA256)
    API->>DB: Lưu Slides, Transcript, Evidences, LectureNotes
    opt Có phát hiện lời nhắc deadline nói miệng
        API->>Redis: Đẩy tiếp vào luồng trích xuất công việc (Module 3)
    end
    API->>Redis: Publish SSE (100%: Hoàn tất)
    Redis-->>SV: Cập nhật UI Ghi chú theo Slide (Bấm mốc giờ nghe lại)
```

---

### 2.2. Sơ đồ Sequence 2: Luồng Xây dựng Đồ thị Khái niệm Liên bài & Cảnh báo Dùng trước Định nghĩa

```mermaid
sequenceDiagram
    autonumber
    actor SV as Sinh viên (Android App)
    participant API as Máy chủ Laravel
    participant AI as Dịch vụ Python AI
    participant Qdrant as Qdrant Vector DB
    participant MySQL as MySQL Database

    SV->>API: Yêu cầu cập nhật Đồ thị khái niệm môn học
    API->>AI: Gửi danh sách Slide & Ghi chú của các bài học (Bài 1..N)
    AI->>AI: 1. LLM trích xuất Khái niệm (DEFINED / USED) + Quan hệ ứng viên
    AI->>AI: 2. Kiểm tra xác định đoạn bằng chứng (verify_evidence_grounding)
    loop Với mỗi khái niệm trích xuất được
        AI->>Qdrant: Truy vấn vector đa ngôn ngữ (Bilingual Embedding)
        alt Độ tương đồng >= 0.85 hoặc trùng cặp từ điển Việt-Anh
            AI->>AI: Gộp vào Canonical Concept đã có (Entity Resolution)
        else Khái niệm mới
            AI->>Qdrant: Lưu vector Canonical Concept mới
        end
    end
    AI->>AI: 3. So sánh Pos_first_use < Pos_first_def (Phát hiện dùng trước khi định nghĩa)
    AI->>API: Trả về Đồ thị chuẩn hóa + Cảnh báo Used-Before-Defined
    API->>MySQL: Lưu bảng concepts, concept_relations, evidences
    API-->>SV: Hiển thị Đồ thị khái niệm & Cảnh báo bài cần ôn lại
```

---

### 2.3. Sơ đồ Sequence 3: Luồng Trích xuất, Xác thực Ngày giờ & Gộp trùng Deadline Đa nguồn

```mermaid
sequenceDiagram
    autonumber
    participant Src as Nguồn (Gmail / Telegram Bot / Lời giảng)
    participant API as Máy chủ Laravel
    participant AI as Dịch vụ Python AI
    participant DB as MySQL & Qdrant
    actor SV as Sinh viên (Android App)

    Src->>API: Tin nhắn / Email mới (kèm received_at)
    API->>DB: Lưu raw_messages (Chống trùng theo external_message_id)
    API->>AI: Đẩy văn bản sang Pipeline Trích xuất công việc
    AI->>AI: 1. Tiền lọc bằng Regex từ khóa học tập
    AI->>AI: 2. LLM trích xuất JSON (title, course, raw_due_expr, evidence_quote, effort)
    AI->>AI: 3. Bộ phân tích ngày giờ xác định (Asia/Ho_Chi_Minh) từ received_at
    AI->>AI: 4. Kiểm tra bằng chứng gốc (verify_evidence_grounding)
    AI->>DB: 5. Tìm công việc cùng môn học để tính MatchScore(T_new, T_existing)
    alt MatchScore >= 0.78 (Công việc trùng hoặc thông báo dời hạn)
        AI->>DB: Gộp bằng chứng mới; cập nhật due_at nếu is_deadline_extension = true
    else Công việc hoàn toàn mới
        alt Độ tin cậy >= 0.75 & Ngày giờ hợp lệ
            AI->>AI: Tách Milestones + Tính điểm cấp bách U(i) kèm câu giải thích
            AI->>DB: Tạo Task mới (status = todo)
        else Độ tin cậy < 0.75 hoặc ngày giờ mơ hồ
            AI->>DB: Đưa vào hàng chờ xác nhận (status = pending_confirmation)
        end
    end
    DB-->>SV: Hiển thị danh sách việc theo Mức cấp bách & Hộp thoại xác nhận việc mơ hồ
```

---

## 3. SƠ ĐỒ HOẠT ĐỘNG TOÀN HỆ THỐNG (ACTIVITY DIAGRAM)

```mermaid
stateDiagram-v2
    [*] --> NhanDuLieu: Sinh viên tải lên bài giảng hoặc nhận tin nhắn

    state NhanDuLieu {
        [*] --> PhanLoaiNguon
        PhanLoaiNguon --> XuLyBaiGiang: Slide PDF + Audio
        PhanLoaiNguon --> XuLyTinNhan: Email / Telegram
    }

    state XuLyBaiGiang {
        TachChuSlide --> NhanDangWhisper: Bơm Glossary Prompt
        NhanDangWhisper --> CanChinhDTW: Quy hoạch động đơn điệu
        CanChinhDTW --> ChamDiemNovelty: Ngưỡng 0.28
        ChamDiemNovelty --> SinhGhiChuBoSung: Trích xuất thuật ngữ & Bắt deadline miệng
        SinhGhiChuBoSung --> XayDungDoThi: Cập nhật thực thể liên bài
    }

    state XuLyTinNhan {
        LocRegex --> TrichXuatLLM: Trùng từ khóa học tập
        LocRegex --> BoQua: Không phải tin bài tập
        TrichXuatLLM --> XacThucNgayGio: Bộ phân tích xác định Asia/Ho_Chi_Minh
        XacThucNgayGio --> KiemTraGrounding: verify_evidence_grounding >= 85%
        KiemTraGrounding --> TinhMatchScore: So sánh việc cũ cùng môn
    }

    XayDungDoThi --> PhatHienUsedBeforeDefined: Pos(L, S) = L * 1000 + S
    TinhMatchScore --> GopTrungHoacDoiHan: MatchScore >= 0.78
    TinhMatchScore --> TaoViecMoi: MatchScore < 0.78
    TaoViecMoi --> TinhDiemCapBach: U in [0, 100] & Tách Milestones

    PhatHienUsedBeforeDefined --> DongBoAppAndroid
    GopTrungHoacDoiHan --> DongBoAppAndroid
    TinhDiemCapBach --> DongBoAppAndroid

    DongBoAppAndroid --> [*]: Hiển thị trực quan trên FocusBoard
```

---

## 4. SƠ ĐỒ CƠ SỞ DỮ LIỆU CHUẨN 13 BẢNG (ERD DIAGRAM)

```mermaid
erDiagram
    users ||--o{ courses : owns
    users ||--o{ raw_messages : receives
    users ||--o{ tasks : assigned

    courses ||--o{ lectures : contains
    courses ||--o{ concepts : defines
    courses ||--o{ concept_relations : structures
    courses ||--o{ tasks : categorizes

    lectures ||--o{ slides : has_pages
    lectures ||--o{ slide_alignments : aligns
    lectures ||--o{ transcript_segments : transcribes
    lectures ||--o{ evidences : sources

    slides ||--o{ slide_alignments : maps_to
    slides ||--o{ transcript_segments : links_to
    slides ||--o{ lecture_notes : annotated_by
    slides ||--o{ evidences : grounds

    raw_messages ||--o{ evidences : grounds

    evidences ||--o{ lecture_notes : supports
    evidences ||--o{ concept_relations : proves
    evidences ||--o{ tasks : justifies

    concepts ||--o{ concept_relations : source_or_target
    concepts ||--o{ slides : defined_or_used_in

    tasks ||--o{ task_milestones : divides_into
    ai_jobs ||--o{ lectures : tracks_async
```
