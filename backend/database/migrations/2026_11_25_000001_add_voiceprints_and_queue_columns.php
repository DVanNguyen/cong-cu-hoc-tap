<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        // 1. Bổ sung ngôn ngữ bài giảng và metadata cho bảng lectures
        Schema::table('lectures', function (Blueprint $table) {
            if (!Schema::hasColumn('lectures', 'language_mode')) {
                $table->string('language_mode', 10)->default('auto')->after('title');
            }
            if (!Schema::hasColumn('lectures', 'processing_status')) {
                $table->string('processing_status', 40)->default('queued')->after('duration_sec');
            }
            if (!Schema::hasColumn('lectures', 'progress_percent')) {
                $table->unsignedTinyInteger('progress_percent')->default(0)->after('processing_status');
            }
            if (!Schema::hasColumn('lectures', 'error_message')) {
                $table->text('error_message')->nullable()->after('progress_percent');
            }
        });

        // 2. Tạo sẵn bảng lưu vector giọng giảng viên (192 chiều từ ECAPA-TDNN) dùng cho Tuần 2
        if (!Schema::hasTable('lecturer_voiceprints')) {
            Schema::create('lecturer_voiceprints', function (Blueprint $table) {
                $table->id();
                $table->foreignId('course_id')->constrained('courses')->cascadeOnDelete();
                $table->string('lecturer_name', 120);
                $table->string('sample_audio_path');
                $table->json('embedding_vector'); // Mảng 192 số thực (float) đã chuẩn hóa L2
                $table->float('cosine_threshold')->default(0.38);
                $table->timestamps();
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('lecturer_voiceprints');
        Schema::table('lectures', function (Blueprint $table) {
            if (Schema::hasColumn('lectures', 'error_message')) {
                $table->dropColumn('error_message');
            }
            if (Schema::hasColumn('lectures', 'progress_percent')) {
                $table->dropColumn('progress_percent');
            }
            if (Schema::hasColumn('lectures', 'processing_status')) {
                $table->dropColumn('processing_status');
            }
            if (Schema::hasColumn('lectures', 'language_mode')) {
                $table->dropColumn('language_mode');
            }
        });
    }
};
