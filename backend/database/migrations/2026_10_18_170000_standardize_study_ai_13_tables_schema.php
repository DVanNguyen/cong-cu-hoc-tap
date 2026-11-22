<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        // ==========================================
        // 1. Cập nhật bảng COURSES
        // ==========================================
        Schema::table('courses', function (Blueprint $table) {
            if (!Schema::hasColumn('courses', 'syllabus_keywords')) {
                $table->json('syllabus_keywords')->nullable()->after('name');
            }
        });

        // ==========================================
        // 2. Cập nhật bảng LECTURES
        // ==========================================
        Schema::table('lectures', function (Blueprint $table) {
            if (!Schema::hasColumn('lectures', 'lecture_order')) {
                $table->integer('lecture_order')->default(1)->after('course_id');
            }
            if (!Schema::hasColumn('lectures', 'slide_file_path')) {
                $table->string('slide_file_path', 500)->nullable()->after('title');
            }
            if (!Schema::hasColumn('lectures', 'audio_file_path')) {
                $table->string('audio_file_path', 500)->nullable()->after('slide_file_path');
            }
            if (!Schema::hasColumn('lectures', 'recorded_at')) {
                $table->dateTime('recorded_at')->nullable()->after('audio_file_path');
            }
            if (!Schema::hasColumn('lectures', 'processing_status')) {
                $table->enum('processing_status', ['pending', 'processing', 'completed', 'failed'])->default('pending')->after('recorded_at');
            }
            if (!Schema::hasColumn('lectures', 'progress_percent')) {
                $table->integer('progress_percent')->default(0)->after('processing_status');
            }
        });

        // ==========================================
        // 3. Cập nhật bảng SLIDES
        // ==========================================
        Schema::table('slides', function (Blueprint $table) {
            if (!Schema::hasColumn('slides', 'slide_number')) {
                $table->integer('slide_number')->default(1)->after('lecture_id');
            }
            if (!Schema::hasColumn('slides', 'title')) {
                $table->string('title', 500)->nullable()->after('slide_number');
            }
            if (!Schema::hasColumn('slides', 'raw_text')) {
                $table->longText('raw_text')->nullable()->after('title');
            }
            if (!Schema::hasColumn('slides', 'extracted_terms')) {
                $table->json('extracted_terms')->nullable()->after('raw_text');
            }
            if (!Schema::hasColumn('slides', 'qdrant_point_id')) {
                $table->char('qdrant_point_id', 36)->nullable()->after('extracted_terms');
            }
        });

        if (Schema::hasColumn('slides', 'page_number')) {
            DB::statement("UPDATE slides SET slide_number = page_number WHERE page_number IS NOT NULL");
        }
        if (Schema::hasColumn('slides', 'slide_text')) {
            DB::statement("UPDATE slides SET raw_text = slide_text WHERE raw_text IS NULL AND slide_text IS NOT NULL");
        }

        // ==========================================
        // 10. Bảng RAW_MESSAGES
        // ==========================================
        if (!Schema::hasTable('raw_messages')) {
            Schema::create('raw_messages', function (Blueprint $table) {
                $table->id();
                $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
                $table->enum('source_channel', ['gmail', 'telegram', 'manual_paste', 'lecture_oral'])->default('telegram');
                $table->string('external_message_id', 255);
                $table->string('sender', 255)->nullable();
                $table->string('subject', 500)->nullable();
                $table->longText('body_text');
                $table->dateTime('received_at');
                $table->timestamps();

                $table->unique(['source_channel', 'external_message_id'], 'uq_source_msg');
            });
        }

        // ==========================================
        // 6. Bảng EVIDENCES (Evidence Grounding Trung tâm)
        // ==========================================
        if (!Schema::hasTable('evidences')) {
            Schema::create('evidences', function (Blueprint $table) {
                $table->id();
                $table->enum('source_type', ['lecture_audio', 'slide_text', 'email', 'telegram']);
                $table->foreignId('lecture_id')->nullable()->constrained('lectures')->nullOnDelete();
                $table->foreignId('slide_id')->nullable()->constrained('slides')->nullOnDelete();
                $table->foreignId('raw_message_id')->nullable()->constrained('raw_messages')->nullOnDelete();
                $table->text('exact_quote');
                $table->float('start_time_sec')->nullable();
                $table->float('end_time_sec')->nullable();
                $table->timestamps();
            });
        }

        // ==========================================
        // 4. Bảng SLIDE_ALIGNMENTS
        // ==========================================
        if (!Schema::hasTable('slide_alignments')) {
            Schema::create('slide_alignments', function (Blueprint $table) {
                $table->id();
                $table->foreignId('lecture_id')->constrained('lectures')->cascadeOnDelete();
                $table->foreignId('slide_id')->constrained('slides')->cascadeOnDelete();
                $table->float('start_time_sec');
                $table->float('end_time_sec');
                $table->enum('alignment_source', ['manual_click', 'auto_dtw'])->default('auto_dtw');
                $table->float('alignment_confidence')->default(1.0);
                $table->timestamps();
            });
        }

        // ==========================================
        // 5. Bảng TRANSCRIPT_SEGMENTS
        // ==========================================
        if (!Schema::hasTable('transcript_segments')) {
            Schema::create('transcript_segments', function (Blueprint $table) {
                $table->id();
                $table->foreignId('lecture_id')->constrained('lectures')->cascadeOnDelete();
                $table->foreignId('aligned_slide_id')->nullable()->constrained('slides')->nullOnDelete();
                $table->float('start_time_sec');
                $table->float('end_time_sec');
                $table->text('text');
                $table->float('novelty_score')->nullable();
                $table->timestamps();
            });
        }

        // ==========================================
        // 7. Bảng LECTURE_NOTES
        // ==========================================
        if (!Schema::hasTable('lecture_notes')) {
            Schema::create('lecture_notes', function (Blueprint $table) {
                $table->id();
                $table->foreignId('slide_id')->constrained('slides')->cascadeOnDelete();
                $table->foreignId('evidence_id')->constrained('evidences')->cascadeOnDelete();
                $table->text('bullet_content');
                $table->json('term_explanations')->nullable();
                $table->boolean('is_edited_by_user')->default(false);
                $table->timestamps();
            });
        }

        // ==========================================
        // 8. Cập nhật bảng CONCEPTS
        // ==========================================
        Schema::table('concepts', function (Blueprint $table) {
            if (!Schema::hasColumn('concepts', 'canonical_name')) {
                $table->string('canonical_name', 255)->nullable()->after('course_id');
            }
            if (!Schema::hasColumn('concepts', 'definition_summary')) {
                $table->text('definition_summary')->nullable()->after('aliases');
            }
            if (!Schema::hasColumn('concepts', 'first_defined_lecture_order')) {
                $table->integer('first_defined_lecture_order')->nullable()->after('definition_summary');
            }
            if (!Schema::hasColumn('concepts', 'first_defined_slide_id')) {
                $table->foreignId('first_defined_slide_id')->nullable()->after('first_defined_lecture_order')->constrained('slides')->nullOnDelete();
            }
            if (!Schema::hasColumn('concepts', 'first_used_lecture_order')) {
                $table->integer('first_used_lecture_order')->nullable()->after('first_defined_slide_id');
            }
            if (!Schema::hasColumn('concepts', 'first_used_slide_id')) {
                $table->foreignId('first_used_slide_id')->nullable()->after('first_used_lecture_order')->constrained('slides')->nullOnDelete();
            }
            if (!Schema::hasColumn('concepts', 'is_used_before_defined')) {
                $table->boolean('is_used_before_defined')->default(false)->after('first_used_slide_id');
            }
            if (!Schema::hasColumn('concepts', 'qdrant_point_id')) {
                $table->char('qdrant_point_id', 36)->nullable()->after('is_used_before_defined');
            }
        });

        if (Schema::hasColumn('concepts', 'name')) {
            DB::statement("UPDATE concepts SET canonical_name = name WHERE canonical_name IS NULL AND name IS NOT NULL");
        }
        if (Schema::hasColumn('concepts', 'definition')) {
            DB::statement("UPDATE concepts SET definition_summary = definition WHERE definition_summary IS NULL AND definition IS NOT NULL");
        }

        // ==========================================
        // 9. Bảng CONCEPT_RELATIONS
        // ==========================================
        if (!Schema::hasTable('concept_relations')) {
            Schema::create('concept_relations', function (Blueprint $table) {
                $table->id();
                $table->foreignId('course_id')->constrained('courses')->cascadeOnDelete();
                $table->foreignId('source_concept_id')->constrained('concepts')->cascadeOnDelete();
                $table->foreignId('target_concept_id')->constrained('concepts')->cascadeOnDelete();
                $table->enum('relation_type', ['prerequisite', 'part_of', 'example_of', 'contrast'])->default('prerequisite');
                $table->foreignId('evidence_id')->constrained('evidences')->cascadeOnDelete();
                $table->float('confidence')->default(1.0);
                $table->enum('status', ['verified', 'pending_review'])->default('verified');
                $table->timestamps();
            });
        }

        // ==========================================
        // 11. Cập nhật bảng TASKS
        // ==========================================
        Schema::table('tasks', function (Blueprint $table) {
            if (!Schema::hasColumn('tasks', 'due_at')) {
                $table->dateTime('due_at')->nullable()->after('task_type');
            }
            if (!Schema::hasColumn('tasks', 'estimated_effort_hours')) {
                $table->float('estimated_effort_hours')->default(2.0)->after('due_at');
            }
            if (!Schema::hasColumn('tasks', 'remaining_effort_hours')) {
                $table->float('remaining_effort_hours')->default(2.0)->after('estimated_effort_hours');
            }
            if (!Schema::hasColumn('tasks', 'urgency_score')) {
                $table->float('urgency_score')->default(0.0)->after('remaining_effort_hours');
            }
            if (!Schema::hasColumn('tasks', 'urgency_explanation')) {
                $table->string('urgency_explanation', 500)->nullable()->after('urgency_score');
            }
            if (!Schema::hasColumn('tasks', 'primary_evidence_id')) {
                $table->foreignId('primary_evidence_id')->nullable()->after('urgency_explanation')->constrained('evidences')->nullOnDelete();
            }
            if (!Schema::hasColumn('tasks', 'qdrant_point_id')) {
                $table->char('qdrant_point_id', 36)->nullable()->after('primary_evidence_id');
            }
        });

        // Convert epoch ms in due_date to due_at
        try {
            DB::statement("UPDATE tasks SET due_at = FROM_UNIXTIME(CAST(due_date AS UNSIGNED) / 1000) WHERE due_at IS NULL AND due_date IS NOT NULL AND due_date REGEXP '^[0-9]{10,13}$'");
        } catch (\Throwable $e) {}

        // ==========================================
        // 12. Bảng TASK_MILESTONES
        // ==========================================
        if (!Schema::hasTable('task_milestones')) {
            Schema::create('task_milestones', function (Blueprint $table) {
                $table->id();
                $table->foreignUuid('task_id')->constrained('tasks')->cascadeOnDelete();
                $table->integer('step_order')->default(1);
                $table->string('title', 255);
                $table->float('estimated_hours')->default(1.0);
                $table->dateTime('suggested_deadline')->nullable();
                $table->boolean('is_completed')->default(false);
                $table->timestamps();
            });
        }

        // ==========================================
        // 13. Bảng AI_JOBS (Quản lý Bất đồng bộ & Idempotency)
        // ==========================================
        if (!Schema::hasTable('ai_jobs')) {
            Schema::create('ai_jobs', function (Blueprint $table) {
                $table->id();
                $table->char('idempotency_key', 64)->unique();
                $table->enum('job_type', ['process_lecture', 'build_concept_graph', 'extract_task']);
                $table->enum('status', ['queued', 'running', 'completed', 'failed'])->default('queued');
                $table->json('result_payload')->nullable();
                $table->text('error_message')->nullable();
                $table->timestamps();
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('ai_jobs');
        Schema::dropIfExists('task_milestones');
        Schema::dropIfExists('concept_relations');
        Schema::dropIfExists('lecture_notes');
        Schema::dropIfExists('transcript_segments');
        Schema::dropIfExists('slide_alignments');
        Schema::dropIfExists('evidences');
        Schema::dropIfExists('raw_messages');
    }
};
