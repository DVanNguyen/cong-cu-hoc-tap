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
        Schema::dropIfExists('note_items');
        Schema::dropIfExists('slides');
        Schema::dropIfExists('lectures');
        Schema::dropIfExists('courses');

        // 1. Courses Table
        Schema::create('courses', function (Blueprint $table) {
            $table->id();
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->string('code')->nullable();
            $table->string('name');
            $table->text('description')->nullable();
            $table->timestamps();
        });

        // 2. Lectures Table
        Schema::create('lectures', function (Blueprint $table) {
            $table->id();
            $table->foreignId('course_id')->constrained()->cascadeOnDelete();
            $table->string('title');
            $table->string('audio_path')->nullable();
            $table->string('slide_path')->nullable();
            $table->integer('duration_sec')->default(0);
            $table->enum('status', ['uploaded', 'processing_asr', 'aligning_slides', 'completed', 'failed'])->default('uploaded');
            $table->text('error_message')->nullable();
            $table->timestamps();
        });

        // 3. Slides Table
        Schema::create('slides', function (Blueprint $table) {
            $table->id();
            $table->foreignId('lecture_id')->constrained()->cascadeOnDelete();
            $table->integer('page_number');
            $table->longText('slide_text')->nullable();
            $table->text('summary')->nullable();
            $table->timestamps();

            $table->unique(['lecture_id', 'page_number']);
        });

        // 4. Note Items Table (Chỉ phần giảng viên nói thêm + giải thích thuật ngữ)
        Schema::create('note_items', function (Blueprint $table) {
            $table->id();
            $table->foreignId('slide_id')->constrained()->cascadeOnDelete();
            $table->text('content');
            $table->integer('timestamp_start_sec')->default(0);
            $table->integer('timestamp_end_sec')->default(0);
            $table->enum('type', ['addition', 'term', 'exam_hint'])->default('addition');
            $table->float('confidence')->default(1.0);
            $table->string('term_original')->nullable();
            $table->string('term_vietnamese')->nullable();
            $table->timestamps();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('note_items');
        Schema::dropIfExists('slides');
        Schema::dropIfExists('lectures');
        Schema::dropIfExists('courses');
    }
};
