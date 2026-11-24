<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class CourseSlideVault extends Model
{
    use HasFactory;

    protected $fillable = [
        'course_id',
        'file_name',
        'file_path',
        'mime_type',
        'page_count',
        'parsed_blocks_json',
    ];

    protected $casts = [
        'page_count' => 'integer',
        'parsed_blocks_json' => 'array',
    ];

    public function course(): BelongsTo
    {
        return $this->belongsTo(Course::class);
    }
}
