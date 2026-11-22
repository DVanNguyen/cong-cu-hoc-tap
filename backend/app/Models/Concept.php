<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Concept extends Model
{
    use HasFactory;

    protected $fillable = [
        'course_id',
        'canonical_name',
        'name',
        'aliases',
        'definition_summary',
        'definition',
        'first_defined_lecture_order',
        'first_defined_slide_id',
        'first_used_lecture_order',
        'first_used_slide_id',
        'is_used_before_defined',
        'qdrant_point_id',
        'first_defined_lecture_id',
    ];

    protected $casts = [
        'aliases' => 'array',
        'is_used_before_defined' => 'boolean',
        'first_defined_lecture_order' => 'integer',
        'first_used_lecture_order' => 'integer',
    ];

    public function course(): BelongsTo
    {
        return $this->belongsTo(Course::class);
    }

    public function firstDefinedSlide(): BelongsTo
    {
        return $this->belongsTo(Slide::class, 'first_defined_slide_id');
    }

    public function firstUsedSlide(): BelongsTo
    {
        return $this->belongsTo(Slide::class, 'first_used_slide_id');
    }

    public function outgoingRelations(): HasMany
    {
        return $this->hasMany(ConceptRelation::class, 'source_concept_id');
    }

    public function incomingRelations(): HasMany
    {
        return $this->hasMany(ConceptRelation::class, 'target_concept_id');
    }

    public function outgoingEdges(): HasMany
    {
        return $this->hasMany(ConceptEdge::class, 'from_concept_id');
    }

    public function incomingEdges(): HasMany
    {
        return $this->hasMany(ConceptEdge::class, 'to_concept_id');
    }
}
