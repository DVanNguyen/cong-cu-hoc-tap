<?php

namespace App\Http\Controllers;

use App\Models\Event;
use Illuminate\Http\Request;

/**
 * EventController - Offline-First Architecture v4.0
 *
 * ✅ All responses use BaseController methods (wrapped JSON)
 * ✅ All deletes are soft (via softDelete())
 * ✅ All updates set updated_at_ms
 * ✅ All queries use notDeleted() scope
 * ✅ Expanded day_of_week to 0-6 (Mon-Sun)
 * ✅ Added category, is_daily, custom_category fields
 */
class EventController extends BaseController
{
    /**
     * ✅ Get all events for the authenticated user (including group events)
     */
    public function index(Request $request)
    {
        $userId = $request->user()->id;
        $groupIds = \App\Models\GroupMember::where('user_id', $userId)->pluck('group_id');

        $events = Event::where(function ($query) use ($userId, $groupIds) {
                $query->where('user_id', $userId)
                      ->orWhereIn('group_id', $groupIds);
            })
            ->where(function ($query) use ($userId) {
                $query->whereNull('excluded_user_ids')
                      ->orWhere('excluded_user_ids', 'not like', '%"' . $userId . '"%');
            })
            ->notDeleted()
            ->get();

        return $this->successResponse($events, 'Events retrieved successfully');
    }

    /**
     * ✅ Create a new event
     */
    public function store(Request $request)
    {
        $validated = $request->validate([
            'id' => 'sometimes|string|uuid',
            'title' => 'required|string|max:255',
            'location' => 'nullable|string|max:255',
            'day_of_week' => 'nullable|integer|between:0,6',
            'start_time' => 'nullable|integer',
            'end_time' => 'nullable|integer',
            'repeat_mode' => 'nullable|string|max:50',
            'tag' => 'nullable|string',
            'palette_idx' => 'sometimes|integer|between:0,5',
            'is_all_day' => 'sometimes|boolean',
            'category' => 'nullable|string|max:50',
            'custom_category' => 'nullable|string|max:100',
            'group_id' => 'nullable|string|uuid',
        ]);

        if (!empty($validated['group_id'])) {
            $member = \App\Models\GroupMember::where('group_id', $validated['group_id'])
                ->where('user_id', $request->user()->id)
                ->first();
            if (!$member) {
                return $this->errorResponse('You are not a member of this group', null, 403);
            }
            if ($member->role !== 'OWNER' && $member->role !== 'ADMIN') {
                return $this->errorResponse('Only Admins or Owners can create group schedules', null, 403);
            }
        }

        $eventId = $validated['id'] ?? (string) \Illuminate\Support\Str::uuid();

        $event = Event::create([
            'id' => $eventId,
            'user_id' => $request->user()->id,
            'title' => $validated['title'],
            'location' => $validated['location'] ?? '',
            'day_of_week' => $validated['day_of_week'] ?? null,
            'start_time' => $validated['start_time'] ?? null,
            'end_time' => $validated['end_time'] ?? null,
            'repeat_mode' => $validated['repeat_mode'] ?? 'NONE',
            'tag' => $validated['tag'] ?? '',
            'palette_idx' => $validated['palette_idx'] ?? 0,
            'is_all_day' => $validated['is_all_day'] ?? false,
            'category' => $validated['category'] ?? 'work',
            'custom_category' => $validated['custom_category'] ?? null,
            'group_id' => $validated['group_id'] ?? null,
            'excluded_user_ids' => null,
            'updated_at_ms' => now()->getTimestampMs(),
        ]);

        // If it's a group event, post a system message to trigger silent sync for members
        if ($event->group_id) {
            \App\Models\Message::create([
                'id' => (string) \Illuminate\Support\Str::uuid(),
                'group_id' => $event->group_id,
                'sender_id' => $request->user()->id,
                'content' => '__SYSTEM_SYNC_SCHEDULE__',
                'created_at_ms' => now()->timestamp * 1000,
            ]);
        }

        return $this->successResponse($event, 'Event created successfully', 201);
    }

