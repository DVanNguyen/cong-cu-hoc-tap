<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Controllers\BaseController;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class AiController extends BaseController
{
    public function generateSubtasks(Request $request)
    {
        $validated = $request->validate([
            'title' => 'required|string',
            'description' => 'nullable|string',
            'expected_subtasks_count' => 'nullable|integer'
        ]);

        $count = $validated['expected_subtasks_count'] ?? 4;
        if ($count < 1) $count = 1;
        if ($count > 12) $count = 12;

        $apiKey = env('GEMINI_API_KEY');
        
        $prompt = "Bạn là một trợ lý quản lý dự án xuất sắc. Hãy chia nhỏ công việc lớn sau đây thành các công việc nhỏ hơn. ";
        $prompt .= "Công việc chính: {$validated['title']}. ";
        if (!empty($validated['description'])) {
            $prompt .= "Mô tả chi tiết: {$validated['description']}. ";
        }
        $prompt .= "Hãy đề xuất đúng {$count} công việc/giai đoạn cụ thể tương ứng từng tuần. Chỉ trả về dữ liệu tuân thủ đúng schema JSON đã định nghĩa là một mảng chuỗi (JSON array of strings), tuyệt đối không giải thích thêm.";

        $payload = [
            'contents' => [
                [
                    'parts' => [
                        ['text' => $prompt]
                    ]
                ]
            ],
            'generationConfig' => [
                'responseMimeType' => 'application/json',
                'responseSchema' => [
                    'type' => 'ARRAY',
                    'items' => [
                        'type' => 'STRING'
                    ]
                ]
            ]
        ];

        if ($apiKey) {
            $modelsToTry = ['gemini-3.6-flash', 'gemini-3.5-flash', 'gemini-flash-latest'];
            foreach ($modelsToTry as $model) {
                try {
                    $url = "https://generativelanguage.googleapis.com/v1beta/models/{$model}:generateContent?key={$apiKey}";
                    $response = Http::timeout(10)->post($url, $payload);
                    
                    if ($response->successful()) {
                        $jsonResponse = $response->json();
                        $parts = $jsonResponse['candidates'][0]['content']['parts'] ?? [];
                        $textResult = '';
                        foreach ($parts as $part) {
                            if (isset($part['text']) && !empty($part['text'])) {
                                $textResult = $part['text'];
                                break;
                            }
                        }
                        
                        $subtasks = json_decode($textResult, true);
                        if (is_array($subtasks) && count($subtasks) > 0) {
                            return $this->successResponse($subtasks, 'Subtasks generated successfully');
                        }
                    } else {
                        Log::warning("Gemini model {$model} failed: " . $response->body());
                    }
                } catch (\Exception $e) {
                    Log::warning("Gemini model {$model} exception: " . $e->getMessage());
                }
            }
        }

        // ── Smart Fallback Project Breakdown Generator ──
        $fallbackSubtasks = $this->generateFallbackSubtasks($validated['title'], $count);
        return $this->successResponse($fallbackSubtasks, 'Subtasks generated using smart project planner template');
    }

    private function generateFallbackSubtasks(string $title, int $count): array
    {
        $templates = [
            1 => [
                "Lên kế hoạch và thực hiện toàn bộ: {$title}"
            ],
            2 => [
                "Giai đoạn 1: Lên kế hoạch, thiết kế và chuẩn bị cho {$title}",
                "Giai đoạn 2: Triển khai thực hiện, kiểm tra và hoàn thành {$title}"
            ],
            3 => [
                "Tuần 1: Nghiên cứu, thu thập yêu cầu và phác thảo giải pháp cho {$title}",
                "Tuần 2: Tiến hành xây dựng, phát triển nội dung và tính năng chính",
                "Tuần 3: Kiểm thử, tinh chỉnh và nghiệm thu kết quả {$title}"
            ],
            4 => [
                "Tuần 1: Khảo sát, phân tích yêu cầu và lập kế hoạch chi tiết cho {$title}",
                "Tuần 2: Thiết kế giao diện / cấu trúc và chuẩn bị tài nguyên",
                "Tuần 3: Lập trình phát triển các module và tính năng trọng tâm",
                "Tuần 4: Kiểm thử, sửa lỗi, hoàn thiện tài liệu và bàn giao dự án"
            ],
            5 => [
                "Tuần 1: Xác định mục tiêu, phạm vi và xây dựng kế hoạch dự án",
                "Tuần 2: Thiết kế kiến trúc và chuẩn bị môi trường triển khai",
                "Tuần 3: Phát triển các tính năng cốt lõi (Core Features)",
                "Tuần 4: Tích hợp hệ thống, kiểm thử chất lượng và tối ưu",
                "Tuần 5: Đóng gói, viết tài liệu hướng dẫn và nghiệm thu"
            ],
            6 => [
                "Tuần 1: Khảo sát yêu cầu và thiết lập mục tiêu ban đầu",
                "Tuần 2: Thiết kế khung sườn (Wireframe/Architecture)",
                "Tuần 3: Triển khai giai đoạn 1 của {$title}",
                "Tuần 4: Triển khai giai đoạn 2 và hoàn thiện tính năng mở rộng",
                "Tuần 5: Kiểm thử chuyên sâu (Unit Test & Integration Test)",
                "Tuần 6: Tối ưu hiệu năng, bảo mật và báo cáo tổng kết"
            ]
        ];

        if (isset($templates[$count])) {
            return $templates[$count];
        }

        // Generic generator for N weeks
        $results = [];
        for ($i = 1; $i <= $count; $i++) {
            $phase = match (true) {
                $i === 1 => "Khởi động & Lên kế hoạch chi tiết",
                $i <= (int)($count * 0.4) => "Nghiên cứu & Thiết kế giải pháp",
                $i <= (int)($count * 0.75) => "Tiến hành triển khai giai đoạn " . ($i - 1),
                $i < $count => "Kiểm thử, đánh giá tiến độ và sửa lỗi",
                default => "Hoàn thiện, đánh giá kết quả và tổng kết dự án"
            };
            $results[] = "Tuần {$i}: {$phase} cho {$title}";
        }
        return $results;
    }
}
