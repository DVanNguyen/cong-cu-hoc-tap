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
        // Disable foreign key constraints to drop tables cleanly
        Schema::disableForeignKeyConstraints();

        // 1. Drop redundant tables
        Schema::dropIfExists('group_invite_tokens');
        Schema::dropIfExists('group_messages');
        Schema::dropIfExists('group_members');
        Schema::dropIfExists('groups');
        Schema::dropIfExists('shared_files');
        Schema::dropIfExists('notes');

        // Re-enable foreign key constraints
        Schema::enableForeignKeyConstraints();

        // 2. Standardize events table
        Schema::table('events', function (Blueprint $table) {
            // Drop old fragmented columns
            $table->dropColumn(['event_date', 'start_hour', 'end_hour', 'is_daily']);

            // Add new timestamp columns and repeat mode
            $table->bigInteger('start_time')->nullable();
            $table->bigInteger('end_time')->nullable();
            $table->string('repeat_mode')->default('NONE');

            // Make day_of_week nullable (using change method)
            $table->integer('day_of_week')->nullable()->change();
        });

        // 3. Standardize tasks table
        Schema::table('tasks', function (Blueprint $table) {
            // Drop index first (especially for SQLite compatibility)
            $table->dropIndex('tasks_deadline_index');
            
            // Drop deadline and add due_date (BIGINT)
            $table->dropColumn('deadline');
            $table->bigInteger('due_date')->nullable();

            // Change weekly_commitment to TEXT
            $table->text('weekly_commitment')->nullable()->change();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        // Reverse migration
    }
};
