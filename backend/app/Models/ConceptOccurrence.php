<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class ConceptOccurrence extends Model
{
    use HasFactory;

    protected $table = 'concept_occurrences';
    public $timestamps = false; // Chỉ có created_at

    protected $fillable = [
        'concept_id',
        'lecture_id',
        'slide_id',
        'transcript_segment_id',
        'occurrence_role',
        'pos_linear_index',
        'context_excerpt',
        'timestamp_sec',
        'created_at',
    ];

    protected $casts = [
        'pos_linear_index' => 'integer',
        'timestamp_sec' => 'float',
        'created_at' => 'datetime',
    ];

    public function concept()
    {
        return $this->belongsTo(Concept::class);
    }

    public function lecture()
    {
        return $this->belongsTo(Lecture::class);
    }

    public function slide()
    {
        return $this->belongsTo(Slide::class);
    }

    public function transcriptSegment()
    {
        return $this->belongsTo(TranscriptSegment::class);
    }
}
