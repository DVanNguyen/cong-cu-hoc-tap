<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Message extends Model
{
    use HasFactory, HasUuids;

    public $incrementing = false;
    protected $keyType = 'string';

    protected $fillable = [
        'id',
        'group_id',
        'sender_id',
        'content',
        'created_at_ms',
    ];

    protected $casts = [
        'created_at_ms' => 'integer',
    ];

    protected $appends = ['sender_name'];

    public function getSenderNameAttribute(): string
    {
        return $this->sender ? $this->sender->name : 'Thành viên';
    }

    public function group(): BelongsTo
    {
        return $this->belongsTo(Group::class);
    }

    public function sender(): BelongsTo
    {
        return $this->belongsTo(User::class, 'sender_id');
    }
}
