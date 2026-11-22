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
            $table->string('task_type')->default('SMALL');
            $table->string('repeat_mode')->default('NONE');
            $table->integer('total_weeks')->default(0);
            $table->integer('completed_weeks')->default(0);
            $table->integer('missed_weeks')->default(0);
            $table->string('weekly_commitment')->default('');
            
            $table->bigInteger('last_completed_date')->nullable();
            $table->bigInteger('completed_at')->nullable();
            $table->integer('streak_count')->default(0);
            $table->integer('snooze_count')->default(0);
            $table->bigInteger('snoozed_until')->nullable();
            
            $table->bigInteger('scheduled_start_time')->nullable();
            $table->bigInteger('scheduled_end_time')->nullable();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('tasks', function (Blueprint $table) {
            $table->dropColumn([
                'task_type',
                'repeat_mode',
                'total_weeks',
                'completed_weeks',
                'missed_weeks',
                'weekly_commitment',
                'last_completed_date',
                'completed_at',
                'streak_count',
                'snooze_count',
                'snoozed_until',
                'scheduled_start_time',
                'scheduled_end_time'
            ]);
        });
    }
};
