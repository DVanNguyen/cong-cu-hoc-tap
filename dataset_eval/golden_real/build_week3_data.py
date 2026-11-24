"""
Sinh bộ dữ liệu kiểm thử Tuần 3 (viết tay, KHÔNG phải dữ liệu thu âm thật):
  - bilingual_cs188_eval.json : 30 câu giảng tiếng Việt (xen thuật ngữ Anh) -> trang slide CS188 gốc (tiếng Anh)
  - nonmonotonic_tsql_eval.json: chuỗi câu giảng T-SQL có 5 câu quay ngược Slide 8 -> Slide 3
  - block_tsql_eval.json       : ghi chú -> block đúng trên slide T-SQL (gold = chuỗi con của block)
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))


def dump(name, obj):
    with open(os.path.join(HERE, name), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


# ---------------------------------------------------------------- 1. Song ngữ CS188
bilingual = [
    (3, "Đầu tiên em xin giới thiệu thầy Cameron Allen, postdoc năm nhất, nghiên cứu về an toàn AI ở trung tâm Human-Compatible AI."),
    (4, "Người thứ hai là Michael Cohen, đang làm postdoc cùng giáo sư Stuart Russell và từng học PhD ở Oxford."),
    (8, "Về chuyện đăng ký học phần, đội giảng dạy không quyết định được, chỉ sinh viên ngành CS mới được enroll học kỳ này."),
    (9, "Lớp học diễn ra ở phòng Wheeler 150, thứ Ba và thứ Năm, và các bạn bắt buộc phải đến lớp trực tiếp."),
    (10, "Mỗi buổi sẽ có một mã điểm danh, các bạn nhập mã đó để chứng minh là có mặt trong lớp."),
    (11, "Sau mỗi buổi có đường link khảo sát để các bạn góp ý về bài giảng, cũng đăng trên website môn học."),
    (12, "Có ba loại buổi thảo luận: thảo luận thường, thảo luận ôn thi, và thảo luận dành cho sinh viên cần thêm thời gian."),
    (13, "Giờ office hours bạn có thể đến trực tiếp hoặc tham gia online để hỏi bài tập hay nêu vấn đề với trợ giảng."),
    (14, "Hãy ghi lại ngày thi: giữa kỳ vào tối thứ Ba mùng 5 tháng 3, còn thi cuối kỳ vào sáng thứ Năm ngày 9 tháng 5."),
    (15, "Mọi tài liệu như slide, ghi chú, video ghi hình và bài tập đều được đăng trên trang web của khóa học."),
    (16, "Điểm số gồm sáu project chiếm 25 phần trăm, là bài lập trình Python được chấm tự động, có thể làm theo cặp."),
    (17, "Chúng tôi sẽ bỏ điểm bài tập về nhà thấp nhất, và mỗi bạn có năm slip days để nộp trễ các project."),
    (18, "Sinh viên khuyết tật có thể đăng ký chương trình DSP của Berkeley để được hỗ trợ điều chỉnh việc học."),
    (19, "Về liêm chính học thuật, các bạn được thảo luận ý tưởng nhưng tuyệt đối không được sao chép bài của người khác."),
    (20, "Sức khỏe tinh thần quan trọng hơn môn học này, nếu cảm thấy quá tải thì hãy tìm đến các nguồn hỗ trợ."),
    (21, "Nửa sau buổi học hôm nay ta sẽ trả lời câu hỏi trí tuệ nhân tạo là gì và nó làm được những gì."),
    (36, "Vậy ta nên xây dựng loại máy nào: máy suy nghĩ như con người, suy nghĩ hợp lý, hành động như người hay hành động hợp lý?"),
    (37, "Trong môn này từ rational mang nghĩa kỹ thuật rất cụ thể: đạt được tối đa các mục tiêu đã định sẵn."),
    (39, "Theo quan điểm dựa trên kỹ năng, một hệ thống chỉ thông minh nếu nó chơi được cờ vua hay học được từ kinh nghiệm."),
    (40, "Rodney Brooks đưa ra góc nhìn embodiment, nhấn mạnh trí thông minh gắn với cơ thể vật lý trong môi trường thật."),
    (41, "François Chollet thì cho rằng phải đo năng lực chứ không phải kỹ năng, trên nhiều loại nhiệm vụ khác nhau."),
    (42, "Stuart Russell đề xuất mục tiêu của máy là tối đa hóa độ thỏa dụng của con người, ban đầu máy không chắc về sở thích đó."),
    (44, "Còn bộ não thì sao? Não người ra quyết định hợp lý khá tốt nhưng không hoàn hảo, và không chia module như phần mềm."),
    (47, "Giai đoạn 2010 đến 2017 là thời kỳ big data, GPU và deep learning, AlexNet thắng cuộc thi ImageNet năm 2012."),
    (49, "Năm 2016 chương trình AlphaGo của Google đã đánh bại kỳ thủ Lee Sedol ở môn cờ vây."),
    (55, "Mục tiêu của khóa học là thiết kế rational agent, một thực thể cảm nhận môi trường và hành động để tối đa hóa utility kỳ vọng."),
    (56, "Lấy ví dụ Pac-Man là một agent: sensors nhận percepts từ environment, còn actuators thực hiện các actions."),
    (58, "Các thành phần cốt lõi gồm search và planning, probability và inference, reinforcement learning và supervised learning."),
    (65, "Bạn có nên học CS 188 không? Có, nếu muốn biết cách thiết kế rational agent, và môn này rèn thêm tư duy toán."),
    (66, "Cuối khóa các bạn sẽ xây dựng và hiểu toán của các agent biết học, và chọn đúng phương pháp AI cho nhiều bài toán."),
    (67, "Buổi sau chúng ta sẽ bắt đầu với chủ đề đầu tiên là search, tức tìm kiếm trong không gian trạng thái."),
]
dump("bilingual_cs188_eval.json", {
    "note": "Tập tự dựng: 30 câu giảng tiếng Việt viết tay cho slide tiếng Anh cs188_sp24_lec01.pdf; không phải thu âm thật.",
    "slide_pdf": "dataset_eval/slides/cs188_sp24_lec01.pdf",
    "items": [{"id": i + 1, "text": t, "gold_slide": s} for i, (s, t) in enumerate(bilingual)],
})

# ---------------------------------------------------------------- 2. Non-monotonic T-SQL
seq = []
def add(slide, texts, inserted=False):
    for t in texts:
        seq.append({"text": t, "gold_slide": slide, "inserted_backjump": inserted})

add(6, [
    "Cách thứ hai để gán giá trị cho biến là dùng lệnh SELECT, SELECT @HoTen bằng chuỗi Nguyễn Hằng Nga.",
    "Kết quả giống hệt dùng SET, chỉ khác là lần này ta gán bằng câu lệnh SELECT.",
    "Các em nhớ khai báo biến HoTen kiểu nvarchar hai mươi trước khi gán.",
])
add(7, [
    "Cách thứ ba là gán từ dữ liệu trong bảng, SELECT biến bằng tên cột FROM tên bảng.",
    "Ví dụ tìm mức lương lớn nhất của tất cả nhân viên, ta dùng hàm MAX trên cột Luong của bảng NhanVien.",
    "Biến MaxSalary kiểu decimal sẽ nhận giá trị lương cao nhất đó.",
])
add(8, [
    "Bây giờ sang ví dụ về biến kiểu ngày tháng, khai báo biến date rồi SET bằng ngày mười sáu tháng tư năm 2019.",
    "Ta cũng có thể gán luôn lúc khai báo, curent_date bằng hàm getdate lấy ngày hiện tại.",
    "Biến duedate bằng getdate cộng hai, nghĩa là hạn nộp sau ngày hiện tại hai ngày.",
    "Cuối cùng SELECT ba biến này với các bí danh Date, Curent date và Due date để xem kết quả.",
])
add(3, [
    "À nhắc lại một chút, quay lại khái niệm khối lệnh, T-SQL tổ chức theo từng khối lệnh.",
    "Một khối lệnh bắt đầu bởi BEGIN và kết thúc bởi END, các em nhớ đóng khối bằng END.",
    "Khối lệnh có thể lồng bên trong một khối lệnh khác, và các lệnh ngăn cách nhau bởi dấu chấm phẩy.",
    "Bên trong BEGIN END thường có phần khai báo biến rồi mới đến các câu lệnh T-SQL.",
    "Đó là cấu trúc chung của một khối lệnh, nhớ chấm phẩy cuối mỗi lệnh nhé.",
], inserted=True)
add(8, [
    "Rồi, quay lại ví dụ ngày tháng ban nãy, các em chạy thử đoạn SELECT ba biến ngày.",
    "Các em thấy cột Due date lớn hơn Curent date đúng hai ngày.",
])
add(9, [
    "Tiếp theo là biến kiểu table, khai báo TableVariable gồm course_name và course_price.",
    "Ta INSERT INTO biến bảng này dữ liệu SELECT name, price từ bảng Training_Course với id là một và hai.",
    "Sau đó SELECT course_name, course_price từ biến bảng để xem kết quả.",
])
dump("nonmonotonic_tsql_eval.json", {
    "note": "Chuỗi tự dựng: slide 6->7->8, chèn 5 câu quay ngược Slide 3 (BEGIN...END), rồi quay lại 8->9.",
    "slide_pdf": "dataset_eval/my_test/TSQL_Lecture09.pdf",
    "items": [dict(id=i + 1, start_sec=round(i * 12.0, 1), end_sec=round(i * 12.0 + 10.0, 1), **x) for i, x in enumerate(seq)],
})

# ---------------------------------------------------------------- 3. Block eval
blocks = [
    (3, "Khối lệnh bắt đầu và kết thúc", "Một khối lệnh trong T-SQL bắt đầu bởi BEGIN và kết thúc bởi END, các lệnh cách nhau bằng dấu chấm phẩy.", "T-SQL tổ chức theo từng khối lệnh"),
    (3, "Cấu trúc BEGIN END mẫu", "Cấu trúc mẫu gồm BEGIN, phần khai báo biến, các câu lệnh T-SQL rồi END.", "Cấu trúc:"),
    (4, "Tên biến bắt đầu bằng @", "Mọi tên biến trong T-SQL đều phải bắt đầu bằng ký tự @, chẳng hạn @Tuoi.", "Tên biến:"),
    (4, "Kiểu dữ liệu của biến", "Biến dùng kiểu dữ liệu hệ thống, nhưng không được dùng kiểu text, ntext và image.", "Kiểu dữ liệu của biến"),
    (4, "Ví dụ khai báo biến", "Ví dụ khai báo biến Tuoi kiểu int, MSSV kiểu varchar năm ký tự và numCount kiểu int.", "DECLARE @Tuoi int"),
    (5, "Gán bằng SET", "Cách một là dùng lệnh SET, SET tên biến bằng giá trị.", "Cách 1"),
    (6, "Gán bằng SELECT", "Cách hai là dùng SELECT, SELECT tên biến bằng giá trị.", "Cách 2"),
    (7, "Gán từ dữ liệu bảng", "Cách ba là SELECT tên biến bằng tên cột FROM tên bảng để lấy dữ liệu từ bảng.", "Cách 3"),
    (7, "Tìm lương lớn nhất", "Ví dụ dùng hàm MAX trên cột Luong để tìm lương lớn nhất của nhân viên.", "Ví dụ: Tìm lương"),
    (8, "Gán ngày cố định", "SET biến date bằng ngày mười sáu tháng tư năm 2019.", "SET @date"),
    (8, "Ngày hiện tại bằng getdate", "Biến curent_date lấy ngày hiện tại bằng hàm getdate.", "@curent_date date"),
    (8, "Hạn nộp cộng hai ngày", "Biến duedate bằng getdate cộng hai, tức là sau hai ngày.", "@duedate date"),
    (9, "Khai báo biến table", "Khai báo biến kiểu table gồm hai cột course_name và course_price.", "DECLARE @TableVariable table"),
    (9, "Chèn dữ liệu vào biến table", "Dùng INSERT INTO biến bảng rồi SELECT name, price từ Training_Course.", "INSERT INTO"),
    (9, "Truy vấn biến table", "Cuối cùng SELECT course_name, course_price FROM biến bảng để xem kết quả.", "SELECT course_name, course_price FROM"),
]
dump("block_tsql_eval.json", {
    "note": "Gold = chuỗi con xuất hiện trong đúng một block; block_id theo quy ước Laravel slide_{N}_b{i}.",
    "slide_pdf": "dataset_eval/my_test/TSQL_Lecture09.pdf",
    "items": [dict(id=i + 1, slide=s, headline=h, content=c, gold_block_substring=g) for i, (s, h, c, g) in enumerate(blocks)],
})
print("OK: wrote 3 files")
