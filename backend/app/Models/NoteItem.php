<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class NoteItem extends Model
{
    use HasFactory;

    protected $fillable = [
        'slide_id',
        'content',
        'timestamp_start_sec',
        'timestamp_end_sec',
        'type',
        'confidence',
        'term_original',
        'term_vietnamese',
    ];

    public function slide(): BelongsTo
    {
        return $this->belongsTo(Slide::class);
    }
}
