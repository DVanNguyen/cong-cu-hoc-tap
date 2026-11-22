<?php
/**
 * Seed Living Dictionary Entries, External References & Cross-Chapter Occurrences
 * vào CSDL MySQL 'focusboard' theo định hướng mới của TS. Trần Uyên Trang.
 */
require 'vendor/autoload.php';
$app = require_once 'bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

use Illuminate\Support\Facades\DB;

echo "=== SEEDING LIVING DICTIONARY & CROSS-CHAPTER DEEP LINKS ===" . PHP_EOL;

$courseId = 1;

// 1. Tạo thêm Lectures nếu chưa có đủ 5 Chapters
$lectureTitles = [
    1 => 'Chapter 1: Tổng quan về Trí tuệ Nhân tạo & Tác tử Thông minh',
    2 => 'Chapter 2: Không gian Trạng thái & Tìm kiếm Mù (BFS, DFS, UCS)',
    3 => 'Chapter 3: Tìm kiếm có Thông tin (Heuristic Search & A*)',
    4 => 'Chapter 4: Tìm kiếm Cục bộ (Hill Climbing & Simulated Annealing)',
    5 => 'Chapter 5: Mô hình Sinh Sâu (Deep Generative Models & GAN)',
];

foreach ($lectureTitles as $order => $title) {
    $existing = DB::table('lectures')->where('id', $order)->first();
    if (!$existing) {
        DB::table('lectures')->insert([
            'id' => $order,
            'course_id' => $courseId,
            'title' => $title,
            'lecture_date' => date('Y-m-d', strtotime("+" . (($order - 1) * 7) . " days")),
            'total_duration_sec' => 5400.0,
            'status' => 'COMPLETED',
            'created_at' => now(),
            'updated_at' => now(),
        ]);
    } else {
        DB::table('lectures')->where('id', $order)->update(['title' => $title]);
    }

    // Đảm bảo mỗi Lecture có tối thiểu 20 trang slide và slide_number khớp với page_number
    DB::statement("UPDATE slides SET slide_number = page_number WHERE lecture_id = ? AND (slide_number != page_number OR slide_number IS NULL)", [$order]);
    $slideCount = DB::table('slides')->where('lecture_id', $order)->count();
    if ($slideCount < 20) {
        for ($s = 1; $s <= 25; $s++) {
            $existingSlide = DB::table('slides')->where('lecture_id', $order)->where('slide_number', $s)->first();
            if (!$existingSlide) {
                DB::table('slides')->insert([
                    'lecture_id' => $order,
                    'slide_number' => $s,
                    'page_number' => $s,
                    'title' => "Slide {$s}: {$title}",
                    'raw_text' => "Nội dung slide {$s} của bài giảng {$title}. Giới thiệu các khái niệm cốt lõi, công thức toán và sơ đồ giải thuật.",
                    'extracted_terms' => json_encode(["AI", "Lecture {$order}", "Slide {$s}"], JSON_UNESCAPED_UNICODE),
                    'created_at' => now(),
                    'updated_at' => now(),
                ]);
            }
        }
    }
}

// 2. Lấy danh sách các khái niệm trong DB
$concepts = DB::table('concepts')->where('course_id', $courseId)->get()->keyBy('name');

// Thêm khái niệm GAN nếu chưa có
if (!$concepts->has('Generative Adversarial Network (GAN)')) {
    $ganId = DB::table('concepts')->insertGetId([
        'course_id' => $courseId,
        'canonical_name' => 'Mạng đối nghịch tạo sinh (Generative Adversarial Network - GAN)',
        'name' => 'Generative Adversarial Network (GAN)',
        'aliases' => json_encode(['GAN', 'Mạng GAN', 'Generative Adversarial Networks'], JSON_UNESCAPED_UNICODE),
        'definition_summary' => 'Kiến trúc học sâu gồm 2 mạng Generator và Discriminator đối kháng nhau để sinh dữ liệu nhân tạo chân thực.',
        'first_defined_lecture_order' => 1,
        'first_defined_slide_id' => 12,
        'first_used_lecture_order' => 1,
        'first_used_slide_id' => 12,
        'is_used_before_defined' => 0,
        'created_at' => now(),
        'updated_at' => now(),
    ]);
    $concepts = DB::table('concepts')->where('course_id', $courseId)->get()->keyBy('name');
}

