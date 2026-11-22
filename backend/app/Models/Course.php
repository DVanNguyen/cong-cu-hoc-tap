<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Course extends Model
{
    use HasFactory;

    protected $fillable = [
        'user_id',
        'code',
        'name',
        'description',
        'syllabus_keywords',
    ];

    protected $casts = [
        'syllabus_keywords' => 'array',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function lectures(): HasMany
    {
        return $this->hasMany(Lecture::class)->orderBy('lecture_order', 'asc');
    }

    public function concepts(): HasMany
    {
        return $this->hasMany(Concept::class);
    }

    public function conceptRelations(): HasMany
    {
        return $this->hasMany(ConceptRelation::class);
    }

    public function conceptEdges(): HasMany
    {
        return $this->hasMany(ConceptEdge::class);
    }

    public function tasks(): HasMany
    {
        return $this->hasMany(Task::class);
    }
}
