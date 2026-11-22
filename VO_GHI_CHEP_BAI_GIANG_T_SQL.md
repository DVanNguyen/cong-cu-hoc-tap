# VỞ GHI CHÉP BÀI GIẢNG THÔNG MINH - STUDYAI
## Môn học: Quản trị Cơ sở Dữ liệu SQL Server
### Bài học: #09 - Lập trình Transact-SQL (T-SQL)
**Giảng viên giảng dạy:** Thầy Lương Trần Hy Hiến  
**Thời lượng bài giảng:** 33 phút 47 giây | **Số trang slide:** 23 trang  
**Hệ thống thực hiện:** Trợ lý AI Hỗ trợ Học tập StudyAI (Đồ án Nghiên cứu & Phát triển)  

---

> [!NOTE]
> **Ý NGHĨA CỦA VỞ GHI CHÉP NÀY:**  
> Toàn bộ nội dung dưới đây được hệ thống StudyAI tự động tổng hợp dựa trên 3 Trụ cột:
> 1. **Khung sườn Slide gốc:** Lưu giữ trọn vẹn văn bản, cú pháp và code mẫu của slide.
> 2. **Lời giảng giải thích chi tiết của giảng viên (Bồi đắp khung sườn):** Tự động phát hiện những đoạn thầy giảng giải thích sâu, lưu ý thực tế mà trên slide không ghi (kèm mốc thời gian audio chính xác).
> 3. **Từ điển sống (Living Dictionary):** Tự động nhận diện thuật ngữ kỹ thuật, bổ sung định nghĩa chuẩn và tài liệu tham khảo chính thống từ Microsoft Docs & Wikipedia.

---

