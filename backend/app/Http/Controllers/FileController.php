<?php

namespace App\Http\Controllers;

use App\Models\File;
use App\Models\Workspace;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;

class FileController extends Controller
{
    // ── GET /api/files/all ── Pull ALL files for user ────
    public function all(Request $request): JsonResponse
    {
        $workspaceIds = Workspace::where('user_id', $request->user()->id)->pluck('id');

        $files = File::whereIn('workspace_id', $workspaceIds)
            ->active()
            ->orderBy('updated_at_ms', 'desc')
            ->get();

        return response()->json(['success' => true, 'data' => $files]);
    }

    // ── GET /api/workspaces/{workspaceId}/files ────────────
    public function index(Request $request, string $workspaceId): JsonResponse
    {
        $workspace = Workspace::where('id', $workspaceId)
            ->where('user_id', $request->user()->id)
            ->firstOrFail();

        $files = File::where('workspace_id', $workspace->id)
            ->active()
            ->orderBy('updated_at_ms', 'desc')
            ->get();

        return response()->json(['success' => true, 'data' => $files]);
    }

    // ── POST /api/workspaces/{workspaceId}/files ───────────
    public function store(Request $request, string $workspaceId): JsonResponse
    {
        $workspace = Workspace::where('id', $workspaceId)
            ->where('user_id', $request->user()->id)
            ->firstOrFail();

        $validated = $request->validate([
            'id'           => 'required|string|uuid',
            'name'         => 'required|string|max:500',
            'type'         => 'required|string|max:50',
            'size_str'     => 'required|string|max:50',
            'time_str'     => 'required|string|max:50',
            'file'         => 'required|file|max:51200', // max 50MB
        ]);

        $uploadedFile = $request->file('file');
        $path = $uploadedFile->store('workspaces/' . $workspace->id, 'public');

        $fileRecord = File::updateOrCreate(
            ['id' => $validated['id']],
            [
                'workspace_id'  => $workspace->id,
                'user_id'       => $request->user()->id,
                'name'          => $validated['name'],
                'type'          => $validated['type'],
                'size_str'      => $validated['size_str'],
                'time_str'      => $validated['time_str'],
                'server_path'   => $path,
                'updated_at_ms' => now()->timestamp * 1000,
                'is_deleted'    => false,
            ]
        );

        return response()->json(['success' => true, 'data' => $fileRecord], 201);
    }

    // ── DELETE /api/files/{id} ─────────────────────────────
    public function destroy(Request $request, string $id): JsonResponse
    {
        $file = File::whereHas('workspace', function ($q) use ($request) {
                $q->where('user_id', $request->user()->id);
            })
            ->where('id', $id)
            ->firstOrFail();

        $file->update(['is_deleted' => true]);

        return response()->json(['success' => true, 'message' => 'Đã xoá file']);
    }

    // ── GET /api/files/download/{id} (Public endpoint) ────
    public function download(string $id)
    {
        $file = File::findOrFail($id);
        
        $filePath = storage_path('app/public/' . $file->server_path);
        if (!file_exists($filePath)) {
            abort(404, 'File not found on disk');
        }

        return response()->download($filePath, $file->name);
    }
}