// Xóa dữ liệu cũ của 3 bảng mới
DB::table('external_references')->delete();
DB::table('dictionary_entries')->delete();
DB::table('concept_occurrences')->delete();

// =========================================================================
// 3. SEED BẢNG concept_occurrences: Vết xuất hiện & Liên kết chéo liên chương
// =========================================================================
$firstSlide = DB::table('slides')->where('lecture_id', 1)->first();
$slide1Id = $firstSlide ? $firstSlide->id : 1;

$occurrencesData = [
    // 1. Mạng GAN: Xuất hiện ở Chapter 1 (Slide 12), nhắc lại chuyên sâu ở Chapter 5 (Slide 8)
    [
        'concept_name' => 'Generative Adversarial Network (GAN)',
        'lecture_id' => 1, 'lecture_order' => 1, 'slide_id' => $slide1Id, 'slide_no' => 12,
        'role' => 'DEFINED_HERE', 'sec' => 255.0,
        'excerpt' => 'Giới thiệu sơ lược các đột phá của AI: Mô hình Mạng đối nghịch tạo sinh GAN sinh ảnh chân thực.'
    ],
    [
        'concept_name' => 'Generative Adversarial Network (GAN)',
        'lecture_id' => 5, 'lecture_order' => 5, 'slide_id' => $slide1Id, 'slide_no' => 8,
        'role' => 'REFERENCED_BACK', 'sec' => 612.0,
        'excerpt' => 'Ứng dụng cấu trúc mạng GAN mà chúng ta đã làm quen ở Chương 1 để sinh ảnh người nhân tạo độ nét cao.'
    ],

    // 2. Thuật toán A*: Định nghĩa ở Chapter 3 (Slide 6), nhưng được nhắc ở Chapter 1 và dùng lại ở Chapter 5
    [
        'concept_name' => 'A* Search',
        'lecture_id' => 1, 'lecture_order' => 1, 'slide_id' => $slide1Id, 'slide_no' => 2,
        'role' => 'REFERENCED_BACK', 'sec' => 30.0,
        'excerpt' => 'Trong thực tế, xe tự hành có thể dùng thuật toán A* để tìm đường đi ngắn nhất.'
    ],
    [
        'concept_name' => 'A* Search',
        'lecture_id' => 3, 'lecture_order' => 3, 'slide_id' => $slide1Id, 'slide_no' => 6,
        'role' => 'DEFINED_HERE', 'sec' => 225.0,
        'excerpt' => 'Thuật toán A* kết hợp f(n) = g(n) + h(n), bảo đảm tìm ra đường đi tối ưu với heuristic admissible.'
    ],
    [
        'concept_name' => 'A* Search',
        'lecture_id' => 5, 'lecture_order' => 5, 'slide_id' => $slide1Id, 'slide_no' => 14,
        'role' => 'REFERENCED_BACK', 'sec' => 920.0,
        'excerpt' => 'So sánh phương pháp lập kế hoạch cổ điển bằng A* với phương pháp sinh đường đi bằng mạng nơ-ron học sâu.'
    ],

    // 3. Tác tử thông minh (Intelligent Agent): Định nghĩa ở Chapter 1 (Slide 1), nhắc lại ở Chapter 5 (Slide 1)
    [
        'concept_name' => 'Intelligent Agent',
        'lecture_id' => 1, 'lecture_order' => 1, 'slide_id' => $slide1Id, 'slide_no' => 1,
        'role' => 'DEFINED_HERE', 'sec' => 15.0,
        'excerpt' => 'Hôm nay chúng ta bắt đầu tìm hiểu về Intelligent Agent trong hệ thống trí tuệ nhân tạo.'
    ],
    [
        'concept_name' => 'Intelligent Agent',
        'lecture_id' => 5, 'lecture_order' => 5, 'slide_id' => $slide1Id, 'slide_no' => 1,
        'role' => 'REFERENCED_BACK', 'sec' => 45.0,
        'excerpt' => 'Tích hợp các mô hình học sâu hiện đại vào bên trong chu trình nhận thức - hành động của Intelligent Agent.'
    ],

    // 4. Khuôn mẫu PEAS: Định nghĩa ở Chapter 1 (Slide 6), nhắc lại ở Chapter 2 (Slide 3)
    [
        'concept_name' => 'PEAS Framework',
        'lecture_id' => 1, 'lecture_order' => 1, 'slide_id' => $slide1Id, 'slide_no' => 6,
        'role' => 'DEFINED_HERE', 'sec' => 75.0,
        'excerpt' => 'Mô hình PEAS bao gồm Performance measure, Environment, Actuators, Sensors.'
    ],
    [
        'concept_name' => 'PEAS Framework',
        'lecture_id' => 2, 'lecture_order' => 2, 'slide_id' => $slide1Id, 'slide_no' => 3,
        'role' => 'REFERENCED_BACK', 'sec' => 110.0,
        'excerpt' => 'Từ bảng đặc tả PEAS của bài trước, ta xác định được không gian bài toán và tập trạng thái cần tìm kiếm.'
    ],
];

