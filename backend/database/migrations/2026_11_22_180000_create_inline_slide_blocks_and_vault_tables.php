<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        // 1. Cập nhật bảng COURSES: thêm lecturer và cho phép user_id nullable
        Schema::table('courses', function (Blueprint $table) {
            if (!Schema::hasColumn('courses', 'lecturer')) {
                $table->string('lecturer')->nullable()->after('name');
            }
            if (Schema::hasColumn('courses', 'user_id')) {
                $table->uuid('user_id')->nullable()->change();
            }
        });

        // 2. Bảng course_slide_vaults (Kho chứa slide upload trước theo từng môn)
        if (!Schema::hasTable('course_slide_vaults')) {
            Schema::create('course_slide_vaults', function (Blueprint $table) {
                $table->id();
                $table->foreignId('course_id')->constrained('courses')->cascadeOnDelete();
                $table->string('file_name');
                $table->string('file_path', 500);
                $table->string('mime_type')->default('application/pdf');
                $table->integer('page_count')->default(0);
                $table->longText('parsed_blocks_json')->nullable();
                $table->timestamps();
            });
        }

        // 3. Bảng lecture_slide_blocks (Bản sao từng ý của slide trong một vở ghi chú)
        if (!Schema::hasTable('lecture_slide_blocks')) {
            Schema::create('lecture_slide_blocks', function (Blueprint $table) {
                $table->id();
                $table->foreignId('lecture_id')->constrained('lectures')->cascadeOnDelete();
                $table->integer('slide_page');
                $table->integer('block_index');
                $table->string('block_id', 64)->index();
                $table->string('kind', 32)->default('bullet'); // heading | bullet | formula | code
                $table->longText('original_slide_text');
                $table->boolean('is_confused')->default(false);
                $table->double('last_spoken_sec')->nullable();
                $table->timestamps();

                $table->index(['lecture_id', 'slide_page']);
            });
        }

        // 4. Bảng inline_block_notes (Ghi chú phân tích của giảng viên gắn trực tiếp vào từng block_id)
        if (!Schema::hasTable('inline_block_notes')) {
            Schema::create('inline_block_notes', function (Blueprint $table) {
                $table->id();
                $table->foreignId('lecture_id')->constrained('lectures')->cascadeOnDelete();
                $table->string('block_id', 64)->nullable()->index(); // null = general_note
                $table->longText('explanation_text');
                $table->longText('raw_transcript')->nullable();
                $table->double('timestamp_start_sec')->default(0.0);
                $table->double('timestamp_end_sec')->default(0.0);
                $table->double('novelty_score')->default(0.0);
                $table->boolean('jumped_non_linear')->default(false);
                $table->timestamps();
            });
        }

        // 5. Bảng block_concept_highlights (Từ khóa highlight 2 màu nằm ngay trong từng block)
        if (!Schema::hasTable('block_concept_highlights')) {
            Schema::create('block_concept_highlights', function (Blueprint $table) {
                $table->id();
                $table->foreignId('lecture_id')->constrained('lectures')->cascadeOnDelete();
                $table->string('block_id', 64)->index();
                $table->string('term');
                $table->string('source_type', 32); // previous_lecture | academic_internet
                $table->string('source_label');
                $table->longText('explanation');
                $table->unsignedBigInteger('course_context_id')->nullable();
                $table->longText('alternative_contexts_json')->nullable();
                $table->timestamps();
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('block_concept_highlights');
        Schema::dropIfExists('inline_block_notes');
        Schema::dropIfExists('lecture_slide_blocks');
        Schema::dropIfExists('course_slide_vaults');

        Schema::table('courses', function (Blueprint $table) {
            if (Schema::hasColumn('courses', 'lecturer')) {
                $table->dropColumn('lecturer');
            }
        });
    }
};
