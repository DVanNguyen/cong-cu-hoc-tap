<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Page;
use App\Models\Workspace;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class PageController extends Controller
{
    // ── GET /api/workspaces/{workspaceId}/pages ────────────

    public function index(Request $request, string $workspaceId): JsonResponse
    {
        // Xác nhận workspace thuộc về user đang đăng nhập
        $workspace = Workspace::where('id', $workspaceId)
            ->where('user_id', $request->user()->id)
            ->firstOrFail();

        $pages = Page::where('workspace_id', $workspace->id)
            ->active()
            ->orderByDesc('is_pinned')  // Pinned lên đầu
            ->orderBy('position')
            ->get();

        return response()->json(['success' => true, 'data' => $pages]);
    }

    // ── GET /api/pages/all ── Pull ALL pages for user ────
    public function all(Request $request): JsonResponse
    {
        $workspaceIds = Workspace::where('user_id', $request->user()->id)
            ->pluck('id');

        $pages = Page::whereIn('workspace_id', $workspaceIds)
            ->active()
            ->orderBy('workspace_id')
            ->orderBy('position')
            ->get();

        return response()->json(['success' => true, 'data' => $pages]);
    }

    // ── POST /api/workspaces/{workspaceId}/pages ───────────

    public function store(Request $request, string $workspaceId): JsonResponse
    {
        $workspace = Workspace::where('id', $workspaceId)
            ->where('user_id', $request->user()->id)
            ->firstOrFail();

        $validated = $request->validate([
            'id'           => 'required|string|uuid',
            'title'        => 'required|string|max:500',
            'icon_emoji'   => 'nullable|string|max:10',
            'is_pinned'    => 'nullable|boolean',
            'position'     => 'nullable|integer',
            'updated_at_ms'=> 'nullable|integer',
        ]);

        $page = Page::updateOrCreate(
            ['id' => $validated['id']],
            [
                'workspace_id'  => $workspace->id,
                'title'         => $validated['title'],
                'icon_emoji'    => $validated['icon_emoji']    ?? '📄',
                'is_pinned'     => $validated['is_pinned']     ?? false,
                'position'      => $validated['position']      ?? 0,
                'updated_at_ms' => $validated['updated_at_ms'] ?? now()->timestamp * 1000,
                'is_deleted'    => false,
            ]
        );

        return response()->json(['success' => true, 'data' => $page], 201);
    }

    // ── PUT /api/pages/{id} ────────────────────────────────

    public function update(Request $request, string $id): JsonResponse
    {
        // Lấy page và xác nhận quyền sở hữu qua workspace
        $page = Page::whereHas('workspace', function ($q) use ($request) {
                $q->where('user_id', $request->user()->id);
            })
            ->where('id', $id)
            ->firstOrFail();

        $validated = $request->validate([
            'title'         => 'sometimes|string|max:500',
            'icon_emoji'    => 'sometimes|string|max:10',
            'is_pinned'     => 'sometimes|boolean',
            'position'      => 'sometimes|integer',
            'updated_at_ms' => 'sometimes|integer',
            'is_deleted'    => 'sometimes|boolean',
        ]);

        $page->update($validated);

        return response()->json(['success' => true, 'data' => $page]);
    }

    // ── DELETE /api/pages/{id} ─────────────────────────────

    public function destroy(Request $request, string $id): JsonResponse
    {
        $page = Page::whereHas('workspace', function ($q) use ($request) {
                $q->where('user_id', $request->user()->id);
            })
            ->where('id', $id)
            ->firstOrFail();

        $page->update(['is_deleted' => true]);

        return response()->json(['success' => true, 'message' => 'Đã xoá page']);
    }

    // ── POST /api/pages/sync ───────────────────────────────

    public function sync(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'items'                  => 'required|array|min:1|max:200',
            'items.*.id'             => 'required|string|uuid',
            'items.*.workspace_id'   => 'required|string|uuid',
            'items.*.title'          => 'required|string|max:500',
            'items.*.icon_emoji'     => 'nullable|string|max:10',
            'items.*.is_pinned'      => 'nullable|boolean',
            'items.*.position'       => 'nullable|integer',
            'items.*.updated_at_ms'  => 'nullable|integer',
            'items.*.is_deleted'     => 'nullable|boolean',
        ]);

        // Lấy tất cả workspace_id hợp lệ của user để validate quyền
        $validWorkspaceIds = Workspace::where('user_id', $request->user()->id)
            ->pluck('id')
            ->toArray();

        $syncedIds = [];

        DB::beginTransaction();
        try {
            foreach ($validated['items'] as $item) {
                // Bỏ qua nếu workspace không thuộc user này
                if (! in_array($item['workspace_id'], $validWorkspaceIds)) {
                    continue;
                }

                Page::updateOrCreate(
                    ['id' => $item['id']],
                    [
                        'workspace_id'  => $item['workspace_id'],
                        'title'         => $item['title'],
                        'icon_emoji'    => $item['icon_emoji']   ?? '📄',
                        'is_pinned'     => $item['is_pinned']    ?? false,
                        'position'      => $item['position']     ?? 0,
                        'updated_at_ms' => $item['updated_at_ms'] ?? now()->timestamp * 1000,
                        'is_deleted'    => $item['is_deleted']   ?? false,
                    ]
                );
                $syncedIds[] = $item['id'];
            }
            DB::commit();
        } catch (\Exception $e) {
            DB::rollBack();
            return response()->json(['success' => false, 'message' => $e->getMessage()], 500);
        }

        return response()->json([
            'success'    => true,
            'synced_ids' => $syncedIds,
        ]);
    }
}