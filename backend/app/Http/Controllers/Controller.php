<?php

namespace App\Http\Controllers;

use Illuminate\Foundation\Auth\Access\AuthorizesRequests;
use Illuminate\Foundation\Validation\ValidatesRequests;
use Illuminate\Routing\Controller as BaseControllerBase;

/**
 * Base Controller - Standardized JSON Response Handler
 * 
 * ✅ ALL endpoints MUST return wrapped responses:
 * { "success": true, "data": { ... }, "message": "..." }
 */
abstract class Controller extends BaseControllerBase
{
    use AuthorizesRequests, ValidatesRequests;

    /**
     * ✅ Standard success response wrapper
     * 
     * @param mixed $data - The response data
     * @param string $message - Optional message
     * @param int $statusCode - HTTP status code
     * @return \Illuminate\Http\JsonResponse
     */
    protected function successResponse($data = null, string $message = 'Success', int $statusCode = 200)
    {
        return response()->json([
            'success' => true,
            'data' => $data,
            'message' => $message,
        ], $statusCode);
    }

    /**
     * ✅ Standard error response wrapper
     * 
     * @param string $message - Error message
     * @param mixed $errors - Validation errors (optional)
     * @param int $statusCode - HTTP status code
     * @return \Illuminate\Http\JsonResponse
     */
    protected function errorResponse(string $message = 'Error', $errors = null, int $statusCode = 400)
    {
        $response = [
            'success' => false,
            'data' => null,
            'message' => $message,
        ];

        if ($errors !== null) {
            $response['errors'] = $errors;
        }

        return response()->json($response, $statusCode);
    }
}