    /**
     * ✅ Get event details
     */
    public function show(Event $event, Request $request)
    {
        $userId = $request->user()->id;
        $groupIds = \App\Models\GroupMember::where('user_id', $userId)->pluck('group_id')->toArray();

        if ($event->user_id != $userId && !in_array($event->group_id, $groupIds)) {
            return $this->errorResponse('Unauthorized', null, 403);
        }

        return $this->successResponse($event, 'Event retrieved successfully');
    }

    /**
     * ✅ Update an event
     */
    public function update(Event $event, Request $request)
    {
        $userId = $request->user()->id;
        
        $isAuthorized = ($event->user_id == $userId);
        if (!$isAuthorized && $event->group_id) {
            $isAuthorized = \App\Models\GroupMember::where('group_id', $event->group_id)
                ->where('user_id', $userId)
                ->whereIn('role', ['OWNER', 'ADMIN'])
                ->exists();
        }

        if (!$isAuthorized) {
            return $this->errorResponse('Unauthorized to update this event', null, 403);
        }

        $validated = $request->validate([
            'title' => 'sometimes|string|max:255',
            'location' => 'nullable|string|max:255',
            'day_of_week' => 'nullable|integer|between:0,6',
            'start_time' => 'nullable|integer',
            'end_time' => 'nullable|integer',
            'repeat_mode' => 'nullable|string|max:50',
            'tag' => 'nullable|string',
            'palette_idx' => 'sometimes|integer|between:0,5',
            'is_all_day' => 'sometimes|boolean',
            'category' => 'nullable|string|max:50',
            'custom_category' => 'nullable|string|max:100',
            'group_id' => 'nullable|string|uuid',
        ]);

        $validated['updated_at_ms'] = now()->getTimestampMs();
        $event->update($validated);

        if ($event->group_id) {
            \App\Models\Message::create([
                'id' => (string) \Illuminate\Support\Str::uuid(),
                'group_id' => $event->group_id,
                'sender_id' => $request->user()->id,
                'content' => '__SYSTEM_SYNC_SCHEDULE__',
                'created_at_ms' => now()->timestamp * 1000,
            ]);
        }

        return $this->successResponse($event, 'Event updated successfully');
    }

    /**
     * ✅ Soft delete an event
     */
    public function destroy(Event $event, Request $request)
    {
        $userId = $request->user()->id;
        
        $isAuthorized = ($event->user_id == $userId);
        if (!$isAuthorized && $event->group_id) {
            $isAuthorized = \App\Models\GroupMember::where('group_id', $event->group_id)
                ->where('user_id', $userId)
                ->whereIn('role', ['OWNER', 'ADMIN'])
                ->exists();
        }

        if ($isAuthorized) {
            $event->softDelete();
        } else {
            if ($event->group_id) {
                $excluded = json_decode($event->excluded_user_ids ?: '[]', true);
                if (!is_array($excluded)) $excluded = [];
                if (!in_array($userId, $excluded)) {
                    $excluded[] = $userId;
                    $event->excluded_user_ids = json_encode($excluded);
                    $event->updated_at_ms = now()->getTimestampMs();
                    $event->save();
                }
            } else {
                return $this->errorResponse('Unauthorized to delete this event', null, 403);
            }
        }

        if ($event->group_id) {
            \App\Models\Message::create([
                'id' => (string) \Illuminate\Support\Str::uuid(),
                'group_id' => $event->group_id,
                'sender_id' => $request->user()->id,
                'content' => '__SYSTEM_SYNC_SCHEDULE__',
                'created_at_ms' => now()->timestamp * 1000,
            ]);
        }

        return $this->successResponse(null, 'Event deleted successfully');
    }

