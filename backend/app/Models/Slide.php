<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Slide extends Model
{
    use HasFactory;

    protected $fillable = [
        'lecture_id',
        'slide_number',
        'page_number',
        'title',
        'raw_text',
        'slide_text',
        'summary',
        'extracted_terms',
        'qdrant_point_id',
    ];

    protected $casts = [
        'slide_number' => 'integer',
        'page_number' => 'integer',
        'extracted_terms' => 'array',
    ];

    public function lecture(): BelongsTo
    {
        return $this->belongsTo(Lecture::class);
    }

    public function lectureNotes(): HasMany
    {
        return $this->hasMany(LectureNote::class);
    }

    public function noteItems(): HasMany
    {
        return $this->hasMany(NoteItem::class)->orderBy('timestamp_start_sec');
    }

    public function slideAlignments(): HasMany
    {
        return $this->hasMany(SlideAlignment::class);
    }

    public function transcriptSegments(): HasMany
    {
        return $this->hasMany(TranscriptSegment::class, 'aligned_slide_id');
    }

    public function evidences(): HasMany
    {
        return $this->hasMany(Evidence::class);
    }
}
