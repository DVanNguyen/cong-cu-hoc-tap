<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class ExternalReference extends Model
{
    use HasFactory;

    protected $table = 'external_references';
    public $timestamps = false; // Chỉ có created_at

    protected $fillable = [
        'concept_id',
        'slide_id',
        'title',
        'url',
        'source_provider',
        'domain_name',
        'snippet_summary',
        'context_similarity',
        'authority_weight',
        'final_rank_score',
        'citation_badge',
        'created_at',
    ];

    protected $casts = [
        'context_similarity' => 'float',
        'authority_weight' => 'float',
        'final_rank_score' => 'float',
        'created_at' => 'datetime',
    ];

    public function concept()
    {
        return $this->belongsTo(Concept::class);
    }

    public function slide()
    {
        return $this->belongsTo(Slide::class);
    }
}
