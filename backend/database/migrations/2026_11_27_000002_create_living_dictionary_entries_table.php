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
        if (!Schema::hasTable('living_dictionary_entries')) {
            Schema::create('living_dictionary_entries', function (Blueprint $table) {
                $table->id();
                $table->foreignId('course_id')->nullable()->constrained('courses')->nullOnDelete();
                $table->unsignedBigInteger('lecture_id')->nullable()->index();
                $table->integer('slide_number')->nullable();
                $table->string('origin_label', 120)->default('Từ điển sống');
                $table->string('term', 220);
                $table->text('context_quote')->nullable();
                $table->longText('ai_explanation');
                $table->text('code_example')->nullable();
                $table->text('simple_followup')->nullable();
                $table->text('pitfall_followup')->nullable();
                $table->longText('trusted_sources_json')->nullable();
                $table->boolean('is_mastered')->default(false);
                $table->boolean('is_new')->default(true);
                $table->timestamps();

                $table->index(['course_id', 'is_mastered']);
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('living_dictionary_entries');
    }
};
