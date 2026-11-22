<?php

namespace App\Http\Controllers;

use Illuminate\Foundation\Auth\Access\AuthorizesRequests;
use Illuminate\Foundation\Validation\ValidatesRequests;
use Illuminate\Routing\Controller as RoutingController;

/**
 * BaseController - Standard API Response Wrapper
 * 
 * All controllers should extend this to ensure consistent response format
 * across the entire FocusBoard API.
 */
class BaseController extends RoutingController
{
    use AuthorizesRequests, ValidatesRequests;

    /**
     * Success Response Wrapper
     * 
     * Standard format:
     * {
     *   "success": true,
     *   "data": {...},
     *   "message": "..."
     * }
     */
    protected function successResponse($data = null, $message = 'Success', $statusCode = 200)
    {
        return response()->json([
            'success' => true,
            'data' => $data,
            'message' => $message,
        ], $statusCode);
    }

    /**
     * Error Response Wrapper
     * 
     * Standard format:
     * {
     *   "success": false,
     *   "data": null,
     *   "message": "...",
     *   "errors": {...}
     * }
     */
    protected function errorResponse($message = 'Error', $errors = null, $statusCode = 400)
    {
        return response()->json([
            'success' => false,
            'data' => null,
            'message' => $message,
            'errors' => $errors,
        ], $statusCode);
    }

    /**
     * Paginated Response Wrapper
     * 
     * For list endpoints returning paginated results
     */
    protected function paginatedResponse($items, $total, $per_page, $current_page, $message = 'Success')
    {
        return response()->json([
            'success' => true,
            'data' => $items,
            'pagination' => [
                'total' => $total,
                'per_page' => $per_page,
                'current_page' => $current_page,
                'last_page' => ceil($total / $per_page),
            ],
            'message' => $message,
        ], 200);
    }
}
