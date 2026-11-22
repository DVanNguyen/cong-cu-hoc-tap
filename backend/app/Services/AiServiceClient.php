<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class AiServiceClient
{
    protected string $baseUrl;

    public function __construct()
    {
        $this->baseUrl = config('services.ai_service.url', 'http://127.0.0.1:8001/api/v1');
    }

    /**
     * Kiểm tra trạng thái AI Service
     */
    public function healthCheck(): ?array
    {
        try {
            $response = Http::timeout(3)->get('http://127.0.0.1:8001/health');
            return $response->successful() ? $response->json() : null;
        } catch (\Exception $e) {
            Log::error('AI Service health check failed: ' . $e->getMessage());
            return null;
        }
    }

    /**
     * Trích xuất deadline từ tin nhắn (Telegram / Email / Lời nói)
     */
    public function extractDeadline(string $text, string $source, string $receivedAt, ?string $courseHint = 'Trí tuệ nhân tạo'): ?array
    {
        try {
            $response = Http::timeout(10)->post("{$this->baseUrl}/deadlines/extract", [
                'text' => $text,
                'source' => $source,
                'received_at' => $receivedAt,
                'course_hint' => $courseHint,
            ]);

            return $response->successful() ? $response->json() : null;
        } catch (\Exception $e) {
            Log::error('AI Service extractDeadline error: ' . $e->getMessage());
            return null;
        }
    }

    /**
     * Tính mức cấp bách và sắp xếp công việc theo EDF
     */
    public function rankTasks(array $tasks): array
    {
        try {
            $response = Http::timeout(5)->post("{$this->baseUrl}/scheduler/rank", $tasks);
            return $response->successful() ? $response->json() : [];
        } catch (\Exception $e) {
            Log::error('AI Service rankTasks error: ' . $e->getMessage());
            return [];
        }
    }

    /**
     * Đọc slide PDF từ đường dẫn file
     */
    public function parseSlides(string $pdfPath): array
    {
        try {
            $response = Http::timeout(15)->post("{$this->baseUrl}/lectures/parse-slides", [
                'pdf_path' => $pdfPath,
            ]);

            return $response->successful() ? $response->json() : [];
        } catch (\Exception $e) {
            Log::error('AI Service parseSlides error: ' . $e->getMessage());
            return [];
        }
    }

    /**
     * Căn lời giảng với slide và tính Novelty Score
     */
    public function alignLecture(array $transcriptSegments, array $slides, ?array $clicks = null): array
    {
        try {
            $response = Http::timeout(20)->post("{$this->baseUrl}/lectures/align", [
                'transcript_segments' => $transcriptSegments,
                'slides' => $slides,
                'user_slide_clicks' => $clicks,
            ]);

            return $response->successful() ? $response->json() : [];
        } catch (\Exception $e) {
            Log::error('AI Service alignLecture error: ' . $e->getMessage());
            return [];
        }
    }

    /**
     * Kiểm tra tính đúng đắn của Đồ thị tri thức (DAG)
     */
    public function validateGraph(array $concepts, array $edges): ?array
    {
        try {
            $response = Http::timeout(5)->post("{$this->baseUrl}/graph/validate", [
                'concepts' => $concepts,
                'edges' => $edges,
            ]);

            return $response->successful() ? $response->json() : null;
        } catch (\Exception $e) {
            Log::error('AI Service validateGraph error: ' . $e->getMessage());
            return null;
        }
    }
}
