<?php

namespace App\Http\Controllers\Api\StudyAi;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class CourseController extends Controller
{
    /**
     * GET /api/v1/courses
     */
    public function index(Request $request)
    {
        $userId = $request->query('user_id');

        $query = DB::table('courses')
            ->select('id', 'name', 'code', 'lecturer', 'description', 'created_at', 'updated_at')
            ->orderBy('id', 'asc');

        if ($userId) {
            $query->where('user_id', $userId);
        }

        $courses = $query->get();

        $lectureCounts = DB::table('lectures')
            ->select('course_id', DB::raw('count(*) as count'))
            ->groupBy('course_id')
            ->pluck('count', 'course_id');

        $data = $courses->map(function ($c) use ($lectureCounts) {
            return [
                'id' => (int) $c->id,
                'code' => (string) ($c->code ?? ''),
                'name' => (string) $c->name,
                'lecturer' => (string) ($c->lecturer ?? ''),
                'description' => (string) ($c->description ?? ''),
                'lecture_count' => (int) ($lectureCounts[$c->id] ?? 0),
                'created_at' => (string) $c->created_at,
                'updated_at' => (string) $c->updated_at,
            ];
        });

        return response()->json([
            'courses' => $data,
        ]);
    }

    /**
     * POST /api/v1/courses
     */
    public function store(Request $request)
    {
        $validated = $request->validate([
            'name' => 'required|string|max:255',
            'code' => 'nullable|string|max:50',
            'lecturer' => 'nullable|string|max:255',
            'description' => 'nullable|string',
            'user_id' => 'nullable|integer',
        ]);

        $userId = $validated['user_id'] ?? null;
        if (!$userId) {
            $firstUser = DB::table('users')->first();
            $userId = $firstUser ? $firstUser->id : null;
        }

        $courseId = DB::table('courses')->insertGetId([
            'name' => $validated['name'],
            'code' => $validated['code'] ?? '',
            'lecturer' => $validated['lecturer'] ?? '',
            'description' => $validated['description'] ?? '',
            'user_id' => $userId,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $course = DB::table('courses')->where('id', $courseId)->first();

        return response()->json([
            'course' => [
                'id' => (int) $course->id,
                'code' => (string) ($course->code ?? ''),
                'name' => (string) $course->name,
                'lecturer' => (string) ($course->lecturer ?? ''),
                'description' => (string) ($course->description ?? ''),
                'lecture_count' => 0,
                'created_at' => (string) $course->created_at,
                'updated_at' => (string) $course->updated_at,
            ],
        ], 201);
    }

    /**
     * PUT /api/v1/courses/{courseId}
     */
    public function update(Request $request, int $courseId)
    {
        $course = DB::table('courses')->where('id', $courseId)->first();
        if (!$course) {
            return response()->json(['error' => 'Course not found'], 404);
        }

        $validated = $request->validate([
            'name' => 'nullable|string|max:255',
            'code' => 'nullable|string|max:50',
            'lecturer' => 'nullable|string|max:255',
            'description' => 'nullable|string',
        ]);

        $updates = [];
        if ($request->has('name')) $updates['name'] = $validated['name'];
        if ($request->has('code')) $updates['code'] = $validated['code'];
        if ($request->has('lecturer')) $updates['lecturer'] = $validated['lecturer'];
        if ($request->has('description')) $updates['description'] = $validated['description'];
        $updates['updated_at'] = now();

        DB::table('courses')->where('id', $courseId)->update($updates);

        $updated = DB::table('courses')->where('id', $courseId)->first();
        $lectureCount = DB::table('lectures')->where('course_id', $courseId)->count();

        return response()->json([
            'course' => [
                'id' => (int) $updated->id,
                'code' => (string) ($updated->code ?? ''),
                'name' => (string) $updated->name,
                'lecturer' => (string) ($updated->lecturer ?? ''),
                'description' => (string) ($updated->description ?? ''),
                'lecture_count' => (int) $lectureCount,
                'created_at' => (string) $updated->created_at,
                'updated_at' => (string) $updated->updated_at,
            ],
        ], 200);
    }

    /**
     * DELETE /api/v1/courses/{courseId}
     */
    public function destroy(int $courseId)
    {
        $course = DB::table('courses')->where('id', $courseId)->first();
        if (!$course) {
            return response()->json(['error' => 'Course not found'], 404);
        }

        DB::table('courses')->where('id', $courseId)->delete();

        return response()->noContent();
    }
}
