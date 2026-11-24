<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class BlockConceptHighlight extends Model
{
    use HasFactory;

    protected $fillable = [
        'lecture_id',
        'block_id',
        'term',
        'source_type', // previous_lecture | academic_internet
        'source_label',
        'explanation',
        'course_context_id',
        'alternative_contexts_json',
    ];

    protected $casts = [
        'course_context_id' => 'integer',
        'alternative_contexts_json' => 'array',
    ];

    public function lecture(): BelongsTo
    {
        return $this->belongsTo(Lecture::class);
    }
}
