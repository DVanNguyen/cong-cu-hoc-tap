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
        Schema::create('files', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->uuid('workspace_id')->index();
            $table->uuid('user_id')->index();
            $table->string('name');
            $table->string('type');
            $table->string('size_str');
            $table->string('time_str');
            $table->string('server_path');
            $table->bigInteger('updated_at_ms')->index();
            $table->boolean('is_deleted')->default(false)->index();
            $table->timestamps();

            // Foreign keys
            $table->foreign('workspace_id')->references('id')->on('workspaces')->onDelete('cascade');
            $table->foreign('user_id')->references('id')->on('users')->onDelete('cascade');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('files');
    }
};
