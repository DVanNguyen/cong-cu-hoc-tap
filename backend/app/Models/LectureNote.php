<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class LectureNote extends Model
{
    protected $table = 'lecture_notes';

    protected $fillable = [
        'slide_id',
        'evidence_id',
        'bullet_content',
        'term_explanations',
        'is_edited_by_user'
    ];

    protected $casts = [
        'term_explanations' => 'array',
        'is_edited_by_user' => 'boolean',
    ];

    public function slide(): BelongsTo
    {
        return $this->belongsTo(Slide::class);
    }

    public function evidence(): BelongsTo
    {
        return $this->belongsTo(Evidence::class);
    }
}
