<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class InlineBlockNote extends Model
{
    use HasFactory;

    protected $fillable = [
        'lecture_id',
        'block_id',
        'explanation_text',
        'raw_transcript',
        'timestamp_start_sec',
        'timestamp_end_sec',
        'novelty_score',
        'jumped_non_linear',
    ];

    protected $casts = [
        'timestamp_start_sec' => 'float',
        'timestamp_end_sec' => 'float',
        'novelty_score' => 'float',
        'jumped_non_linear' => 'boolean',
    ];

    public function lecture(): BelongsTo
    {
        return $this->belongsTo(Lecture::class);
    }
}
