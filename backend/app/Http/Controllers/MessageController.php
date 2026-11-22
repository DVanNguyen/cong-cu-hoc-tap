<?php

namespace App\Http\Controllers;

use App\Models\Group;
use App\Models\GroupMember;
use App\Models\Message;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class MessageController extends BaseController
{
    /**
     * Get chat messages for the group
     */
    public function index(Request $request, string $groupId)
    {
        $group = Group::findOrFail($groupId);
        $userId = $request->user()->id;

        // Ensure user is in group
        $isMember = GroupMember::where('group_id', $groupId)
            ->where('user_id', $userId)
            ->exists();

        if (!$isMember) {
            return $this->errorResponse('You are not a member of this group', null, 403);
        }

        // Get messages that are NOT system triggers
        $messages = Message::where('group_id', $groupId)
            ->whereNotIn('content', ['__SYSTEM_SYNC_SCHEDULE__', '__SYSTEM_SYNC_TASK__'])
            ->with('sender')
            ->orderBy('created_at_ms', 'asc')
            ->get();

        return $this->successResponse($messages, 'Messages retrieved successfully');
    }

    /**
     * Send a new message to the group
     */
    public function store(Request $request, string $groupId)
    {
        $group = Group::findOrFail($groupId);
        $userId = $request->user()->id;

        $isMember = GroupMember::where('group_id', $groupId)
            ->where('user_id', $userId)
            ->exists();

        if (!$isMember) {
            return $this->errorResponse('You are not a member of this group', null, 403);
        }

        $validated = $request->validate([
            'id' => 'sometimes|string|uuid',
            'content' => 'required|string',
        ]);

        $messageId = $validated['id'] ?? (string) Str::uuid();

        $message = Message::create([
            'id' => $messageId,
            'group_id' => $groupId,
            'sender_id' => $userId,
            'content' => $validated['content'],
            'created_at_ms' => now()->timestamp * 1000,
        ]);

        return $this->successResponse($message->load('sender'), 'Message sent successfully', 201);
    }

    /**
     * Stream real-time events via Server-Sent Events (SSE)
     */
    public function stream(Request $request, string $groupId)
    {
        // Set proper execution time limit to run long stream
        set_time_limit(0);

        return response()->stream(function () use ($groupId, $request) {
            $lastSeenId = $request->query('last_id', null);

            while (true) {
                if (connection_aborted()) {
                    break;
                }

                $query = Message::where('group_id', $groupId);
                if ($lastSeenId) {
                    $lastMsg = Message::find($lastSeenId);
                    if ($lastMsg) {
                        $query->where(function ($q) use ($lastMsg) {
                            $q->where('created_at_ms', '>', $lastMsg->created_at_ms)
                              ->orWhere(function ($q2) use ($lastMsg) {
                                  $q2->where('created_at_ms', '=', $lastMsg->created_at_ms)
                                     ->where('id', '>', $lastMsg->id);
                              });
                        });
                    }
                }
                
                $newMessages = $query->with('sender')->orderBy('created_at_ms', 'asc')->get();

                if ($newMessages->isNotEmpty()) {
                    foreach ($newMessages as $msg) {
                        if ($msg->content === '__SYSTEM_SYNC_SCHEDULE__') {
                            echo "data: " . json_encode([
                                'type' => 'sync_schedule',
                                'group_id' => $groupId,
                                'id' => $msg->id
                            ]) . "\n\n";
                        } elseif ($msg->content === '__SYSTEM_SYNC_TASK__') {
                            echo "data: " . json_encode([
                                'type' => 'sync_task',
                                'group_id' => $groupId,
                                'id' => $msg->id
                            ]) . "\n\n";
                        } else {
                            echo "data: " . json_encode([
                                'type' => 'message',
                                'message' => $msg,
                                'id' => $msg->id
                            ]) . "\n\n";
                        }
                    }
                    if (ob_get_level() > 0) {
                        ob_flush();
                    }
                    flush();
                    $lastSeenId = $newMessages->last()->id;
                } else {
                    // Send heartbeat comment to check connection status
                    echo ": heartbeat\n\n";
                    if (ob_get_level() > 0) {
                        ob_flush();
                    }
                    flush();
                }

                sleep(1); // Poll database every 1 second
            }
        }, 200, [
            'Content-Type' => 'text/event-stream',
            'Cache-Control' => 'no-cache',
            'Connection' => 'keep-alive',
            'X-Accel-Buffering' => 'no', // Disable buffering for Nginx
        ]);
    }
}
