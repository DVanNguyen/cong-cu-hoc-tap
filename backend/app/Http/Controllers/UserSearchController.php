<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\Request;

class UserSearchController extends BaseController
{
    /**
     * Search users by name or email.
     * Used for adding group members, finding users to invite, etc.
     *
     * GET /api/users/search?q=john
     */
    public function search(Request $request)
    {
        $query = $request->query('q', '');

        if (strlen($query) < 2) {
            return $this->errorResponse('Query must be at least 2 characters', null, 400);
        }

        $users = User::where('name', 'like', "%{$query}%")
            ->orWhere('email', 'like', "%{$query}%")
            ->select('id', 'name', 'email')
            ->limit(20)
            ->get();

        return $this->successResponse($users, 'Users retrieved successfully');
    }

    /**
     * Get user by ID
     * GET /api/users/{id}
     */
    public function show(User $user)
    {
        return $this->successResponse($user->only('id', 'name', 'email'), 'User retrieved successfully');
    }
}
