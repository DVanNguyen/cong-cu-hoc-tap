<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class ConceptEdge extends Model
{
    use HasFactory;

    protected $fillable = [
        'course_id',
        'from_concept_id',
        'to_concept_id',
        'relation',
        'evidence_quote',
        'evidence_lecture_id',
    ];

    public function course(): BelongsTo
    {
        return $this->belongsTo(Course::class);
    }

    public function fromConcept(): BelongsTo
    {
        return $this->belongsTo(Concept::class, 'from_concept_id');
    }

    public function toConcept(): BelongsTo
    {
        return $this->belongsTo(Concept::class, 'to_concept_id');
    }

    public function evidenceLecture(): BelongsTo
    {
        return $this->belongsTo(Lecture::class, 'evidence_lecture_id');
    }
}
