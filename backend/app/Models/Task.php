<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Task Model - Offline-First Architecture v4.0
 * 
 * CRITICAL RULES:
 * ✅ ID is UUID string (NOT auto-increment)
 * ✅ Foreign keys are UUID strings
 * ✅ Includes updated_at_ms for conflict resolution
 * ✅ Includes is_deleted for soft delete pattern
 * ✅ Never hard delete - always soft delete
 */
class Task extends Model
{
    use HasFactory, HasUuids;

    public $incrementing = false;
    protected $keyType = 'string';
    protected $primaryKey = 'id';

    /**
     * ✅ Fillable fields include sync metadata
     */
    protected $fillable = [
        'id',
        'workspace_id',
        'user_id',
        'title',
        'description',
        'tag',
        'due_date',
        'completed',
        'palette_idx',
        'important',
        'updated_at_ms',
        'created_at_ms',
        'is_deleted',
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
        'scheduled_end_time',
        'group_id',
        'assigner_id',
        'assignee_id',
        'parent_task_id',
        'is_public',
        'status',
        // StudyAI fields
        'raw_item_id',
        'course_id',
        'due_at',
        'estimated_effort_hours',
        'remaining_effort_hours',
        'urgency_score',
        'urgency_explanation',
        'primary_evidence_id',
        'qdrant_point_id',
        'confidence',
        'intent',
        'effort_est_min',
        'load_ratio',
        'evidence_quote',
        'needs_confirmation',
    ];

    /**
     * ✅ Cast types for data consistency
     */
    protected $casts = [
        'completed'     => 'boolean',
        'important'     => 'boolean',
        'is_public'     => 'boolean',
        'palette_idx'   => 'integer',
        'is_deleted'    => 'boolean',
        'updated_at_ms' => 'integer',
        'created_at_ms' => 'integer',
        'due_date'      => 'integer',
        'created_at'    => 'datetime',
        'updated_at'    => 'datetime',
        'total_weeks'   => 'integer',
        'completed_weeks' => 'integer',
        'missed_weeks'  => 'integer',
        'streak_count'  => 'integer',
        'snooze_count'  => 'integer',
        'last_completed_date' => 'integer',
        'completed_at'  => 'integer',
        'snoozed_until' => 'integer',
        'scheduled_start_time' => 'integer',
        'scheduled_end_time'   => 'integer',
        'due_at'               => 'datetime',
        'estimated_effort_hours' => 'float',
        'remaining_effort_hours' => 'float',
        'urgency_score'        => 'float',
    ];

    // ── RELATIONSHIPS ────────────────────────────────────────────────

    /**
     * Task belongs to a Workspace
     */
    public function workspace(): BelongsTo
    {
        return $this->belongsTo(Workspace::class);
    }

    /**
     * Task assigned to a User
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function assigner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'assigner_id');
    }

    public function assignee(): BelongsTo
    {
        return $this->belongsTo(User::class, 'assignee_id');
    }

    public function parentTask(): BelongsTo
    {
        return $this->belongsTo(Task::class, 'parent_task_id');
    }

    public function subTasks()
    {
        return $this->hasMany(Task::class, 'parent_task_id');
    }

    public function submissions()
    {
        return $this->hasMany(TaskSubmission::class, 'task_id');
    }

    // ── SOFT DELETE METHODS ──────────────────────────────────────────

    /**
     * ✅ Soft delete - mark as deleted instead of removing from DB
     */
    public function softDelete(): bool
    {
        return $this->update([
            'is_deleted' => true,
            'updated_at_ms' => now()->getTimestampMs(),
        ]);
    }

    /**
     * ✅ Restore a soft-deleted task
     */
    public function restore(): bool
    {
        return $this->update([
            'is_deleted' => false,
            'updated_at_ms' => now()->getTimestampMs(),
        ]);
    }

    // ── SCOPES ───────────────────────────────────────────────────────

    /**
     * ✅ Scope: Exclude soft-deleted tasks
     */
    public function scopeNotDeleted($query)
    {
        return $query->where('is_deleted', false);
    }

    /**
     * ✅ Scope: Only soft-deleted tasks
     */
    public function scopeDeleted($query)
    {
        return $query->where('is_deleted', true);
    }

    /**
     * ✅ Scope: Only completed tasks
     */
    public function scopeCompleted($query)
    {
        return $query->where('completed', true);
    }

    /**
     * ✅ Scope: Only pending tasks
     */
    public function scopePending($query)
    {
        return $query->where('completed', false);
    }

    public function primaryEvidence(): BelongsTo
    {
        return $this->belongsTo(Evidence::class, 'primary_evidence_id');
    }

    public function milestones(): \Illuminate\Database\Eloquent\Relations\HasMany
    {
        return $this->hasMany(TaskMilestone::class)->orderBy('step_order', 'asc');
    }
}
