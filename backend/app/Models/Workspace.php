<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Workspace extends Model
{
    use HasFactory, HasUuids;

    // UUID từ Android — không dùng auto-increment
    public $incrementing = false;
    protected $keyType   = 'string';

    protected $fillable = [
        'id',
        'user_id',
        'name',
        'icon_emoji',
        'color',
        'position',
        'updated_at_ms',
        'is_deleted',
    ];

    protected $casts = [
        'is_deleted'    => 'boolean',
        'updated_at_ms' => 'integer',
        'position'      => 'integer',
    ];

    // ── Relationships ──────────────────────────────────────

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function pages(): HasMany
    {
        return $this->hasMany(Page::class);
    }

    // ── Scope: chỉ lấy chưa xoá ───────────────────────────

    public function scopeNotDeleted($query)
    {
        return $query->where('is_deleted', false);
    }
}