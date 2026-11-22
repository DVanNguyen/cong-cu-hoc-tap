<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::create('notifications', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->uuid('user_id');
            $table->uuid('sender_id')->nullable();
            $table->string('type'); // friend_request, schedule_conflict, alarm
            $table->string('title');
            $table->text('content');
            $table->text('data')->nullable(); // json data
            $table->string('status')->default('pending'); // pending, accepted, rejected, read
            $table->bigInteger('created_at_ms');
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
        });

        Schema::create('friends', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->uuid('user_id');
            $table->uuid('friend_id');
            $table->timestamps();

            $table->unique(['user_id', 'friend_id']);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('notifications');
        Schema::dropIfExists('friends');
    }
};
