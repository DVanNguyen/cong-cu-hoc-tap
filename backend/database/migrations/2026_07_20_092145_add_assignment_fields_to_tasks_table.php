<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('tasks', function (Blueprint $table) {
            if (!Schema::hasColumn('tasks', 'status')) {
                $table->string('status')->default('PENDING')->after('task_type'); // PENDING, IN_PROGRESS, IN_REVIEW, COMPLETED, REJECTED
            }
        });
    }

    public function down(): void
    {
        Schema::table('tasks', function (Blueprint $table) {
            if (Schema::hasColumn('tasks', 'status')) {
                $table->dropColumn('status');
            }
        });
    }
};
