<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Block;
use App\Models\Page;
use App\Models\Workspace;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class BlockController extends Controller
{
    // ── GET /api/pages/{pageId}/blocks ────────────────────

    public function index(Request $request, string $pageId): JsonResponse
    {
        try {
            $page = $this->getPageForUser($pageId, $request->user()->id);
            
            $blocks = Block::where('page_id', $page->id)
                ->active()
                ->orderBy('position')
                ->get();
                
            return response()->json(['success' => true, 'data' => $blocks]);
        } catch (\Illuminate\Database\Eloquent\ModelNotFoundException $e) {
            // Page not synced yet or deleted, return empty blocks
            return response()->json(['success' => true, 'data' => []]);
        }
    }

    // ── GET /api/blocks/all ── Pull ALL blocks for user ────
    public function all(Request $request): JsonResponse
    {
        $workspaceIds = Workspace::where('user_id', $request->user()->id)->pluck('id');
        $pageIds = Page::whereIn('workspace_id', $workspaceIds)->pluck('id');

        $blocks = Block::whereIn('page_id', $pageIds)
            ->active()
            ->orderBy('page_id')
            ->orderBy('position')
            ->get();

        return response()->json(['success' => true, 'data' => $blocks]);
    }

    // ── POST /api/pages/{pageId}/blocks ───────────────────

    public function store(Request $request, string $pageId): JsonResponse
    {
        $page = $this->getPageForUser($pageId, $request->user()->id);

        $validated = $request->validate([
            'id'           => 'required|string|uuid',
            'type'         => 'required|string|in:H1,H2,H3,TEXT,TODO,IMAGE,CODE,DIVIDER,QUOTE,BULLET,TOPIC,FILE_LINK,TABLE,LINK',
            'content'      => 'nullable|string',
            'position'     => 'nullable|integer',
            'is_checked'   => 'nullable|boolean',
            'metadata'     => 'nullable|array',
            'updated_at_ms'=> 'nullable|integer',
        ]);

        $block = Block::updateOrCreate(
            ['id' => $validated['id']],
            [
                'page_id'       => $page->id,
                'type'          => $validated['type'],
                'content'       => $validated['content']      ?? '',
                'position'      => $validated['position']     ?? 0,
                'is_checked'    => $validated['is_checked']   ?? false,
                'metadata'      => $validated['metadata']     ?? null,
                'updated_at_ms' => $validated['updated_at_ms'] ?? now()->timestamp * 1000,
                'is_deleted'    => false,
            ]
        );

        return response()->json(['success' => true, 'data' => $block], 201);
    }

    // ── PUT /api/blocks/{id} ───────────────────────────────

    public function update(Request $request, string $id): JsonResponse
    {
        $block = $this->getBlockForUser($id, $request->user()->id);

        $validated = $request->validate([
            'content'       => 'sometimes|string|nullable',
            'position'      => 'sometimes|integer',
            'is_checked'    => 'sometimes|boolean',
            'metadata'      => 'sometimes|array|nullable',
            'updated_at_ms' => 'sometimes|integer',
            'is_deleted'    => 'sometimes|boolean',
        ]);

        $block->update($validated);

        return response()->json(['success' => true, 'data' => $block]);
    }

    // ── DELETE /api/blocks/{id} ────────────────────────────

    public function destroy(Request $request, string $id): JsonResponse
    {
        $block = $this->getBlockForUser($id, $request->user()->id);
        $block->update(['is_deleted' => true]);

        return response()->json(['success' => true, 'message' => 'Đã xoá block']);
    }

    // ── PUT /api/pages/{pageId}/blocks/reorder ─────────────
    /**
     * Cập nhật position hàng loạt sau khi user drag & drop.
     * Body: { "positions": [ {"id": "uuid", "position": 1000}, ... ] }
     */
    public function reorder(Request $request, string $pageId): JsonResponse
    {
        $page = $this->getPageForUser($pageId, $request->user()->id);

        $validated = $request->validate([
            'positions'              => 'required|array|min:1',
            'positions.*.id'         => 'required|string|uuid',
            'positions.*.position'   => 'required|integer',
        ]);

        DB::beginTransaction();
        try {
            foreach ($validated['positions'] as $item) {
                Block::where('id', $item['id'])
                    ->where('page_id', $page->id) // bảo vệ: chỉ update block của page này
                    ->update(['position' => $item['position']]);
            }
            DB::commit();
        } catch (\Exception $e) {
            DB::rollBack();
            return response()->json(['success' => false, 'message' => $e->getMessage()], 500);
        }

        return response()->json(['success' => true, 'message' => 'Đã cập nhật thứ tự']);
    }

    // ── POST /api/blocks/sync ──────────────────────────────
    /**
     * Batch sync nhiều block cùng lúc từ Android.
     * Đây là endpoint quan trọng nhất — SyncWorker gọi endpoint này.
     */
    public function sync(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'items'                  => 'required|array|min:1|max:500',
            'items.*.id'             => 'required|string|uuid',
            'items.*.page_id'        => 'required|string|uuid',
            'items.*.type'           => 'required|string|in:H1,H2,H3,TEXT,TODO,IMAGE,CODE,DIVIDER,QUOTE,BULLET,TOPIC,FILE_LINK,TABLE,LINK',
            'items.*.content'        => 'nullable|string',
            'items.*.position'       => 'nullable|integer',
            'items.*.is_checked'     => 'nullable|boolean',
            'items.*.metadata'       => 'nullable|array',
            'items.*.updated_at_ms'  => 'nullable|integer',
            'items.*.is_deleted'     => 'nullable|boolean',
        ]);

        // Lấy tất cả page_id hợp lệ của user để validate quyền
        $validPageIds = Page::whereHas('workspace', function ($q) use ($request) {
                $q->where('user_id', $request->user()->id);
            })
            ->pluck('id')
            ->toArray();

        $syncedIds = [];

        DB::beginTransaction();
        try {
            foreach ($validated['items'] as $item) {
                if (! in_array($item['page_id'], $validPageIds)) {
                    continue; // Bỏ qua block không thuộc user
                }

                Block::updateOrCreate(
                    ['id' => $item['id']],
                    [
                        'page_id'       => $item['page_id'],
                        'type'          => $item['type'],
                        'content'       => $item['content']       ?? '',
                        'position'      => $item['position']      ?? 0,
                        'is_checked'    => $item['is_checked']    ?? false,
                        'metadata'      => $item['metadata']      ?? null,
                        'updated_at_ms' => $item['updated_at_ms'] ?? now()->timestamp * 1000,
                        'is_deleted'    => $item['is_deleted']    ?? false,
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

    // ── Private helpers ────────────────────────────────────

    /**
     * Lấy Page và đảm bảo nó thuộc user hiện tại.
     * Tự throw 404 nếu không tìm thấy hoặc không có quyền.
     */
    private function getPageForUser(string $pageId, string $userId): Page
    {
        return Page::whereHas('workspace', function ($q) use ($userId) {
                $q->where('user_id', $userId);
            })
            ->where('id', $pageId)
            ->firstOrFail();
    }

    private function getBlockForUser(string $blockId, string $userId): Block
    {
        return Block::whereHas('page.workspace', function ($q) use ($userId) {
                $q->where('user_id', $userId);
            })
            ->where('id', $blockId)
            ->firstOrFail();
    }
}