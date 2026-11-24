<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class LectureSlideBlock extends Model
{
    use HasFactory;

    protected $fillable = [
        'lecture_id',
        'slide_page',
        'block_index',
        'block_id',
        'kind',
        'original_slide_text',
        'is_confused',
        'last_spoken_sec',
    ];

    protected $casts = [
        'slide_page' => 'integer',
        'block_index' => 'integer',
        'is_confused' => 'boolean',
        'last_spoken_sec' => 'float',
    ];

    public function lecture(): BelongsTo
    {
        return $this->belongsTo(Lecture::class);
    }

    public function inlineNotes(): HasMany
    {
        return $this->hasMany(InlineBlockNote::class, 'block_id', 'block_id')
            ->where('lecture_id', $this->lecture_id);
    }

    public function conceptHighlights(): HasMany
    {
        return $this->hasMany(BlockConceptHighlight::class, 'block_id', 'block_id')
            ->where('lecture_id', $this->lecture_id);
    }
}
