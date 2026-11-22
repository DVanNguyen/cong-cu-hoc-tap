<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     * 
     * ✅ FocusBoard v4.0 Offline-First Architecture:
     * - UUID Primary Key (NOT auto-increment)
     * - UUID Foreign Keys
     * - Sync metadata (updated_at_ms, is_deleted)
     */
    public function up(): void
    {
        Schema::create('tasks', function (Blueprint $table) {
            // ✅ UUID Primary Key
            $table->uuid('id')->primary();

            // ✅ UUID Foreign Keys
            $table->uuid('workspace_id');
            $table->uuid('user_id');
            $table->foreign('workspace_id')->references('id')->on('workspaces')->onDelete('cascade');
            $table->foreign('user_id')->references('id')->on('users')->onDelete('cascade');

            // ✅ Task metadata
            $table->string('title');
            $table->text('description')->nullable();
            $table->string('tag')->nullable();
            $table->timestamp('deadline')->nullable();
            $table->boolean('completed')->default(false);
            $table->integer('palette_idx')->default(0);
            $table->boolean('important')->default(false);

            // ✅ CRITICAL: Sync & Conflict Resolution
            $table->bigInteger('updated_at_ms')->default(0);
            $table->boolean('is_deleted')->default(false);

            // ✅ Standard timestamps
            $table->timestamps();

            // ✅ Indices for queries
            $table->index('workspace_id');
            $table->index('user_id');
            $table->index(['is_deleted', 'updated_at_ms']);
            $table->index('deadline');
            $table->index('completed');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('tasks');
    }
};
