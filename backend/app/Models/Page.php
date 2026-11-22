<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Page extends Model
{
    use HasFactory;

    public $incrementing = false;
    protected $keyType   = 'string';

    protected $fillable = [
        'id',
        'workspace_id',
        'title',
        'icon_emoji',
        'is_pinned',
        'position',
        'updated_at_ms',
        'is_deleted',
    ];

    protected $casts = [
        'is_pinned'     => 'boolean',
        'is_deleted'    => 'boolean',
        'updated_at_ms' => 'integer',
        'position'      => 'integer',
    ];

    public function workspace(): BelongsTo
    {
        return $this->belongsTo(Workspace::class);
    }

    public function blocks(): HasMany
    {
        return $this->hasMany(Block::class)->orderBy('position');
    }

    public function scopeActive($query)
    {
        return $query->where('is_deleted', false);
    }
}