<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('pages', function (Blueprint $table) {
            $table->uuid('id')->primary();

            // FK đến workspaces — xoá workspace thì xoá hết page
            $table->uuid('workspace_id');
            $table->foreign('workspace_id')
                  ->references('id')
                  ->on('workspaces')
                  ->cascadeOnDelete();

            $table->string('title')->default('Trang không có tiêu đề');
            $table->string('icon_emoji', 10)->default('📄');
            $table->boolean('is_pinned')->default(false);
            $table->integer('position')->default(0);
            $table->bigInteger('updated_at_ms')->default(0);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();

            // Index để query nhanh theo workspace
            $table->index('workspace_id');
            $table->index(['workspace_id', 'is_deleted']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('pages');
    }
};