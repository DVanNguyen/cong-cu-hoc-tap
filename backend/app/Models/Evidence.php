<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Evidence extends Model
{
    protected $table = 'evidences';

    protected $fillable = [
        'source_type',
        'lecture_id',
        'slide_id',
        'raw_message_id',
        'exact_quote',
        'start_time_sec',
        'end_time_sec'
    ];

    protected $casts = [
        'start_time_sec' => 'float',
        'end_time_sec' => 'float',
    ];

    public function lecture(): BelongsTo
    {
        return $this->belongsTo(Lecture::class);
    }

    public function slide(): BelongsTo
    {
        return $this->belongsTo(Slide::class);
    }

    public function rawMessage(): BelongsTo
    {
        return $this->belongsTo(RawMessage::class);
    }

    public function lectureNotes(): HasMany
    {
        return $this->hasMany(LectureNote::class);
    }

    public function conceptRelations(): HasMany
    {
        return $this->hasMany(ConceptRelation::class);
    }

    public function tasks(): HasMany
    {
        return $this->hasMany(Task::class, 'primary_evidence_id');
    }
}
