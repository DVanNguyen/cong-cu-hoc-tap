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
        Schema::create('notes', function (Blueprint $table) {
            // ✅ UUID Primary Key
            $table->uuid('id')->primary();

            // ✅ UUID Foreign Keys
            $table->uuid('workspace_id');
            $table->uuid('user_id');
            $table->foreign('workspace_id')->references('id')->on('workspaces')->onDelete('cascade');
            $table->foreign('user_id')->references('id')->on('users')->onDelete('cascade');

            // ✅ Note metadata
            $table->string('title');
            $table->text('content');
            $table->json('blocks')->nullable();

            // ✅ CRITICAL: Sync & Conflict Resolution
            $table->bigInteger('updated_at_ms')->default(0);
            $table->boolean('is_deleted')->default(false);

            // ✅ Standard timestamps
            $table->timestamps();

            // ✅ Indices for queries
            $table->index('workspace_id');
            $table->index('user_id');
            $table->index(['is_deleted', 'updated_at_ms']);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('notes');
    }
};
