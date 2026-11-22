<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

// php artisan make:migration create_workspaces_table
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('workspaces', function (Blueprint $table) {
            // ID dùng UUID từ Android gửi lên — không auto-increment
            $table->uuid('id')->primary();

            // FK đến bảng users của Laravel — UUID type
            $table->uuid('user_id');
            $table->foreign('user_id')
                  ->references('id')
                  ->on('users')
                  ->cascadeOnDelete();

            $table->string('name');
            $table->string('icon_emoji', 10)->default('📋');
            $table->string('color', 10)->default('#1A56DB');
            $table->integer('position')->default(0);

            // Dùng bigInteger để lưu Unix timestamp milliseconds từ Android
            $table->bigInteger('updated_at_ms')->default(0);

            // Soft delete phía server: khi Android xoá, gửi is_deleted=true
            $table->boolean('is_deleted')->default(false);

            // Laravel timestamps (created_at, updated_at) — dùng thêm cho server
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('workspaces');
    }
};