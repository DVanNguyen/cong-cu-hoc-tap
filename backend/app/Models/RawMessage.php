<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class RawMessage extends Model
{
    protected $table = 'raw_messages';

    protected $fillable = [
        'user_id',
        'source_channel',
        'external_message_id',
        'sender',
        'subject',
        'body_text',
        'received_at'
    ];

    protected $casts = [
        'received_at' => 'datetime',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function evidences(): HasMany
    {
        return $this->hasMany(Evidence::class);
    }
}
