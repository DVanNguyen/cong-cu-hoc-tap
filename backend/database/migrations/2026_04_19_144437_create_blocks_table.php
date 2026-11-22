<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('blocks', function (Blueprint $table) {
            $table->uuid('id')->primary();

            $table->uuid('page_id');
            $table->foreign('page_id')
                  ->references('id')
                  ->on('pages')
                  ->cascadeOnDelete();

            // Loại block: H1|H2|TEXT|TODO|IMAGE|CODE|DIVIDER
            $table->string('type', 20);

            // Nội dung chính — dùng text để chứa nội dung dài
            $table->text('content')->nullable();

            // Thứ tự hiển thị trong page
            $table->integer('position')->default(0);

            // Chỉ dùng cho TODO block
            $table->boolean('is_checked')->default(false);

            // Metadata mở rộng JSON: màu sắc, ngôn ngữ code, caption ảnh...
            $table->json('metadata')->nullable();

            $table->bigInteger('updated_at_ms')->default(0);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();

            // Index quan trọng nhất: lấy blocks theo page + thứ tự
            $table->index(['page_id', 'position']);
            $table->index(['page_id', 'is_deleted']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('blocks');
    }
};