<?php

namespace App\Http\Controllers;

use App\Models\Group;
use App\Models\GroupMember;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class GroupController extends BaseController
{
    /**
     * Get all groups the user belongs to
     */
    public function index(Request $request)
    {
        $userId = $request->user()->id;
        $groups = Group::whereHas('members', function ($query) use ($userId) {
            $query->where('users.id', $userId);
        })->with('owner')->get();

        return $this->successResponse($groups, 'Groups retrieved successfully');
    }

    /**
     * Create a new group
     */
    public function store(Request $request)
    {
        $validated = $request->validate([
            'id' => 'sometimes|string|uuid',
            'name' => 'required|string|max:255',
            'category' => 'sometimes|string|max:255',
            'icon' => 'sometimes|string|max:255',
            'palette_idx' => 'sometimes|integer',
            'paletteIdx' => 'sometimes|integer',
        ]);

        $userId = $request->user()->id;
        $groupId = $validated['id'] ?? (string) Str::uuid();
        $paletteIdx = $validated['palette_idx'] ?? $validated['paletteIdx'] ?? 0;

        DB::beginTransaction();
        try {
            $group = Group::create([
                'id' => $groupId,
                'name' => $validated['name'],
                'owner_id' => $userId,
                'category' => $validated['category'] ?? 'work',
                'icon' => $validated['icon'] ?? 'briefcase',
                'palette_idx' => $paletteIdx,
            ]);

            GroupMember::create([
                'group_id' => $groupId,
                'user_id' => $userId,
                'role' => 'OWNER',
            ]);

            DB::commit();
            return $this->successResponse($group->load('owner'), 'Group created successfully', 201);
        } catch (\Exception $e) {
            DB::rollBack();
            return $this->errorResponse('Failed to create group: ' . $e->getMessage(), null, 500);
        }
    }

    /**
     * Add member to a group
     */
    public function addMember(Request $request, string $groupId)
    {
        $group = Group::findOrFail($groupId);

        // Ensure current user is the owner or member
        $userId = $request->user()->id;
        $isMember = GroupMember::where('group_id', $groupId)
            ->where('user_id', $userId)
            ->exists();

        if (!$isMember) {
            return $this->errorResponse('You are not a member of this group', null, 403);
        }

        $validated = $request->validate([
            'email' => 'required|email|exists:users,email',
        ]);

        $newMember = User::where('email', $validated['email'])->firstOrFail();

        // Check if already in group
        $exists = GroupMember::where('group_id', $groupId)
            ->where('user_id', $newMember->id)
            ->exists();

        if ($exists) {
            return $this->errorResponse('User is already a member of this group', null, 400);
        }

        $member = GroupMember::create([
            'group_id' => $groupId,
            'user_id' => $newMember->id,
            'role' => 'MEMBER',
        ]);

        return $this->successResponse($member, 'Member added successfully');
    }

    /**
     * Get all members in a group
     */
    public function members(Request $request, string $groupId)
    {
        $group = Group::findOrFail($groupId);

        // Ensure caller belongs to the group
        $userId = $request->user()->id;
        $isMember = GroupMember::where('group_id', $groupId)
            ->where('user_id', $userId)
            ->exists();

        if (!$isMember) {
            return $this->errorResponse('Unauthorized access to group members', null, 403);
        }

        $members = User::whereHas('groups', function ($q) use ($groupId) {
            $q->where('groups.id', $groupId);
        })->get();

        return $this->successResponse($members, 'Group members retrieved successfully');
    }

    /**
     * Remove a member from a group (Admin only)
     */
    public function removeMember(Request $request, string $groupId, string $memberId)
    {
        $group = Group::findOrFail($groupId);
        $userId = $request->user()->id;

        // Ensure caller is the owner
        if ($group->owner_id !== $userId) {
            return $this->errorResponse('Only the group owner can remove members', null, 403);
        }

        // Prevent owner from removing themselves
        if ($group->owner_id === $memberId) {
            return $this->errorResponse('Owner cannot be removed from the group', null, 400);
        }

        $member = GroupMember::where('group_id', $groupId)
            ->where('user_id', $memberId)
            ->first();

        if (!$member) {
            return $this->errorResponse('Member not found in this group', null, 404);
        }

        $member->delete();

        return $this->successResponse(null, 'Member removed successfully');
    }

    /**
     * Dissolve or leave a group
     */
    public function destroy(Request $request, string $groupId)
    {
        $group = Group::findOrFail($groupId);
        $userId = $request->user()->id;

        $member = GroupMember::where('group_id', $groupId)
            ->where('user_id', $userId)
            ->firstOrFail();

        if ($group->owner_id === $userId) {
            // Owner dissolves group
            $group->delete();
            return $this->successResponse(null, 'Group dissolved successfully');
        } else {
            // Member leaves group
            $member->delete();
            return $this->successResponse(null, 'Left group successfully');
        }
    }
    /**
     * Transfer admin (owner) role to another member
     */
    public function transferAdmin(Request $request, string $groupId)
    {
        $group = Group::findOrFail($groupId);
        $userId = $request->user()->id;

        // Ensure caller is the owner
        if ($group->owner_id !== $userId) {
            return $this->errorResponse('Only the group owner can transfer ownership', null, 403);
        }

        $validated = $request->validate([
            'new_owner_id' => 'required|uuid|exists:users,id',
        ]);

        $newOwnerId = $validated['new_owner_id'];

        // Ensure new owner is already a member
        $isMember = GroupMember::where('group_id', $groupId)
            ->where('user_id', $newOwnerId)
            ->exists();

        if (!$isMember) {
            return $this->errorResponse('The designated user is not a member of this group', null, 400);
        }

        DB::beginTransaction();
        try {
            // Demote current owner
            GroupMember::where('group_id', $groupId)
                ->where('user_id', $userId)
                ->update(['role' => 'MEMBER']);

            // Promote new owner
            GroupMember::where('group_id', $groupId)
                ->where('user_id', $newOwnerId)
                ->update(['role' => 'OWNER']);

            // Update group's owner_id
            $group->update(['owner_id' => $newOwnerId]);

            DB::commit();
            return $this->successResponse(null, 'Ownership transferred successfully');
        } catch (\Exception $e) {
            DB::rollBack();
            return $this->errorResponse('Failed to transfer ownership: ' . $e->getMessage(), null, 500);
        }
    }
}
