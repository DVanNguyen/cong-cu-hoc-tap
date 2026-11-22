<?php

namespace App\Http\Controllers;

use App\Models\Notification;
use App\Models\Friend;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\Support\Facades\DB;

class NotificationController extends BaseController
{
    /**
     * Get active notifications for the user
     */
    public function index(Request $request)
    {
        $userId = $request->user()->id;

        $notifications = Notification::where('user_id', $userId)
            ->active()
            ->orderBy('created_at_ms', 'desc')
            ->get();

        return $this->successResponse($notifications, 'Notifications retrieved successfully');
    }

    /**
     * Update notification status (read, accept, reject)
     */
    public function update(Request $request, string $id)
    {
        $userId = $request->user()->id;
        $notification = Notification::where('user_id', $userId)
            ->where('id', $id)
            ->firstOrFail();

        $validated = $request->validate([
            'status' => 'required|string|in:read,accepted,rejected',
        ]);

        $status = $validated['status'];

        if ($notification->type === 'friend_request' && ($status === 'accepted' || $status === 'rejected')) {
            if ($notification->status !== 'pending') {
                return $this->errorResponse('This request has already been processed', null, 400);
            }

            if ($status === 'accepted') {
                // Begin Transaction to create reciprocal friendships
                DB::transaction(function () use ($userId, $notification) {
                    $senderId = $notification->sender_id;

                    // Re-check friendship existence
                    $exists = Friend::where('user_id', $userId)
                        ->where('friend_id', $senderId)
                        ->exists();

                    if (!$exists) {
                        Friend::create([
                            'id' => (string) Str::uuid(),
                            'user_id' => $userId,
                            'friend_id' => $senderId,
                        ]);

                        Friend::create([
                            'id' => (string) Str::uuid(),
                            'user_id' => $senderId,
                            'friend_id' => $userId,
                        ]);
                    }
                });
            }
        }

        $notification->update([
            'status' => $status,
            'updated_at' => now(),
        ]);

        return $this->successResponse($notification, 'Notification updated successfully');
    }

    /**
     * Soft delete notification
     */
    public function destroy(Request $request, string $id)
    {
        $userId = $request->user()->id;
        $notification = Notification::where('user_id', $userId)
            ->where('id', $id)
            ->firstOrFail();

        $notification->update([
            'is_deleted' => true,
        ]);

        return $this->successResponse(null, 'Notification deleted successfully');
    }

    /**
     * Send a friend request to a user by ID
     */
    public function sendFriendRequest(Request $request)
    {
        $validated = $request->validate([
            'friend_id' => 'required|string|uuid',
        ]);

        $authId = $request->user()->id;
        $friendId = $validated['friend_id'];

        if ($authId === $friendId) {
            return $this->errorResponse('You cannot add yourself as a friend', null, 400);
        }

        // Verify recipient exists
        $friendUser = User::findOrFail($friendId);

        // Check if already friends
        $alreadyFriends = Friend::where('user_id', $authId)
            ->where('friend_id', $friendId)
            ->exists();

        if ($alreadyFriends) {
            return $this->errorResponse('You are already friends with this user', null, 400);
        }

        // Check if there is a pending request (either way)
        $pendingRequest = Notification::where('type', 'friend_request')
            ->where('status', 'pending')
            ->where(function ($q) use ($authId, $friendId) {
                $q->where(function ($q1) use ($authId, $friendId) {
                    $q1->where('user_id', $friendId)->where('sender_id', $authId);
                })->orWhere(function ($q2) use ($authId, $friendId) {
                    $q2->where('user_id', $authId)->where('sender_id', $friendId);
                });
            })
            ->exists();

        if ($pendingRequest) {
            return $this->errorResponse('A pending friend request already exists between you and this user', null, 400);
        }

        // Create the notification for the recipient
        $notification = Notification::create([
            'id' => (string) Str::uuid(),
            'user_id' => $friendId,
            'sender_id' => $authId,
            'type' => 'friend_request',
            'title' => $request->user()->name,
            'content' => 'muốn kết bạn với bạn.',
            'status' => 'pending',
            'created_at_ms' => now()->timestamp * 1000,
        ]);

        // Post a message in system log or SSE if needed (optional)

        return $this->successResponse($notification, 'Friend request sent successfully', 201);
    }

    /**
     * Get all friends of the current user
     */
    public function friends(Request $request)
    {
        $userId = $request->user()->id;
        $friends = User::whereIn('id', function($query) use ($userId) {
            $query->select('friend_id')->from('friends')->where('user_id', $userId);
        })->get();

        return $this->successResponse($friends, 'Friends retrieved successfully');
    }
}
