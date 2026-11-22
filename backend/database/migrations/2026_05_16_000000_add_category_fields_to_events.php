<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Add category, is_daily, custom_category columns to events table.
     * Supports the new Schedule UI matching lichtrinh.jsx design.
     */
    public function up(): void
    {
        Schema::table('events', function (Blueprint $table) {
            $table->string('category')->default('work')->after('palette_idx');
            $table->boolean('is_daily')->default(false)->after('category');
            $table->string('custom_category')->nullable()->after('is_daily');
        });
    }

    public function down(): void
    {
        Schema::table('events', function (Blueprint $table) {
            $table->dropColumn(['category', 'is_daily', 'custom_category']);
        });
    }
};
