<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class File extends Model
{
    use HasFactory;

    protected $keyType = 'string';
    public $incrementing = false;

    protected $fillable = [
        'id',
        'workspace_id',
        'user_id',
        'name',
        'type',
        'size_str',
        'time_str',
        'server_path',
        'updated_at_ms',
        'is_deleted',
    ];

    protected $casts = [
        'is_deleted' => 'boolean',
        'updated_at_ms' => 'integer',
    ];

    public function workspace()
    {
        return $this->belongsTo(Workspace::class);
    }

    public function user()
    {
        return $this->belongsTo(User::class);
    }

    public function scopeActive($query)
    {
        return $query->where('is_deleted', false);
    }
}
