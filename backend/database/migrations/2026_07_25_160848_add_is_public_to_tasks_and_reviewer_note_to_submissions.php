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
        Schema::table('tasks', function (Blueprint $table) {
            $table->uuid('group_id')->nullable()->after('status');
            $table->uuid('assigner_id')->nullable()->after('group_id');
            $table->uuid('assignee_id')->nullable()->after('assigner_id');
            $table->uuid('parent_task_id')->nullable()->after('assignee_id');
            $table->boolean('is_public')->default(true)->after('parent_task_id');
        });

        Schema::table('task_submissions', function (Blueprint $table) {
            $table->text('reviewer_note')->nullable()->after('file_urls');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('tasks', function (Blueprint $table) {
            $table->dropColumn(['group_id', 'assigner_id', 'assignee_id', 'parent_task_id', 'is_public']);
        });

        Schema::table('task_submissions', function (Blueprint $table) {
            $table->dropColumn('reviewer_note');
        });
    }
};
