<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Workspace;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class WorkspaceController extends Controller
{
    // ── GET /api/workspaces ────────────────────────────────
    // Lấy tất cả workspace của user đang đăng nhập

    public function index(Request $request): JsonResponse
    {
        $workspaces = Workspace::where('user_id', $request->user()->id)
            ->active()                          // scope: is_deleted = false
            ->orderBy('position')
            ->get();

        return response()->json([
            'success' => true,
            'data'    => $workspaces,
        ]);
    }

    // ── POST /api/workspaces ───────────────────────────────
    // Tạo mới hoặc upsert (Android gửi UUID của mình)

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'id'         => 'required|string|uuid',
            'name'       => 'required|string|max:255',
            'icon_emoji' => 'nullable|string|max:10',
            'color'      => 'nullable|string|max:10',
            'position'   => 'nullable|integer',
            'updated_at_ms' => 'nullable|integer',
        ]);

        // updateOrCreate: nếu id đã tồn tại thì update, chưa có thì create
        $workspace = Workspace::updateOrCreate(
            [
                'id'      => $validated['id'],
                'user_id' => $request->user()->id,
            ],
            [
                'name'          => $validated['name'],
                'icon_emoji'    => $validated['icon_emoji']    ?? '📋',
                'color'         => $validated['color']         ?? '#1A56DB',
                'position'      => $validated['position']      ?? 0,
                'updated_at_ms' => $validated['updated_at_ms'] ?? now()->timestamp * 1000,
                'is_deleted'    => false,
            ]
        );

        return response()->json([
            'success' => true,
            'message' => 'Workspace đã được lưu',
            'data'    => $workspace,
        ], 201);
    }

    // ── GET /api/workspaces/{id} ───────────────────────────

    public function show(Request $request, string $id): JsonResponse
    {
        $workspace = Workspace::where('id', $id)
            ->where('user_id', $request->user()->id)
            ->active()
            ->firstOrFail();

        return response()->json(['success' => true, 'data' => $workspace]);
    }

    // ── PUT /api/workspaces/{id} ───────────────────────────

    public function update(Request $request, string $id): JsonResponse
    {
        $workspace = Workspace::where('id', $id)
            ->where('user_id', $request->user()->id)
            ->firstOrFail();

        $validated = $request->validate([
            'name'          => 'sometimes|string|max:255',
            'icon_emoji'    => 'sometimes|string|max:10',
            'color'         => 'sometimes|string|max:10',
            'position'      => 'sometimes|integer',
            'updated_at_ms' => 'sometimes|integer',
            'is_deleted'    => 'sometimes|boolean',
        ]);

        $workspace->update($validated);

        return response()->json(['success' => true, 'data' => $workspace]);
    }

    // ── DELETE /api/workspaces/{id} ───────────────────────
    // Soft delete phía server

    public function destroy(Request $request, string $id): JsonResponse
    {
        $workspace = Workspace::where('id', $id)
            ->where('user_id', $request->user()->id)
            ->firstOrFail();

        $workspace->update(['is_deleted' => true]);

        return response()->json(['success' => true, 'message' => 'Đã xoá workspace']);
    }

    // ── POST /api/workspaces/sync ──────────────────────────
    /**
     * Batch sync nhiều workspace cùng lúc từ Android.
     * Android gửi toàn bộ PENDING records trong một request.
     * Server upsert từng cái, trả về danh sách ids thành công.
     *
     * Body: { "items": [ {...}, {...} ] }
     */
    public function sync(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'items'                => 'required|array|min:1|max:100',
            'items.*.id'           => 'required|string|uuid',
            'items.*.name'         => 'required|string|max:255',
            'items.*.icon_emoji'   => 'nullable|string|max:10',
            'items.*.color'        => 'nullable|string|max:10',
            'items.*.position'     => 'nullable|integer',
            'items.*.updated_at_ms'=> 'nullable|integer',
            'items.*.is_deleted'   => 'nullable|boolean',
        ]);

        $syncedIds = [];
        $failedIds = [];

        DB::beginTransaction();
        try {
            foreach ($validated['items'] as $item) {
                Workspace::updateOrCreate(
                    [
                        'id'      => $item['id'],
                        'user_id' => $request->user()->id,
                    ],
                    [
                        'name'          => $item['name'],
                        'icon_emoji'    => $item['icon_emoji']    ?? '📋',
                        'color'         => $item['color']         ?? '#1A56DB',
                        'position'      => $item['position']      ?? 0,
                        'updated_at_ms' => $item['updated_at_ms'] ?? now()->timestamp * 1000,
                        'is_deleted'    => $item['is_deleted']    ?? false,
                    ]
                );
                $syncedIds[] = $item['id'];
            }
            DB::commit();
        } catch (\Exception $e) {
            DB::rollBack();
            return response()->json([
                'success' => false,
                'message' => 'Sync thất bại: ' . $e->getMessage(),
            ], 500);
        }

        return response()->json([
            'success'    => true,
            'synced_ids' => $syncedIds,  // Android dùng list này để mark SYNCED
            'message'    => count($syncedIds) . ' workspace đã được đồng bộ',
        ]);
    }
}