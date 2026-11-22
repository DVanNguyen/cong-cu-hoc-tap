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
        Schema::dropIfExists('raw_items');

        // 1. Raw Items Table (Dữ liệu thô thu thập từ Email, Telegram, Lời nói)
        Schema::create('raw_items', function (Blueprint $table) {
            $table->id();
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->enum('source', ['email', 'telegram', 'speech', 'manual'])->default('telegram');
            $table->string('external_id')->nullable();
            $table->dateTime('received_at');
            $table->longText('text_content');
            $table->char('hash', 64)->unique(); // Đảm bảo tính idempotent, không bị trùng tin
            $table->enum('status', ['pending', 'processed', 'ignored'])->default('pending');
            $table->timestamps();
        });

        // 2. Thêm các trường phục vụ AI & Quản lý Deadline vào bảng tasks
        Schema::table('tasks', function (Blueprint $table) {
            $table->foreignId('raw_item_id')->nullable()->constrained('raw_items')->nullOnDelete();
            $table->foreignId('course_id')->nullable()->constrained('courses')->nullOnDelete();
            $table->float('confidence')->default(1.0);
            $table->enum('intent', ['create', 'reschedule', 'cancel'])->default('create');
            $table->integer('effort_est_min')->default(60);
            $table->float('load_ratio')->default(0.0);
            $table->text('evidence_quote')->nullable();
            $table->boolean('needs_confirmation')->default(false);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('tasks', function (Blueprint $table) {
            $table->dropForeign(['raw_item_id']);
            $table->dropForeign(['course_id']);
            $table->dropColumn([
                'raw_item_id',
                'course_id',
                'confidence',
                'intent',
                'effort_est_min',
                'load_ratio',
                'evidence_quote',
                'needs_confirmation'
            ]);
        });

        Schema::dropIfExists('raw_items');
    }
};
