<?php

namespace Tests\Feature;

use App\Models\User;
use App\Models\Group;
use App\Models\GroupMember;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class GroupTest extends TestCase
{
    use RefreshDatabase;

    public function test_user_can_create_group_and_fetch_members()
    {
        // 1. Create a user
        $user = User::factory()->create();

        // 2. Authenticate the user with Sanctum
        Sanctum::actingAs($user);

        // 3. Create group request
        $response = $this->postJson('/api/groups', [
            'name' => 'Test Group',
            'category' => 'study',
            'icon' => 'book',
            'palette_idx' => 1,
        ]);

        $response->assertStatus(201);
        $groupId = $response->json('data.id');
        $this->assertNotEmpty($groupId);

        // 4. Fetch group members
        $membersResponse = $this->getJson("/api/groups/{$groupId}/members");
        $membersResponse->assertStatus(200);

        // The user who created the group should be in the members list
        $membersResponse->assertJsonFragment([
            'id' => $user->id,
            'name' => $user->name,
            'email' => $user->email,
        ]);
    }

    public function test_user_can_add_member_to_group()
    {
        // 1. Create owner and invitee
        $owner = User::factory()->create();
        $invitee = User::factory()->create();

        // 2. Authenticate owner
        Sanctum::actingAs($owner);

        // 3. Create group
        $response = $this->postJson('/api/groups', [
            'name' => 'Study Club',
        ]);
        $groupId = $response->json('data.id');

        // 4. Add the invitee
        $addResponse = $this->postJson("/api/groups/{$groupId}/members", [
            'email' => $invitee->email,
        ]);
        $addResponse->assertStatus(200);

        // 5. Fetch members and check that invitee is now a member
        $membersResponse = $this->getJson("/api/groups/{$groupId}/members");
        $membersResponse->assertStatus(200);

        // Check count of members is 2
        $membersData = $membersResponse->json('data');
        $this->assertCount(2, $membersData);

        // Check invitee is present
        $membersResponse->assertJsonFragment([
            'id' => $invitee->id,
            'name' => $invitee->name,
            'email' => $invitee->email,
        ]);
    }

    public function test_owner_can_kick_member_from_group()
    {
        // 1. Create owner and member
        $owner = User::factory()->create();
        $member = User::factory()->create();

        // 2. Authenticate owner
        Sanctum::actingAs($owner);

        // 3. Create group
        $response = $this->postJson('/api/groups', [
            'name' => 'Design Team',
        ]);
        $groupId = $response->json('data.id');

        // 4. Add member
        $this->postJson("/api/groups/{$groupId}/members", [
            'email' => $member->email,
        ]);

        // 5. Kick the member
        $kickResponse = $this->deleteJson("/api/groups/{$groupId}/members/{$member->id}");
        $kickResponse->assertStatus(200);

        // 6. Verify they are no longer in the group
        $membersResponse = $this->getJson("/api/groups/{$groupId}/members");
        $membersResponse->assertStatus(200);
        $this->assertCount(1, $membersResponse->json('data')); // Only the owner remains
    }
}