$occCount = 0;
foreach ($occurrencesData as $occ) {
    $c = $concepts->get($occ['concept_name']);
    if (!$c) continue;

    $matchedSlide = DB::table('slides')
        ->where('lecture_id', $occ['lecture_id'])
        ->where('slide_number', $occ['slide_no'])
        ->first();
    $realSlideId = $matchedSlide ? $matchedSlide->id : $slide1Id;

    DB::table('concept_occurrences')->insert([
        'concept_id' => $c->id,
        'lecture_id' => $occ['lecture_id'],
        'slide_id' => $realSlideId,
        'occurrence_role' => $occ['role'],
        'pos_linear_index' => $occ['lecture_order'] * 1000 + $occ['slide_no'],
        'context_excerpt' => $occ['excerpt'],
        'timestamp_sec' => $occ['sec'],
        'created_at' => now(),
    ]);
    $occCount++;
}
echo " [+] Đã nạp {$occCount} vết xuất hiện liên chương (phục vụ Deep-Linking 2 chiều)." . PHP_EOL;

// =========================================================================
// 4. SEED BẢNG dictionary_entries: Từ điển sống giải thích đào sâu
// =========================================================================
$dictionarySeed = [
    [
        'name' => 'Generative Adversarial Network (GAN)',
        'abstractness' => 0.88,
        'skeleton' => '• Generative Adversarial Networks (GAN). • Generator G vs Discriminator D.',
        'oral' => 'Giảng viên giảng giải: GAN hoạt động như một cuộc đấu trí giữa kẻ làm tiền giả (Generator) và cảnh sát (Discriminator). Càng đấu thì tiền giả càng giống thật.',
        'deep' => 'Mạng đối nghịch tạo sinh (GAN) là kiến trúc học máy đột phá do Ian Goodfellow đề xuất năm 2014. Thay vì tối ưu hàm mất mát truyền thống, GAN thiết lập một trò chơi minimax 2 người chơi giữa Generator sinh mẫu và Discriminator phân biệt mẫu thật/giả.',
        'example' => 'Ứng dụng sinh khuôn mặt người chưa từng tồn tại trên trang ThisPersonDoesNotExist.com hoặc nâng độ phân giải ảnh siêu nét.'
    ],
    [
        'name' => 'A* Search',
        'abstractness' => 0.75,
        'skeleton' => '• A* Search: f(n) = g(n) + h(n). • Optimal if h(n) is admissible.',
        'oral' => 'Giảng viên nhấn mạnh: A* là thuật toán kinh điển nhất trong tìm đường đi. Nó kết hợp chi phí thực tế đã đi g(n) với ước lượng thông minh h(n) để không bị đi mò mẫm vô định.',
        'deep' => 'Thuật toán A* là thuật toán tìm kiếm theo đồ thị có thông tin (Informed Search). Bằng cách kết hợp hàm chi phí từ nút gốc g(n) và hàm đánh giá heuristic tới đích h(n), A* bảo đảm tính tối ưu và hoàn chỉnh khi h(n) là hàm chấp nhận được (admissible).',
        'example' => 'Hệ thống bản đồ Google Maps hoặc thuật toán tìm đường cho nhân vật trong game tránh chướng ngại vật.'
    ],
    [
        'name' => 'Simulated Annealing',
        'abstractness' => 0.82,
        'skeleton' => '• Simulated Annealing. • Temperature T schedule. • Escape local extrema.',
        'oral' => 'Giảng viên ví von: Giống như người thợ rèn đun nóng thanh thép rồi hạ nhiệt từ từ để các nguyên tử sắp xếp tối ưu. Ở nhiệt độ cao, thuật toán chấp nhận cả bước đi xấu để nhảy ra khỏi hố cực trị cục bộ.',
        'deep' => 'Mô phỏng tôi thép là thuật toán tối ưu hóa ngẫu nhiên lấy cảm hứng từ quá trình nhiệt luyện kim loại. Tại mỗi bước, thuật toán xem xét trạng thái kế cận với xác suất chấp nhận trạng thái xấu hơn P = exp(-deltaE / T), giảm dần theo thời gian.',
        'example' => 'Bài toán xếp lịch thi đại học hoặc thiết kế vi mạch bán dẫn với hàng triệu linh kiện không bị xung đột.'
    ],
    [
        'name' => 'PEAS Framework',
        'abstractness' => 0.60,
        'skeleton' => '• PEAS: Performance, Environment, Actuators, Sensors.',
        'oral' => 'Trước khi bắt tay vào code bất kỳ hệ thống AI nào, kỹ sư phần mềm bắt buộc phải ngồi viết ra 4 thành phần PEAS để biết agent được đánh giá bằng gì, làm việc ở đâu và có những bộ phận nào.',
        'deep' => 'Khuôn mẫu PEAS là phương pháp tiếp cận chuẩn mực của Russell & Norvig để mô tả bài toán tác tử: Thước đo hiệu quả (P), Môi trường hoạt động (E), Cơ cấu tác động (A), và Thiết bị cảm biến (S).',
        'example' => 'Với xe tự hành taxi: P = an toàn, nhanh, không vi phạm luật; E = đường sá, người đi bộ, thời tiết; A = vô lăng, ga, phanh; S = camera, radar, GPS.'
    ],
];

