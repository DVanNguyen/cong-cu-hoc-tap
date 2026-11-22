<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Block extends Model
{
    use HasFactory;

    public $incrementing = false;
    protected $keyType   = 'string';

    // Các loại block hợp lệ — validate trước khi lưu
    const VALID_TYPES = ['H1', 'H2', 'H3', 'TEXT', 'TODO', 'IMAGE', 'CODE', 'DIVIDER', 'QUOTE', 'BULLET', 'TOPIC', 'FILE_LINK', 'TABLE', 'LINK'];

    protected $fillable = [
        'id',
        'page_id',
        'type',
        'content',
        'position',
        'is_checked',
        'metadata',
        'updated_at_ms',
        'is_deleted',
    ];

    protected $casts = [
        'is_checked'    => 'boolean',
        'is_deleted'    => 'boolean',
        'metadata'      => 'array',      // JSON tự động encode/decode
        'updated_at_ms' => 'integer',
        'position'      => 'integer',
    ];

    public function page(): BelongsTo
    {
        return $this->belongsTo(Page::class);
    }

    public function scopeActive($query)
    {
        return $query->where('is_deleted', false);
    }
}