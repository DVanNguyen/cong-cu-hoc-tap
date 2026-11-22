<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class TranscriptSegment extends Model
{
    protected $table = 'transcript_segments';

    protected $fillable = [
        'lecture_id',
        'aligned_slide_id',
        'start_time_sec',
        'end_time_sec',
        'text',
        'novelty_score'
    ];

    protected $casts = [
        'start_time_sec' => 'float',
        'end_time_sec' => 'float',
        'novelty_score' => 'float',
    ];

    public function lecture(): BelongsTo
    {
        return $this->belongsTo(Lecture::class);
    }

    public function slide(): BelongsTo
    {
        return $this->belongsTo(Slide::class, 'aligned_slide_id');
    }
}
