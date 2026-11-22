<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Event Model - Offline-First Architecture v4.0
 *
 * CRITICAL RULES:
 * ✅ ID is UUID string (NOT auto-increment)
 * ✅ Foreign keys are UUID strings
 * ✅ Includes updated_at_ms for conflict resolution
 * ✅ Includes is_deleted for soft delete pattern
 * ✅ Never hard delete - always soft delete
 */
class Event extends Model
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
        'user_id',
        'title',
        'location',
        'day_of_week',
        'tag',
        'palette_idx',
        'is_all_day',
        'category',
        'custom_category',
        'start_time',
        'end_time',
        'repeat_mode',
        'group_id',
        'excluded_user_ids',
        'updated_at_ms',
        'is_deleted',
        'overridden_by_event_id',
    ];

    /**
     * ✅ Cast types for data consistency
     */
    protected $casts = [
        'is_all_day'    => 'boolean',
        'is_deleted'    => 'boolean',
        'updated_at_ms' => 'integer',
        'palette_idx'   => 'integer',
        'day_of_week'   => 'integer', // 0-6 (Mon-Sun)
        'start_time'    => 'integer',
        'end_time'      => 'integer',
        'created_at'    => 'datetime',
        'updated_at'    => 'datetime',
    ];

    // ── RELATIONSHIPS ────────────────────────────────────────────────

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function group(): BelongsTo
    {
        return $this->belongsTo(Group::class);
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
     * ✅ Restore a soft-deleted event
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
     * ✅ Scope: Exclude soft-deleted events
     */
    public function scopeNotDeleted($query)
    {
        return $query->where('is_deleted', false);
    }

    /**
     * ✅ Scope: Only soft-deleted events
     */
    public function scopeDeleted($query)
    {
        return $query->where('is_deleted', true);
    }
}
