<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class ConceptRelation extends Model
{
    protected $table = 'concept_relations';

    protected $fillable = [
        'course_id',
        'source_concept_id',
        'target_concept_id',
        'relation_type',
        'evidence_id',
        'confidence',
        'status'
    ];

    protected $casts = [
        'confidence' => 'float',
    ];

    public function course(): BelongsTo
    {
        return $this->belongsTo(Course::class);
    }

    public function sourceConcept(): BelongsTo
    {
        return $this->belongsTo(Concept::class, 'source_concept_id');
    }

    public function targetConcept(): BelongsTo
    {
        return $this->belongsTo(Concept::class, 'target_concept_id');
    }

    public function evidence(): BelongsTo
    {
        return $this->belongsTo(Evidence::class);
    }
}
