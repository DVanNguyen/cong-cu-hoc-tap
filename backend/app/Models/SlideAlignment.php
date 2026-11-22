<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class SlideAlignment extends Model
{
    protected $table = 'slide_alignments';

    protected $fillable = [
        'lecture_id',
        'slide_id',
        'start_time_sec',
        'end_time_sec',
        'alignment_source',
        'alignment_confidence'
    ];

    protected $casts = [
        'start_time_sec' => 'float',
        'end_time_sec' => 'float',
        'alignment_confidence' => 'float',
    ];

    public function lecture(): BelongsTo
    {
        return $this->belongsTo(Lecture::class);
    }

    public function slide(): BelongsTo
    {
        return $this->belongsTo(Slide::class);
    }
}
