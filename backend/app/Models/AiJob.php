<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class AiJob extends Model
{
    protected $table = 'ai_jobs';

    protected $fillable = [
        'idempotency_key',
        'job_type',
        'status',
        'result_payload',
        'error_message'
    ];

    protected $casts = [
        'result_payload' => 'array',
    ];
}
