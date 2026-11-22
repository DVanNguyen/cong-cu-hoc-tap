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
        Schema::disableForeignKeyConstraints();

        // 1. Create groups table
        Schema::create('groups', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->string('name');
            $table->uuid('owner_id');
            $table->foreign('owner_id')->references('id')->on('users')->onDelete('cascade');
            $table->timestamps();
        });

        // 2. Create group_members table
        Schema::create('group_members', function (Blueprint $table) {
            $table->id();
            $table->uuid('group_id');
            $table->uuid('user_id');
            $table->string('role')->default('MEMBER'); // OWNER or MEMBER
            $table->foreign('group_id')->references('id')->on('groups')->onDelete('cascade');
            $table->foreign('user_id')->references('id')->on('users')->onDelete('cascade');
            $table->timestamps();
        });

        // 3. Create messages table
        Schema::create('messages', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->uuid('group_id');
            $table->uuid('sender_id');
            $table->text('content');
            $table->bigInteger('created_at_ms');
            $table->foreign('group_id')->references('id')->on('groups')->onDelete('cascade');
            $table->foreign('sender_id')->references('id')->on('users')->onDelete('cascade');
            $table->timestamps();
        });

        // 4. Add group_id to events table
        Schema::table('events', function (Blueprint $table) {
            $table->uuid('group_id')->nullable()->after('user_id');
            $table->foreign('group_id')->references('id')->on('groups')->onDelete('cascade');
        });

        Schema::enableForeignKeyConstraints();
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::disableForeignKeyConstraints();

        Schema::table('events', function (Blueprint $table) {
            $table->dropForeign(['group_id']);
            $table->dropColumn('group_id');
        });

        Schema::dropIfExists('messages');
        Schema::dropIfExists('group_members');
        Schema::dropIfExists('groups');

        Schema::enableForeignKeyConstraints();
    }
};
