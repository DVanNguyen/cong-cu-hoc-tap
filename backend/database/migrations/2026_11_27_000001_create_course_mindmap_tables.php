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
        if (!Schema::hasTable('course_mindmap_branches')) {
            Schema::create('course_mindmap_branches', function (Blueprint $table) {
                $table->id();
                $table->foreignId('course_id')->constrained('courses')->cascadeOnDelete();
                $table->foreignId('lecture_id')->nullable()->constrained('lectures')->nullOnDelete();
                $table->foreignId('vault_slide_id')->nullable()->constrained('course_slide_vaults')->nullOnDelete();
                $table->string('branch_key', 120);
                $table->string('title');
                $table->string('tone', 24)->default('TEAL');
                $table->boolean('is_newly_added')->default(true);
                $table->integer('sort_order')->default(0);
                $table->timestamps();

                $table->unique(['course_id', 'branch_key'], 'cm_branches_course_key_unique');
            });
        }

        if (!Schema::hasTable('course_mindmap_leaves')) {
            Schema::create('course_mindmap_leaves', function (Blueprint $table) {
                $table->id();
                $table->foreignId('branch_id')->constrained('course_mindmap_branches')->cascadeOnDelete();
                $table->foreignId('course_id')->constrained('courses')->cascadeOnDelete();
                $table->foreignId('lecture_id')->nullable()->constrained('lectures')->nullOnDelete();
                $table->string('slug', 160);
                $table->string('title');
                $table->string('slide_ref', 64)->default('Slide 1');
                $table->integer('slide_number')->default(1);
                $table->integer('timestamp_sec')->default(0);
                $table->text('summary')->nullable();
                $table->boolean('is_mastered')->default(false);
                $table->integer('sort_order')->default(0);
                $table->timestamps();

                $table->unique(['branch_id', 'slug'], 'cm_leaves_branch_slug_unique');
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('course_mindmap_leaves');
        Schema::dropIfExists('course_mindmap_branches');
    }
};