## MỤC LỤC BÀI HỌC
- [Slide 1: Quản trị CSDL SQL Server - Lập trình T-SQL](#slide-1-quản-trị-csdl-sql-server---lập-trình-t-sql)
- [Slide 2: Tổng quan nội dung bài học](#slide-2-tổng-quan-nội-dung-bài-học)
- [Slide 3: Khái niệm Transact-SQL (T-SQL) & Khối lệnh BEGIN...END](#slide-3-khái-niệm-transact-sql-t-sql--khối-lệnh-beginend)
- [Slide 4: Khai báo biến (DECLARE)](#slide-4-khai-báo-biến-declare)
- [Slide 5: Gán giá trị cho biến - Cách 1: Dùng lệnh SET](#slide-5-gán-giá-trị-cho-biến---cách-1-dùng-lệnh-set)
- [Slide 6: Gán giá trị cho biến - Cách 2: Dùng lệnh SELECT](#slide-6-gán-giá-trị-cho-biến---cách-2-dùng-lệnh-select)
- [Slide 7: Gán giá trị cho biến - Cách 3: Lấy từ kết quả câu truy vấn bảng](#slide-7-gán-giá-trị-cho-biến---cách-3-lấy-từ-kết-quả-câu-truy-vấn-bảng)
- [Slide 8: Biến kiểu ngày tháng (Date/Time)](#slide-8-biến-kiểu-ngày-tháng-datetime)
- [Slide 9: Biến kiểu bảng (Table Variable)](#slide-9-biến-kiểu-bảng-table-variable)
- [Slide 10: Cấu trúc điều khiển IF](#slide-10-cấu-trúc-điều-khiển-if)
- [Slide 11: Ví dụ Cấu trúc điều khiển - Tìm Max 2 số](#slide-11-ví-dụ-cấu-trúc-điều-khiển---tìm-max-2-số)
- [Slide 12: Cấu trúc điều kiện IF...ELSE](#slide-12-cấu-trúc-điều-kiện-ifelse)
- [Slide 13: Ví dụ Cấu trúc IF...ELSE - Tìm GTLN 2 số](#slide-13-ví-dụ-cấu-trúc-ifelse---tìm-gtln-2-số)
- [Slide 14: Cấu trúc lựa chọn CASE (Cú pháp 1)](#slide-14-cấu-trúc-lựa-chọn-case-cú-pháp-1)
- [Slide 15: Cấu trúc lựa chọn CASE (Cú pháp 2)](#slide-15-cấu-trúc-lựa-chọn-case-cú-pháp-2)
- [Slide 16: Cấu trúc vòng lặp WHILE - Tính tổng S = 1 + 2 + ... + N](#slide-16-cấu-trúc-vòng-lặp-while---tính-tổng-s--1--2----n)
- [Slide 17: Điều khiển vòng lặp: BREAK và CONTINUE](#slide-17-điều-khiển-vòng-lặp-break-và-continue)
- [Slide 18: Một số biến toàn cục quan trọng (@@ERROR, @@IDENTITY, @@ROWCOUNT)](#slide-18-một-số-biến-toàn-cục-quan-trọng-error-identity-rowcount)
- [Slide 19: Bảng tra cứu các biến toàn cục hệ thống](#slide-19-bảng-tra-cứu-các-biến-toàn-cục-hệ-thống)
- [Slide 20: Điều khiển ngoại lệ và bẫy lỗi (BEGIN TRY...BEGIN CATCH)](#slide-20-điều-khiển-ngoại-lệ-và-bẫy-lỗi-begin-trybegin-catch)
- [Slide 21: Các hàm lấy thông tin lỗi chi tiết](#slide-21-các-hàm-lấy-thông-tin-lỗi-chi-tiết)
- [Slide 22 & 23: Tài liệu tham khảo & Giải đáp Q&A](#slide-22--23-tài-liệu-tham-khảo--giải-đáp-qa)

---

### Slide 1: Quản trị CSDL SQL Server - Lập trình T-SQL
#### 📑 Khung sườn Slide gốc
- **Tiêu đề:** Quản trị CSDL SQL Server - #9. Lập trình T-SQL
- **Giảng viên:** Lương Trần Hy Hiến
- **Website/Kênh:** HIENLTH

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `00:30` - `01:15`
- **Nội dung thầy giải thích thêm:**  
  Thầy giới thiệu bài số 9 trong chuỗi môn học Quản trị CSDL SQL Server. Thầy nhấn mạnh rằng trước bài này sinh viên chủ yếu chỉ viết các câu lệnh truy vấn dữ liệu đơn lẻ (DML như SELECT, INSERT, UPDATE, DELETE). Bắt đầu từ bài này, chúng ta bước sang giai đoạn **lập trình có cấu trúc (Procedural Programming)** bên trong CSDL, cho phép xử lý các nghiệp vụ phức tạp, kiểm tra điều kiện và thực thi tuần tự nhiều lệnh.

#### 📖 Từ điển sống (Tham khảo mở rộng)
- **Transact-SQL (T-SQL):** Phần mở rộng độc quyền của Microsoft và Sybase dành cho ngôn ngữ SQL chuẩn (ANSI/ISO SQL), bổ sung các khả năng lập trình thủ tục như biến cục bộ, cấu trúc rẽ nhánh, vòng lặp và xử lý ngoại lệ. ([Microsoft Docs - T-SQL Reference](https://learn.microsoft.com/sql/t-sql/language-reference))

---

### Slide 2: Tổng quan nội dung bài học
#### 📑 Khung sườn Slide gốc
- T-SQL
- Khai báo biến
- Cấu trúc rẽ nhánh
- Cấu trúc lặp
- Các lỗi

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `01:15` - `01:45`
- **Nội dung thầy giải thích thêm:**  
  Thầy giải thích lộ trình bài học gồm 5 phần chính: Đầu tiên là hiểu cơ chế khối lệnh T-SQL; tiếp theo là kỹ thuật khai báo và sử dụng biến để lưu trữ giá trị tạm; sau đó áp dụng biến vào cấu trúc rẽ nhánh (`IF...ELSE`, `CASE`) và cấu trúc lặp (`WHILE`); cuối cùng là kỹ năng kiểm soát lỗi và bẫy ngoại lệ (`TRY...CATCH`) - đây là kỹ năng cực kỳ quan trọng để đảm bảo CSDL không bị treo hoặc dừng đột ngột khi xảy ra sự cố.

---

### Slide 3: Khái niệm Transact-SQL (T-SQL) & Khối lệnh BEGIN...END
#### 📑 Khung sườn Slide gốc
- T-SQL tổ chức theo từng khối lệnh, một khối lệnh có thể lồng bên trong một khối lệnh khác.
- Một khối lệnh bắt đầu bởi `BEGIN` và kết thúc bởi `END`.
- Bên trong khối lệnh có nhiều lệnh, và các lệnh ngăn cách nhau bởi dấu chấm phẩy (`;`).
- **Cấu trúc:**
```sql
BEGIN
    -- Khai báo biến
    -- Các câu lệnh T-SQL
END;
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `01:45` - `02:40`
- **Nội dung thầy giải thích thêm:**  
  - *Tại sao phải gom nhóm vào BEGIN...END?* Thầy giảng rằng trong SQL Server, nếu bạn có từ 2 câu lệnh trở lên thuộc về một điều kiện logic hoặc một thủ tục, SQL Server cần một ranh giới rõ ràng để biết đâu là điểm bắt đầu và đâu là điểm kết thúc, tương tự như cặp dấu ngoặc nhọn `{ }` trong C, C++, C# hoặc Java.
  - *Khối lệnh lồng nhau:* Thầy lưu ý sinh viên hoàn toàn có thể viết một khối `BEGIN...END` con nằm bên trong một khối `BEGIN...END` cha.
  - *Dấu chấm phẩy (`;`):* Thầy khuyên sinh viên nên tập thói quen kết thúc mỗi lệnh bằng dấu chấm phẩy `;` để chuẩn hóa code theo chuẩn ANSI và tránh lỗi cú pháp ở các phiên bản SQL Server mới.

#### 📖 Từ điển sống (Tham khảo mở rộng)
- **BEGIN...END Statement Block:** Cặp từ khóa điều khiển luồng thực thi, gom nhóm một tập hợp các câu lệnh T-SQL thành một khối lệnh logic duy nhất. ([Microsoft Docs - BEGIN...END](https://learn.microsoft.com/sql/t-sql/language-elements/begin-end-transact-sql))

---

### Slide 4: Khai báo biến (DECLARE)
#### 📑 Khung sườn Slide gốc
- **Cú pháp khai báo biến:**
```sql
DECLARE @Tên_Biến Kiểu_Dữ_Liệu
```
- **Ví dụ:**
```sql
DECLARE @Tuoi int
DECLARE @MSSV varchar(5)
DECLARE @numCount int
```
- **Quy tắc:**
  + Tên biến: Bắt đầu bởi ký tự `@`
  + Kiểu dữ liệu của biến: Lấy kiểu dữ liệu hệ thống, trừ kiểu `text`, `ntext`, `image`

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `02:40` - `04:15`
- **Nội dung thầy giải thích thêm:**  
  - *Tại sao phải khai báo biến trong CSDL?* Thầy giải thích rằng sau bài này, sinh viên sẽ học viết **Stored Procedure (Thủ tục lưu trữ)**, **Trigger (Bộ kích hoạt)** và **Function (Hàm tự định nghĩa)**. Trong các đối tượng này, chúng ta liên tục phải tính toán, lưu tạm kết quả truy vấn, hoặc truyền tham số qua lại giữa các bước thực thi, nên biến là thành phần bắt buộc phải có.
  - *Dấu `@` bắt buộc:* Thầy nhấn mạnh tên biến cục bộ **bắt buộc phải có tiền tố `@` đứng đầu** (ví dụ `@Tuoi`). Nếu viết thiếu `@`, SQL Server sẽ hiểu nhầm đó là tên cột hoặc tên đối tượng và báo lỗi cú pháp.
  - *Tại sao loại trừ kiểu `text, ntext, image`?* Thầy lưu ý đây là các kiểu dữ liệu cũ (deprecated) của SQL Server dành cho đối tượng nhị phân lớn. Hiện nay Microsoft khuyến cáo thay thế hoàn toàn bằng `varchar(max)`, `nvarchar(max)` và `varbinary(max)`.

#### 📖 Từ điển sống (Tham khảo mở rộng)
- **DECLARE (Transact-SQL):** Câu lệnh khởi tạo biến cục bộ trong thân của một batch hoặc procedure, mặc định biến mới tạo sẽ nhận giá trị `NULL` cho đến khi được gán giá trị cụ thể. ([Microsoft Docs - DECLARE](https://learn.microsoft.com/sql/t-sql/language-elements/declare-local-variable-transact-sql))

---

### Slide 5: Gán giá trị cho biến - Cách 1: Dùng lệnh SET
#### 📑 Khung sườn Slide gốc
- **Cú pháp:**
```sql
SET @Tên_Biến = Giá_Trị
```
- **Ví dụ:**
```sql
DECLARE @HoTen nvarchar(20)
SET @HoTen = N'Nguyễn Hằng Nga'
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `04:15` - `05:30`
- **Nội dung thầy giải thích thêm:**  
  - *Ký tự `N` phía trước chuỗi:* Thầy giải thích rất kỹ: vì biến `@HoTen` có kiểu là `nvarchar(20)` (lưu chuỗi Unicode tiếng Việt có dấu), nên khi gán chuỗi ký tự tiếng Việt, **bắt buộc phải có chữ `N` viết hoa phía trước** cặp dấu nháy đơn (`N'Nguyễn Hằng Nga'`). Nếu quên chữ `N`, chuỗi tiếng Việt sẽ bị lỗi font thành dấu chấm hỏi `???` hoặc ký tự rác.
  - Lệnh `SET` là chuẩn ANSI được khuyến nghị sử dụng khi muốn gán một giá trị đơn lẻ cho một biến cụ thể.

---

### Slide 6: Gán giá trị cho biến - Cách 2: Dùng lệnh SELECT
#### 📑 Khung sườn Slide gốc
- **Cú pháp:**
```sql
SELECT @Tên_Biến = Giá_Trị
```
- **Ví dụ:**
```sql
DECLARE @HoTen nvarchar(20)
SELECT @HoTen = N'Nguyễn Hằng Nga'
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `05:30` - `06:45`
- **Nội dung thầy giải thích thêm:**  
  - *So sánh SET vs SELECT:* Thầy giải thích điểm khác biệt: với lệnh `SET`, mỗi dòng lệnh ta chỉ có thể gán cho một biến duy nhất (`SET @a = 1; SET @b = 2`). Nhưng với lệnh `SELECT`, ta có thể **gán cùng lúc cho nhiều biến trong cùng một câu lệnh** (ví dụ `SELECT @HoTen = N'Hằng Nga', @Tuoi = 20`).
  - Lệnh `SELECT` gán giá trị rất tiện lợi khi ta muốn khởi tạo nhanh nhiều biến cùng lúc.

---

### Slide 7: Gán giá trị cho biến - Cách 3: Lấy từ kết quả câu truy vấn bảng
#### 📑 Khung sườn Slide gốc
- **Cú pháp:**
```sql
SELECT @Tên_Biến = Tên_cột
FROM Tên_Bảng
```
- **Ví dụ:** Tìm lương lớn nhất của tất cả nhân viên:
```sql
DECLARE @MaxSalary decimal(18,2)
SELECT @MaxSalary = MAX(Luong)
FROM NhanVien
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `06:45` - `08:30`
- **Nội dung thầy giải thích thêm:**  
  - *Lưu ý sống còn khi lấy dữ liệu từ bảng vào biến:* Thầy cảnh báo một lỗi kinh điển mà sinh viên rất hay mắc phải: Câu lệnh `SELECT @Tên_Biến = Tên_cột FROM Tên_Bảng` chỉ hoạt động an toàn và chính xác khi câu truy vấn trả về **duy nhất 1 dòng** (như dùng hàm tổng hợp `MAX`, `MIN`, `SUM` hoặc có điều kiện `WHERE KhoaChinh = ...`).
  - *Nếu câu query trả về nhiều dòng thì sao?* Thầy nhấn mạnh: Biến sẽ nhận giá trị của **dòng cuối cùng** trong tập kết quả, ghi đè lên các dòng trước đó mà không hề báo lỗi, dẫn đến kết quả sai lệch logic mà lập trình viên không hay biết!

---

### Slide 8: Biến kiểu ngày tháng (Date/Time)
#### 📑 Khung sườn Slide gốc
```sql
DECLARE @date date;
SET @date = '2019-4-16';
DECLARE @curent_date date = getdate();
DECLARE @duedate date = getdate()+2;

SELECT @date AS 'Date', 
       @curent_date AS 'Curent date',
       @duedate AS 'Due date';
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `08:30` - `10:15`
- **Nội dung thầy giải thích thêm:**  
  - *Hàm `getdate()`:* Trả về ngày giờ hiện tại của hệ điều hành máy chủ SQL Server.
  - *Cộng trừ ngày tháng trực tiếp:* Trong SQL Server, kiểu dữ liệu `date`/`datetime` hỗ trợ toán tử cộng số nguyên trực tiếp (`getdate() + 2` nghĩa là cộng thêm 2 ngày so với ngày hiện tại). Thầy giải thích điều này rất hữu ích khi lập trình hạn nộp bài (due date), ngày hết hạn mượn sách, ngày thanh toán hóa đơn.
  - *Khởi tạo giá trị ngay khi khai báo:* Từ SQL Server 2008 trở đi, ta có thể vừa khai báo vừa gán giá trị khởi tạo trên cùng 1 dòng (`DECLARE @curent_date date = getdate();`) mà không cần tách riêng dòng lệnh `SET`.

---

### Slide 9: Biến kiểu bảng (Table Variable)
#### 📑 Khung sườn Slide gốc
```sql
DECLARE @TableVariable table(
    course_name varchar(200),
    course_price int
);

INSERT INTO @TableVariable (course_name, course_price)
SELECT name, price 
FROM Training_Course WHERE id in (1,2);

SELECT course_name, course_price FROM @TableVariable;
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `10:15` - `12:45`
- **Nội dung thầy giải thích thêm:**  
  - *Bản chất của Table Variable:* Thầy giải thích biến bảng thực chất là một bảng dữ liệu tạm được lưu trữ chủ yếu trong bộ nhớ RAM (hoặc `tempdb`). Nó có cấu trúc cột, kiểu dữ liệu và ràng buộc hệt như một bảng vật lý bình thường.
  - *Khi nào nên dùng biến bảng?* Khi ta chỉ cần chứa một lượng dữ liệu nhỏ (vài chục đến vài trăm dòng) để tính toán trung gian trong một phiên làm việc hoặc trong một Stored Procedure. Hết phiên làm việc (hoặc hết batch), biến bảng sẽ tự động bị giải phóng bộ nhớ, không để lại rác trong CSDL.

#### 📖 Từ điển sống (Tham khảo mở rộng)
- **Table Variables vs Temporary Tables:** Biến bảng (`@table`) có phạm vi (scope) giới hạn trong batch/procedure hiện tại và không tham gia vào transactions rollback, giúp giảm nhẹ chi phí khóa (locking overhead) so với bảng tạm `#temp_table`. ([Microsoft Docs - Table Variables](https://learn.microsoft.com/sql/t-sql/data-types/table-transact-sql))

---

### Slide 10: Cấu trúc điều khiển IF
#### 📑 Khung sườn Slide gốc
- **Nguyên lý:** Nếu (biểu thức điều kiện) thì thực hiện Lệnh / Khối lệnh.
- **Cú pháp:**
```sql
IF (biểu thức điều kiện)
BEGIN
    Lệnh/Khối lệnh S
    hoặc SQL Statement
END
Lệnh/Khối lệnh P
hoặc SQL Statement
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `12:45` - `14:30`
- **Nội dung thầy giải thích thêm:**  
  Thầy phân tích sơ đồ khối: Biểu thức điều kiện trong ngoặc đơn được đánh giá thành `TRUE` hoặc `FALSE`. Nếu `Đúng (TRUE)`, luồng thực thi đi vào bên trong khối `BEGIN...END` để chạy lệnh S, sau đó tiếp tục thực hiện lệnh P bên ngoài. Nếu `Sai (FALSE)`, luồng thực thi bỏ qua toàn bộ khối `BEGIN...END` và nhảy thẳng xuống lệnh P.

---

### Slide 11: Ví dụ Cấu trúc điều khiển - Tìm Max 2 số
#### 📑 Khung sườn Slide gốc
```sql
DECLARE @a, @b, @Max int
SET @Max = @a
IF (@a < @b)
BEGIN
    SET @Max = @b
END
PRINT @Max
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `14:30` - `16:00`
- **Nội dung thầy giải thích thêm:**  
  - Thầy phân tích thuật toán: Giả định ban đầu `@Max = @a`. Sau đó so sánh nếu `@a < @b` thì cập nhật lại `@Max = @b`.
  - Lệnh `PRINT @Max`: Thầy lưu ý đây là lệnh xuất thông tin dạng thông điệp văn bản (Messages tab trong SQL Server Management Studio - SSMS), khác với lệnh `SELECT @Max` sẽ trả về dữ liệu dưới dạng bảng kết quả (Results tab).

---

### Slide 12: Cấu trúc điều kiện IF...ELSE
#### 📑 Khung sườn Slide gốc
- **Cú pháp:**
```sql
IF (biểu thức điều kiện)
BEGIN
    Lệnh/Khối lệnh S1
END
ELSE
BEGIN
    Lệnh/Khối lệnh S2
END
Lệnh/Khối lệnh P
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `16:00` - `17:45`
- **Nội dung thầy giải thích thêm:**  
  Thầy lưu ý rằng nếu trong nhánh `IF` hoặc nhánh `ELSE` chỉ có **duy nhất một câu lệnh đơn**, sinh viên có thể lược bỏ cặp từ khóa `BEGIN...END`. Tuy nhiên, để tránh phát sinh lỗi logic khi sau này viết thêm các dòng lệnh mới vào nhánh đó, thầy khuyên sinh viên luôn luôn bao bọc các nhánh bằng `BEGIN...END`.

---

### Slide 13: Ví dụ Cấu trúc IF...ELSE - Tìm GTLN 2 số
#### 📑 Khung sườn Slide gốc
```sql
DECLARE @a, @b, @Max int
IF (@a < @b)
BEGIN
    SELECT @Max = @b
END
ELSE
BEGIN
    SELECT @Max = @a
END
Print @Max
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `17:45` - `19:20`
- **Nội dung thầy giải thích thêm:**  
  Thầy so sánh ví dụ này với Slide 11. Ở đây, thay vì dùng `SET`, thầy cố ý dùng `SELECT @Max = @b` để sinh viên thấy rõ cả hai cách gán giá trị đều tương thích và cho ra kết quả tương đương khi làm việc với biểu thức số học.

---

### Slide 14: Cấu trúc lựa chọn CASE (Cú pháp 1)
#### 📑 Khung sườn Slide gốc
- Cho phép kiểm tra điều kiện và xuất thông tin theo từng trường hợp (tương tự `switch...case`).
- **Cú pháp 1 (Simple CASE):**
```sql
CASE <tên cột>/<biểu thức>
    WHEN <giá trị 1> THEN <biểu thức 1>
    WHEN <giá trị 2> THEN <biểu thức 2>
    ...
    [ELSE <biểu thức mặc định>]
END
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `19:20` - `21:10`
- **Nội dung thầy giải thích thêm:**  
  - *Khi nào dùng Cú pháp 1?* Thầy giảng rằng Cú pháp 1 áp dụng khi ta muốn so sánh **bằng (`=`)** trực tiếp giữa một biểu thức/cột cố định với danh sách các giá trị cụ thể. Ví dụ: `CASE GioiTinh WHEN 1 THEN N'Nam' WHEN 0 THEN N'Nữ' ELSE N'Khác' END`.
  - Nhánh `ELSE` là tùy chọn, nhưng nếu không có `ELSE` và không có điều kiện nào thỏa mãn thì `CASE` sẽ trả về `NULL`.

---

### Slide 15: Cấu trúc lựa chọn CASE (Cú pháp 2)
#### 📑 Khung sườn Slide gốc
- **Cú pháp 2 (Searched CASE):**
```sql
CASE
    WHEN <biểu thức điều kiện 1> THEN <biểu thức 1>
    WHEN <biểu thức điều kiện 2> THEN <biểu thức 2>
    ...
    [ELSE <biểu thức mặc định>]
END
```
- **Ví dụ thực tế:** Giảm 5% giá bán nếu khách hàng là Nữ, giảm 2% giá bán nếu khách hàng là Nam.

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `21:10` - `23:00`
- **Nội dung thầy giải thích thêm:**  
  - *Sự linh hoạt vượt trội của Cú pháp 2:* Thầy nhấn mạnh Cú pháp 2 mạnh mẽ hơn nhiều vì mỗi nhánh `WHEN` có thể chứa một biểu thức logic phức tạp bất kỳ (ví dụ `WHEN Luong >= 10000000 AND ThamNien > 5 THEN ...`), thay vì chỉ so sánh bằng như Cú pháp 1.
  - Cấu trúc `CASE` có thể được nhúng trực tiếp ngay bên trong mệnh đề `SELECT` của câu truy vấn bảng.

---

### Slide 16: Cấu trúc vòng lặp WHILE - Tính tổng S = 1 + 2 + ... + N
#### 📑 Khung sườn Slide gốc
- **Cú pháp:**
```sql
WHILE (biểu thức logic)
BEGIN
    -- Lệnh/Khối lệnh
END
```
- **Bài toán tính tổng $S = 1 + 2 + \dots + N$:**
```sql
DECLARE @i INT
DECLARE @N INT
DECLARE @S INT
SELECT @i = 1, @S = 0
WHILE (@i <= @N)
BEGIN
    SELECT @S = @S + @i
    SELECT @i = @i + 1
END
PRINT @S
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `23:00` - `25:15`
- **Nội dung thầy giải thích thêm:**  
  - *Khởi tạo giá trị ban đầu:* Thầy nhấn mạnh phải khởi tạo `@i = 1` và `@S = 0`. Nếu để biến mang giá trị `NULL`, thì bất kỳ phép cộng nào với `NULL` cũng sẽ cho kết quả là `NULL` (`NULL + 1 = NULL`), vòng lặp sẽ ra kết quả sai hoàn toàn.
  - *Bước tăng điều kiện lặp:* Thầy lưu ý dòng lệnh `SELECT @i = @i + 1` bắt buộc phải có để biến đếm tăng dần đến khi vượt quá `@N`. Nếu quên dòng này, điều kiện `@i <= @N` luôn luôn đúng dẫn đến **vòng lặp vô tận (infinite loop)** làm treo SQL Server.

---

### Slide 17: Điều khiển vòng lặp: BREAK và CONTINUE
#### 📑 Khung sườn Slide gốc
- `BREAK`: Thoát khỏi vòng lặp WHILE ngay lập tức.
- `CONTINUE`: Bỏ qua các câu lệnh còn lại của lần lặp hiện tại và thực hiện lần lặp mới tiếp theo.

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `25:15` - `26:40`
- **Nội dung thầy giải thích thêm:**  
  Thầy giải thích ứng dụng thực tế: Ví dụ duyệt danh sách hóa đơn để xử lý nợ, nếu phát hiện một hóa đơn vi phạm nghiêm trọng thì dùng `BREAK` để dừng toàn bộ tiến trình; hoặc nếu gặp hóa đơn đã thanh toán xong thì dùng `CONTINUE` để bỏ qua, không tính lãi phạt nữa mà nhảy sang kiểm tra hóa đơn kế tiếp.

---

### Slide 18: Một số biến toàn cục quan trọng (@@ERROR, @@IDENTITY, @@ROWCOUNT)
#### 📑 Khung sườn Slide gốc
- `@@ERROR`: Giá trị lỗi trả về trong biến toàn cục hệ thống:
  + `@@ERROR = 0`: Không xảy ra lỗi.
  + `@@ERROR <> 0`: Xảy ra lỗi với mã lỗi cụ thể là `@@ERROR`.
- `@@IDENTITY`: Giá trị khóa tự tăng (identity) gần nhất vừa được sinh ra.
- `@@ROWCOUNT`: Số lượng các dòng bị tác động bởi câu lệnh SQL gần nhất.

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `26:40` - `28:30`
- **Nội dung thầy giải thích thêm:**  
  - *Phân biệt biến cục bộ (@) và biến toàn cục (@@):* Thầy giải thích biến bắt đầu bằng một dấu `@` là do người lập trình tự tạo ra; còn biến bắt đầu bằng hai dấu `@@` là biến do chính SQL Server quản lý tự động, chúng ta chỉ có quyền đọc giá trị chứ không thể dùng lệnh `SET` để gán đè.
  - *Lưu ý về @@ERROR:* Biến `@@ERROR` sẽ bị reset về 0 ngay sau khi một câu lệnh mới thực thi thành công. Do đó, nếu muốn kiểm tra lỗi của một câu lệnh, ta phải kiểm tra hoặc lưu giá trị `@@ERROR` vào một biến cục bộ ngay lập tức ở dòng kế tiếp.
  - *Ứng dụng của @@IDENTITY:* Rất hay dùng khi thêm dữ liệu vào bảng master (ví dụ bảng `HoaDon`), sau đó cần lấy ngay mã hóa đơn vừa sinh để chèn tiếp vào bảng detail (`ChiTietHoaDon`).

#### 📖 Từ điển sống (Tham khảo mở rộng)
- **@@ERROR & @@IDENTITY:** Các biến hàm hệ thống vô hướng (scalar system functions) cung cấp trạng thái thực thi tức thời của session hiện hành. ([Microsoft Docs - @@ERROR](https://learn.microsoft.com/sql/t-sql/functions/error-transact-sql))

---

### Slide 19: Bảng tra cứu các biến toàn cục hệ thống
#### 📑 Khung sườn Slide gốc
| Tên biến hệ thống | Mục đích / Ý nghĩa |
|:---|:---|
| `@@CONNECTIONS` | Số lượng các kết nối tới server kể từ lần khởi động cuối cùng |
| `@@CPU_BUSY` | Số mili-giây hệ thống đang bận xử lý kể từ khi SQL Server khởi động |
| `@@CURSOR_ROWS` | Số hàng trong con trỏ (cursor) truy cập gần nhất |
| `@@DATEFIRST` | Giá trị thiết lập ngày đầu tuần hiện tại |
| `@@FETCH_STATUS` | Trạng thái lệnh fetch con trỏ (0: thành công, -1: lỗi hoặc hết dòng) |
| `@@LANGUAGE` | Tên ngôn ngữ hệ thống hiện đang sử dụng |
| `@@MAX_CONNECTIONS` | Số lượng kết nối tối đa cho phép |
| `@@SERVERNAME` | Tên máy chủ cục bộ |
| `@@SERVICENAME` | Tên dịch vụ SQL Server trên máy |
| `@@TIMETICKS` | Số micro-giây trên mỗi tick đồng hồ hệ thống |
| `@@TRANSCOUNT` | Số lượng giao dịch (transactions) đang mở trên kết nối hiện tại |
| `@@VERSION` | Thông tin chi tiết phiên bản SQL Server đang chạy |

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `28:30` - `29:50`
- **Nội dung thầy giải thích thêm:**  
  Thầy lướt qua các biến chẩn đoán hệ thống và nhấn mạnh sinh viên cần ghi nhớ nhất là `@@TRANSCOUNT` khi học bài Giao dịch (Transaction) để kiểm tra xem lệnh `COMMIT` hoặc `ROLLBACK` đã kết thúc hết các transaction lồng nhau hay chưa.

---

### Slide 20: Điều khiển ngoại lệ và bẫy lỗi (BEGIN TRY...BEGIN CATCH)
#### 📑 Khung sườn Slide gốc
- Sử dụng `BEGIN TRY .. END TRY` để bẫy lỗi.
- Nếu lỗi xảy ra trong khối này, hệ thống sẽ tự động nhảy vào khối `BEGIN CATCH .. END CATCH`.
- Khối `BEGIN CATCH .. END CATCH` phải được đặt ngay phía sau khối `BEGIN TRY .. END TRY`.
- **Cấu trúc:**
```sql
BEGIN TRY
    -- Các lệnh xử lý nghiệp vụ
END TRY
BEGIN CATCH
    -- Các lệnh xử lý khi có lỗi xảy ra
END CATCH
```

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `29:50` - `31:35`
- **Nội dung thầy giải thích thêm:**  
  - *Ý nghĩa thực tế:* Thầy giải thích cơ chế này hoàn toàn tương đồng với `try...catch` trong các ngôn ngữ lập trình hiện đại như C#, Java, Python. Nếu không có `TRY...CATCH`, khi gặp lỗi (ví dụ chia cho 0, vi phạm khóa ngoại, tràn số), SQL Server sẽ dừng ngang chương trình và trả về lỗi đỏ màn hình cho người dùng.
  - Khi có khối `TRY...CATCH`, chương trình không bị chết đột ngột mà quyền điều khiển được chuyển cho khối `CATCH` để ta ghi log lỗi, gửi thông báo thân thiện hoặc hoàn tác dữ liệu (`ROLLBACK TRANSACTION`).

#### 📖 Từ điển sống (Tham khảo mở rộng)
- **TRY...CATCH (Transact-SQL):** Cơ chế xử lý lỗi có cấu trúc cấp độ khối lệnh (structured exception handling), bắt giữ tất cả các lỗi có mức độ nghiêm trọng (severity) lớn hơn 10 nhưng không làm ngắt kết nối database. ([Microsoft Docs - TRY...CATCH](https://learn.microsoft.com/sql/t-sql/language-elements/try-catch-transact-sql))

---

### Slide 21: Các hàm lấy thông tin lỗi chi tiết
#### 📑 Khung sườn Slide gốc
| Hàm hệ thống | Mô tả chức năng |
|:---|:---|
| `ERROR_NUMBER()` | Trả về mã số của lỗi |
| `ERROR_MESSAGE()` | Trả về thông báo lỗi đầy đủ (chuỗi mô tả chi tiết) |
| `ERROR_SEVERITY()` | Trả về mức độ nghiêm trọng của lỗi |
| `ERROR_STATE()` | Trả về trạng thái lỗi |
| `ERROR_LINE()` | Trả về số thứ tự dòng code gây ra lỗi |
| `ERROR_PROCEDURE()` | Trả về tên của Stored Procedure hoặc Trigger phát sinh lỗi |

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `31:35` - `33:00`
- **Nội dung thầy giải thích thêm:**  
  Thầy hướng dẫn thực hành: Bên trong khối `BEGIN CATCH`, ta gọi các hàm này (đặc biệt là `ERROR_MESSAGE()` và `ERROR_LINE()`) để biết chính xác lỗi xảy ra ở dòng thứ mấy và nguyên nhân là gì, giúp việc debug sửa lỗi nhanh chóng hơn rất nhiều.

---

### Slide 22 & 23: Tài liệu tham khảo & Giải đáp Q&A
#### 📑 Khung sườn Slide gốc
- Website tham khảo: `https://www.tsql.info`, `https://www.sqlservertutorial.net`
- Q&A (Hỏi & Đáp)

#### 🎙️ Lời thầy giảng giải thích chi tiết
- **Mốc thời gian:** `33:00` - `33:47`
- **Nội dung thầy dặn dò kết bài:**  
  Thầy tổng kết lại bài học số 9 và dặn dò sinh viên cần thực hành gõ lại toàn bộ các ví dụ về khai báo biến, câu lệnh IF/CASE và cấu trúc TRY CATCH trên SQL Server Management Studio. Thầy nhấn mạnh bài này là nền tảng sống còn để buổi sau học sang Stored Procedure và Function. Thầy chào tạm biệt sinh viên và kết thúc video ở mốc `33:47`.

---
*Tài liệu được trích xuất và đồng bộ tự động bởi Hệ thống Trợ lý AI StudyAI.*
