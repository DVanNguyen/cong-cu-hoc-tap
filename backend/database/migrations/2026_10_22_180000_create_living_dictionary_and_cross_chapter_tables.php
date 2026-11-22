<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     * Cập nhật CSDL theo định hướng mới của TS. Trần Uyên Trang:
     * - Bổ sung bảng concept_occurrences: Vết xuất hiện & liên kết chéo liên chương (Ch.1 -> Ch.5).
     * - Bổ sung bảng dictionary_entries: Từ điển sống giải thích sâu cho khái niệm khó trên khung sườn slide.
     * - Bổ sung bảng external_references: Lưu tài liệu tham khảo mở rộng từ Wikipedia, Semantic Scholar, Web.
     */
    public function up(): void
    {
        // 1. Bảng concept_occurrences: Lưu mọi vị trí xuất hiện của Khái niệm xuyên suốt các Chương/Bài
        if (!Schema::hasTable('concept_occurrences')) {
            Schema::create('concept_occurrences', function (Blueprint $table) {
                $table->id();
                $table->unsignedBigInteger('concept_id');
                $table->unsignedBigInteger('lecture_id');
                $table->unsignedBigInteger('slide_id');
                $table->unsignedBigInteger('transcript_segment_id')->nullable();
                $table->enum('occurrence_role', ['DEFINED_HERE', 'ELABORATED_HERE', 'REFERENCED_BACK'])->default('DEFINED_HERE');
                $table->integer('pos_linear_index')->index(); // Pos(L, S) = lecture_order * 1000 + slide_number
                $table->text('context_excerpt');              // Đoạn trích dẫn chứa khái niệm
                $table->float('timestamp_sec')->nullable();   // Mốc giây âm thanh nếu giảng viên nói tới
                $table->timestamp('created_at')->useCurrent();

                $table->foreign('concept_id')->references('id')->on('concepts')->onDelete('cascade');
                $table->foreign('lecture_id')->references('id')->on('lectures')->onDelete('cascade');
                $table->foreign('slide_id')->references('id')->on('slides')->onDelete('cascade');
            });
        }

        // 2. Bảng dictionary_entries: Bảng "Từ điển sống" cho các khái niệm khó/trừu tượng
        if (!Schema::hasTable('dictionary_entries')) {
            Schema::create('dictionary_entries', function (Blueprint $table) {
                $table->id();
                $table->unsignedBigInteger('concept_id')->unique();
                $table->float('abstractness_score')->default(0.5); // Điểm độ khó/trừu tượng [0.0 - 1.0]
                $table->text('slide_skeleton_context')->nullable(); // Ngữ cảnh cô đọng trên khung sườn slide
                $table->text('lecturer_oral_explanation')->nullable(); // Tổng hợp những gì giảng viên đã giải thích trên lớp
                $table->text('ai_deep_explanation');                // Giải thích trực quan, đào sâu bản chất
                $table->text('intuition_example')->nullable();       // Ví dụ thực tế dễ hiểu cho sinh viên
                $table->timestamp('updated_at')->useCurrent()->useCurrentOnUpdate();

                $table->foreign('concept_id')->references('id')->on('concepts')->onDelete('cascade');
            });
        }

        // 3. Bảng external_references: Lưu các Liên kết Tài liệu Tham khảo Mở rộng từ API bên ngoài
        if (!Schema::hasTable('external_references')) {
            Schema::create('external_references', function (Blueprint $table) {
                $table->id();
                $table->unsignedBigInteger('concept_id');
                $table->unsignedBigInteger('slide_id')->nullable(); // Trang slide nơi khái niệm này xuất hiện
                $table->string('title', 500);                       // Tiêu đề bài viết / bài báo / video
                $table->string('url', 1000);                        // Đường link tham khảo trực tiếp
                $table->enum('source_provider', ['WIKIPEDIA', 'SEMANTIC_SCHOLAR', 'WEB_TUTORIAL', 'YOUTUBE_VIDEO']);
                $table->string('domain_name', 255);                 // arxiv.org, geeksforgeeks.org, viblo.asia,...
                $table->text('snippet_summary');                    // Tóm tắt 2 câu vì sao tài liệu này giúp hiểu khái niệm
                $table->float('context_similarity');                // Độ khớp ngữ nghĩa với ngữ cảnh môn học [0.0 - 1.0]
                $table->float('authority_weight');                  // Độ uy tín của tên miền nguồn [0.0 - 1.0]
                $table->float('final_rank_score');                  // Điểm xếp hạng tổng hợp để hiển thị lên đầu
                $table->string('citation_badge', 100);              // "[Paper Học thuật]", "[Wiki]", "[Hướng dẫn & Code]"
                $table->timestamp('created_at')->useCurrent();

                $table->foreign('concept_id')->references('id')->on('concepts')->onDelete('cascade');
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('external_references');
        Schema::dropIfExists('dictionary_entries');
        Schema::dropIfExists('concept_occurrences');
    }
};
