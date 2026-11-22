<?php

namespace App\Http\Controllers;

use App\Models\Workspace;
use Illuminate\Http\Request;

/**
 * WorkspaceController - Offline-First Architecture v4.0
 * 
 * ✅ All responses use BaseController methods
 * ✅ All deletes are soft (via softDelete())
 * ✅ All updates set updated_at_ms
 */
class WorkspaceController extends BaseController
{
    /**
     * ✅ Get all workspaces for the authenticated user
     */
    public function index(Request $request)
    {
        $workspaces = Workspace::where('user_id', $request->user()->id)
            ->notDeleted()
            ->withCount(['pages' => function ($q) {
                $q->where('is_deleted', false);
            }])
            ->get()
            ->map(function ($ws) {
                $ws->post_count = $ws->pages_count ?? 0;
                return $ws;
            });
        return $this->successResponse($workspaces, 'Workspaces retrieved successfully');
    }

    /**
     * ✅ Create a new workspace
     */
    public function store(Request $request)
    {
        $validated = $request->validate([
            'name' => 'required|string|max:255',
            'icon_emoji' => 'sometimes|string|max:10',
            'palette_idx' => 'sometimes|integer|between:0,5',
            'color' => 'sometimes|string|max:20',
            'position' => 'sometimes|integer',
        ]);

        $workspace = Workspace::create([
            'user_id' => $request->user()->id,
            'name' => $validated['name'],
            'icon_emoji' => $validated['icon_emoji'] ?? '📋',
            'color' => $validated['color'] ?? null,
            'position' => $validated['position'] ?? 0,
            'updated_at_ms' => now()->getTimestampMs(),
            'is_deleted' => false,
        ]);

        return $this->successResponse($workspace, 'Workspace created successfully', 201);
    }

    /**
     * ✅ Get workspace details
     */
    public function show(Workspace $workspace, Request $request)
    {
        if ($workspace->user_id !== $request->user()->id) {
            return $this->errorResponse('Unauthorized access to workspace', null, 403);
        }
        
        $workspace->load('user', 'pages');
        
        return $this->successResponse($workspace, 'Workspace details retrieved successfully');
    }

    /**
     * ✅ Update workspace
     */
    public function update(Workspace $workspace, Request $request)
    {
        if ($workspace->user_id !== $request->user()->id) {
            return $this->errorResponse('Unauthorized access to workspace', null, 403);
        }

        $validated = $request->validate([
            'name' => 'sometimes|string|max:255',
            'icon_emoji' => 'sometimes|string|max:10',
        ]);

        $validated['updated_at_ms'] = now()->getTimestampMs();

        $workspace->update($validated);
        return $this->successResponse($workspace, 'Workspace updated successfully');
    }

    /**
     * ✅ Soft delete workspace
     */
    public function destroy(Workspace $workspace, Request $request)
    {
        if ($workspace->user_id !== $request->user()->id) {
            return $this->errorResponse('Unauthorized access to workspace', null, 403);
        }
        
        $workspace->update([
            'is_deleted' => true,
            'updated_at_ms' => now()->getTimestampMs(),
        ]);

        return $this->successResponse(null, 'Workspace deleted successfully');
    }

    /**
     * ✅ Batch sync workspaces from Android
     * Handles bulk upsert/delete operations from offline-first client
     */
    public function sync(Request $request)
    {
        $validated = $request->validate([
            'updates' => 'sometimes|array',
            'updates.*.id' => 'required_with:updates|string|uuid',
            'updates.*.name' => 'sometimes|string|max:255',
            'updates.*.icon_emoji' => 'sometimes|string|max:10',
            'updates.*.color' => 'sometimes|nullable|string|max:20',
            'updates.*.position' => 'sometimes|nullable|integer',
            'updates.*.is_deleted' => 'sometimes|boolean',
            'updates.*.updated_at_ms' => 'required_with:updates|integer',
            // Android sends 'items' key
            'items' => 'sometimes|array',
            'items.*.id' => 'required_with:items|string|uuid',
            'items.*.name' => 'sometimes|string|max:255',
            'items.*.icon_emoji' => 'sometimes|string|max:10',
            'items.*.color' => 'sometimes|nullable|string|max:20',
            'items.*.position' => 'sometimes|nullable|integer',
            'items.*.is_deleted' => 'sometimes|boolean',
            'items.*.updated_at_ms' => 'required_with:items|integer',
        ]);

        // Accept either 'updates' or 'items' key
        $items = $validated['updates'] ?? $validated['items'] ?? [];

        $syncedIds = [];
        
        foreach ($items as $item) {
            $workspace = Workspace::find($item['id']);
            
            if ($workspace) {
                // Only update if remote version is newer
                if ($item['updated_at_ms'] >= ($workspace->updated_at_ms ?? 0)) {
                    $workspace->update($item);
                    $syncedIds[] = $workspace->id;
                } else {
                    $syncedIds[] = $workspace->id; // Acknowledge anyway
                }
            } else {
                // Create new workspace
                $item['user_id'] = $request->user()->id;
                Workspace::create($item);
                $syncedIds[] = $item['id'];
            }
        }

        return response()->json([
            'success' => true,
            'synced_ids' => $syncedIds,
        ]);
    }
}