$dictCount = 0;
foreach ($dictionarySeed as $d) {
    $c = $concepts->get($d['name']);
    if (!$c) continue;

    DB::table('dictionary_entries')->insert([
        'concept_id' => $c->id,
        'abstractness_score' => $d['abstractness'],
        'slide_skeleton_context' => $d['skeleton'],
        'lecturer_oral_explanation' => $d['oral'],
        'ai_deep_explanation' => $d['deep'],
        'intuition_example' => $d['example'],
        'updated_at' => now(),
    ]);
    $dictCount++;
}
echo " [+] Đã nạp {$dictCount} mục Từ điển sống (Living Dictionary Entries)." . PHP_EOL;

// =========================================================================
// 5. SEED BẢNG external_references: Liên kết tài liệu tham khảo bên ngoài
// =========================================================================
$referencesSeed = [
    // References for GAN
    [
        'name' => 'Generative Adversarial Network (GAN)',
        'title' => 'Generative Adversarial Nets — Công trình nghiên cứu gốc của Ian Goodfellow (2014)',
        'url' => 'https://arxiv.org/abs/1406.2661',
        'provider' => 'SEMANTIC_SCHOLAR', 'domain' => 'arxiv.org',
        'summary' => 'Bài báo khoa học nền tảng đặt nền móng cho kiến trúc GAN với hơn 50,000 trích dẫn học thuật quốc tế.',
        'sim' => 0.96, 'auth' => 1.0, 'rank' => 0.955, 'badge' => '[Paper Gốc ArXiv]'
    ],
    [
        'name' => 'Generative Adversarial Network (GAN)',
        'title' => 'Mạng đối nghịch tạo sinh — Bách khoa toàn thư Wikipedia (Tiếng Việt)',
        'url' => 'https://vi.wikipedia.org/wiki/M%E1%BA%A1ng_%C4%91%E1%BB%91i_ngh%E1%BB%8Bch_t%E1%BA%A1o_sinh',
        'provider' => 'WIKIPEDIA', 'domain' => 'wikipedia.org',
        'summary' => 'Tổng quan định nghĩa, công thức toán minimax và lịch sử phát triển của mạng GAN bằng tiếng Việt chuẩn xác.',
        'sim' => 0.92, 'auth' => 0.95, 'rank' => 0.915, 'badge' => '[Wiki VI]'
    ],
    [
        'name' => 'Generative Adversarial Network (GAN)',
        'title' => 'Hướng dẫn xây dựng mạng GAN từ đầu bằng PyTorch — Viblo AI',
        'url' => 'https://viblo.asia/p/trien-khai-mang-dcgan-sinh-chu-so-viet-tay-mnist-voi-pytorch-ORNZqV0bK0n',
        'provider' => 'WEB_TUTORIAL', 'domain' => 'viblo.asia',
        'summary' => 'Bài viết kỹ thuật tiếng Việt hướng dẫn chi tiết code Generator và Discriminator kèm loss function trực quan.',
        'sim' => 0.90, 'auth' => 0.86, 'rank' => 0.875, 'badge' => '[Hướng dẫn & Code mẫu]'
    ],
    [
        'name' => 'Generative Adversarial Network (GAN)',
        'title' => 'Video đồ họa trực quan: Cơ chế hoạt động của Generative Adversarial Networks — 3Blue1Brown',
        'url' => 'https://www.youtube.com/results?search_query=generative+adversarial+networks+explained',
        'provider' => 'YOUTUBE_VIDEO', 'domain' => 'youtube.com',
        'summary' => 'Video hoạt họa 3D giải thích trực quan nguyên lý đấu trí minimax giữa 2 mạng nơ-ron giúp người học dễ hình dung.',
        'sim' => 0.88, 'auth' => 0.82, 'rank' => 0.850, 'badge' => '[Video Trực quan]'
    ],

    // References for A* Search
    [
        'name' => 'A* Search',
        'title' => 'A Formal Basis for the Heuristic Determination of Minimum Cost Paths (Hart, Nilsson & Raphael)',
        'url' => 'https://ieeexplore.ieee.org/document/4082128',
        'provider' => 'SEMANTIC_SCHOLAR', 'domain' => 'ieee.org',
        'summary' => 'Công trình nghiên cứu kinh điển khai sinh ra thuật toán A* trên tạp chí IEEE năm 1968.',
        'sim' => 0.97, 'auth' => 1.0, 'rank' => 0.965, 'badge' => '[Paper Học thuật IEEE]'
    ],
    [
        'name' => 'A* Search',
        'title' => 'Thuật toán tìm kiếm A* — Cấu trúc dữ liệu & Mã nguồn C++/Python — GeeksforGeeks',
        'url' => 'https://www.geeksforgeeks.org/a-search-algorithm/',
        'provider' => 'WEB_TUTORIAL', 'domain' => 'geeksforgeeks.org',
        'summary' => 'Phân tích độ phức tạp thời gian O(b^d), cấu trúc hàng đợi ưu tiên và code mẫu tìm đường trên ma trận 2D.',
        'sim' => 0.93, 'auth' => 0.88, 'rank' => 0.905, 'badge' => '[Hướng dẫn & Code mẫu]'
    ],
    [
        'name' => 'A* Search',
        'title' => 'Thuật toán tìm kiếm A* — Wikipedia Tiếng Việt',
        'url' => 'https://vi.wikipedia.org/wiki/Thu%E1%BA%ADt_to%C3%A1n_t%C3%ACm_ki%E1%BA%BFm_A*',
        'provider' => 'WIKIPEDIA', 'domain' => 'wikipedia.org',
        'summary' => 'Giải thích chi tiết tính chất hàm f(n) = g(n) + h(n), tính đúng đắn và điều kiện nhất quán của hàm đánh giá.',
        'sim' => 0.94, 'auth' => 0.95, 'rank' => 0.925, 'badge' => '[Wiki VI]'
    ],
];

$refCount = 0;
foreach ($referencesSeed as $ref) {
    $c = $concepts->get($ref['name']);
    if (!$c) continue;

    DB::table('external_references')->insert([
        'concept_id' => $c->id,
        'slide_id' => $slide1Id,
        'title' => $ref['title'],
        'url' => $ref['url'],
        'source_provider' => $ref['provider'],
        'domain_name' => $ref['domain'],
        'snippet_summary' => $ref['summary'],
        'context_similarity' => $ref['sim'],
        'authority_weight' => $ref['auth'],
        'final_rank_score' => $ref['rank'],
        'citation_badge' => $ref['badge'],
        'created_at' => now(),
    ]);
    $refCount++;
}
echo " [+] Đã nạp {$refCount} liên kết tài liệu tham khảo mở rộng từ Wikipedia, IEEE, ArXiv, Viblo." . PHP_EOL;

echo "=== HOÀN TẤT SEED DỮ LIỆU TỪ ĐIỂN SỐNG & LIÊN KẾT CHÉO! ===" . PHP_EOL;
