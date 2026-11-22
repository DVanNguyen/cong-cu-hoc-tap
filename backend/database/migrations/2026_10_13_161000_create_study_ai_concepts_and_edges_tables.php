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
        // 1. Concepts Table
        Schema::create('concepts', function (Blueprint $table) {
            $table->id();
            $table->foreignId('course_id')->constrained()->cascadeOnDelete();
            $table->string('name');
            $table->json('aliases')->nullable(); // Ví dụ: ["tiến trình", "process", "tiến trình hệ điều hành"]
            $table->text('definition')->nullable();
            $table->foreignId('first_defined_lecture_id')->nullable()->constrained('lectures')->nullOnDelete();
            $table->timestamps();

            $table->unique(['course_id', 'name']);
        });

        // 2. Concept Edges Table (Đồ thị tri thức DAG)
        Schema::create('concept_edges', function (Blueprint $table) {
            $table->id();
            $table->foreignId('course_id')->constrained()->cascadeOnDelete();
            $table->foreignId('from_concept_id')->constrained('concepts')->cascadeOnDelete();
            $table->foreignId('to_concept_id')->constrained('concepts')->cascadeOnDelete();
            $table->enum('relation', ['prerequisite_of', 'part_of', 'example_of', 'contrasts_with'])->default('prerequisite_of');
            $table->text('evidence_quote')->nullable();
            $table->foreignId('evidence_lecture_id')->nullable()->constrained('lectures')->nullOnDelete();
            $table->timestamps();

            $table->unique(['from_concept_id', 'to_concept_id', 'relation'], 'concept_edge_unique');
        });

        // 3. Concept Mentions Table (Vị trí xuất hiện của khái niệm)
        Schema::create('concept_mentions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('concept_id')->constrained()->cascadeOnDelete();
            $table->foreignId('lecture_id')->constrained()->cascadeOnDelete();
            $table->foreignId('slide_id')->nullable()->constrained()->nullOnDelete();
            $table->integer('timestamp_sec')->nullable();
            $table->timestamps();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('concept_mentions');
        Schema::dropIfExists('concept_edges');
        Schema::dropIfExists('concepts');
    }
};
