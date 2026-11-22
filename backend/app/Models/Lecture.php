<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Lecture extends Model
{
    use HasFactory;

    protected $fillable = [
        'course_id',
        'lecture_order',
        'title',
        'slide_file_path',
        'audio_file_path',
        'slide_path',
        'audio_path',
        'duration_sec',
        'recorded_at',
        'processing_status',
        'progress_percent',
        'status',
        'error_message',
    ];

    protected $casts = [
        'lecture_order' => 'integer',
        'progress_percent' => 'integer',
        'recorded_at' => 'datetime',
    ];

    public function course(): BelongsTo
    {
        return $this->belongsTo(Course::class);
    }

    public function slides(): HasMany
    {
        return $this->hasMany(Slide::class)->orderBy('slide_number', 'asc');
    }

    public function slideAlignments(): HasMany
    {
        return $this->hasMany(SlideAlignment::class);
    }

    public function transcriptSegments(): HasMany
    {
        return $this->hasMany(TranscriptSegment::class)->orderBy('start_time_sec', 'asc');
    }

    public function evidences(): HasMany
    {
        return $this->hasMany(Evidence::class);
    }
}
