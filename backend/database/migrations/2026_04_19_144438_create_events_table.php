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
        Schema::create('events', function (Blueprint $table) {
            // ✅ UUID Primary Key
            $table->uuid('id')->primary();

            // ✅ UUID Foreign Key
            $table->uuid('user_id');
            $table->foreign('user_id')->references('id')->on('users')->onDelete('cascade');

            $table->string('title');
            $table->string('event_date')->nullable(); // YYYY-MM-DD
            $table->string('location')->nullable();
            $table->integer('day_of_week'); // 0-4 (T2-T6)
            $table->integer('start_hour'); // 7-18
            $table->integer('end_hour');
            $table->string('tag')->nullable();
            $table->integer('palette_idx')->default(0); // 0-4
            $table->boolean('is_all_day')->default(false);

            // ✅ CRITICAL: Sync & Conflict Resolution
            $table->bigInteger('updated_at_ms')->default(0);
            $table->boolean('is_deleted')->default(false);

            // ✅ Standard timestamps
            $table->timestamps();

            // ✅ Indices
            $table->index('user_id');
            $table->index('day_of_week');
            $table->index(['is_deleted', 'updated_at_ms']);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('events');
    }
};
