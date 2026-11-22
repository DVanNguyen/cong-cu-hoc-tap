<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class DictionaryEntry extends Model
{
    use HasFactory;

    protected $table = 'dictionary_entries';
    public $timestamps = false; // Chỉ có updated_at

    protected $fillable = [
        'concept_id',
        'abstractness_score',
        'slide_skeleton_context',
        'lecturer_oral_explanation',
        'ai_deep_explanation',
        'intuition_example',
        'updated_at',
    ];

    protected $casts = [
        'abstractness_score' => 'float',
        'updated_at' => 'datetime',
    ];

    public function concept()
    {
        return $this->belongsTo(Concept::class);
    }
}
