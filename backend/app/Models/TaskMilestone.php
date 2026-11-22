<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class TaskMilestone extends Model
{
    protected $table = 'task_milestones';

    protected $fillable = [
        'task_id',
        'step_order',
        'title',
        'estimated_hours',
        'suggested_deadline',
        'is_completed'
    ];

    protected $casts = [
        'step_order' => 'integer',
        'estimated_hours' => 'float',
        'suggested_deadline' => 'datetime',
        'is_completed' => 'boolean',
    ];

    public function task(): BelongsTo
    {
        return $this->belongsTo(Task::class);
    }
}