    /**
     * ✅ Batch sync events from client (WorkManager gọi).
     * POST /api/events/sync
     * Body: { "events": [{ "id", "title", ... }] }
     */
    public function sync(Request $request)
    {
        $validated = $request->validate([
            'events' => 'required|array',
            'events.*.id' => 'required|string',
            'events.*.title' => 'required|string',
            'events.*.location' => 'nullable|string',
            'events.*.day_of_week' => 'nullable|integer|between:0,6',
            'events.*.start_time' => 'nullable|integer',
            'events.*.end_time' => 'nullable|integer',
            'events.*.repeat_mode' => 'nullable|string',
            'events.*.tag' => 'nullable|string',
            'events.*.palette_idx' => 'nullable|integer',
            'events.*.is_all_day' => 'nullable|boolean',
            'events.*.category' => 'nullable|string',
            'events.*.custom_category' => 'nullable|string',
            'events.*.group_id' => 'nullable|string|uuid',
            'events.*.is_deleted' => 'sometimes|boolean',
            'events.*.updated_at_ms' => 'required|integer',
        ]);


        $syncedCount = 0;
        $userId = $request->user()->id;
        $myGroupIds = \App\Models\GroupMember::where('user_id', $userId)->pluck('group_id')->toArray();
        $triggeredGroups = [];

        foreach ($validated['events'] as $eventData) {
            if (!empty($eventData['group_id'])) {
                if (!in_array($eventData['group_id'], $myGroupIds)) {
                    continue;
                }
            }

            $existing = Event::find($eventData['id']);

            if ($existing) {
                $isAuthorized = ($existing->user_id == $userId);
                if (!$isAuthorized && $existing->group_id) {
                    $isAuthorized = \App\Models\GroupMember::where('group_id', $existing->group_id)
                        ->where('user_id', $userId)
                        ->whereIn('role', ['OWNER', 'ADMIN'])
                        ->exists();
                }

                if (!$isAuthorized) {
                    if (!empty($eventData['is_deleted']) && $eventData['is_deleted'] && $existing->group_id) {
                        $excluded = json_decode($existing->excluded_user_ids ?: '[]', true);
                        if (!is_array($excluded)) $excluded = [];
                        if (!in_array($userId, $excluded)) {
                            $excluded[] = $userId;
                            $existing->excluded_user_ids = json_encode($excluded);
                            $existing->updated_at_ms = max($existing->updated_at_ms, $eventData['updated_at_ms'] ?? 0);
                            $existing->save();
                            $syncedCount++;
                        }
                    }
                    continue;
                }

                $remoteMs = $eventData['updated_at_ms'] ?? 0;
                if ($remoteMs >= ($existing->updated_at_ms ?? 0)) {
                    $existing->update(array_merge($eventData, ['user_id' => $existing->user_id]));
                    $syncedCount++;
                    if ($existing->group_id) {
                        $triggeredGroups[] = $existing->group_id;
                    }
                }
            } else {
                if (!empty($eventData['group_id'])) {
                    $isAdminOrOwner = \App\Models\GroupMember::where('group_id', $eventData['group_id'])
                        ->where('user_id', $userId)
                        ->whereIn('role', ['OWNER', 'ADMIN'])
                        ->exists();
                    if (!$isAdminOrOwner) {
                        continue;
                    }
                }

                Event::create(array_merge($eventData, ['user_id' => $userId]));
                $syncedCount++;
                if (!empty($eventData['group_id'])) {
                    $triggeredGroups[] = $eventData['group_id'];
                }
            }
        }

        // Post system sync message for each updated group
        $triggeredGroups = array_unique($triggeredGroups);
        foreach ($triggeredGroups as $groupId) {
            \App\Models\Message::create([
                'id' => (string) \Illuminate\Support\Str::uuid(),
                'group_id' => $groupId,
                'sender_id' => $userId,
                'content' => '__SYSTEM_SYNC_SCHEDULE__',
                'created_at_ms' => now()->timestamp * 1000,
            ]);
        }

        return $this->successResponse([
            'synced' => $syncedCount,
            'total' => count($validated['events']),
        ], 'Events synced successfully');
    }
}
