from typing import List, Dict, Any

def align_transcript_with_manual_clicks(
    transcript_segments: List[Dict[str, Any]],
    slide_intervals: List[Dict[str, Any]],
    boundary_tolerance_sec: float = 1.5
) -> List[Dict[str, Any]]:
    """
    Căn chỉnh từng đoạn transcript vào trang slide dựa trên lịch sử chuyển slide.
    slide_intervals: [{"slide_number": 1, "start_sec": 0.0, "end_sec": 42.5}, ...]
    """
    if not slide_intervals:
        raise ValueError("Danh sách mốc chuyển slide (slide_intervals) không được rỗng.")

    aligned_results = []

    for seg in transcript_segments:
        u_start = float(seg["start_sec"])
        u_end = float(seg["end_sec"])
        u_duration = max(0.01, u_end - u_start)
        u_mid = (u_start + u_end) / 2.0

        best_slide = None
        best_overlap = 0.0

        # 1. Tính độ giao thoa thời gian (Temporal Intersection) với từng khoảng mở slide
        for interval in slide_intervals:
            s_start = float(interval["start_sec"])
            s_end = float(interval["end_sec"])
            overlap = max(0.0, min(u_end, s_end) - max(u_start, s_start))

            if overlap > best_overlap:
                best_overlap = overlap
                best_slide = int(interval["slide_number"])

        # 2. Nếu câu nói nằm ngoài hoặc ngay sát rìa (overlap == 0), gán vào slide gần trung điểm nhất
        if best_slide is None or best_overlap == 0.0:
            nearest = min(
                slide_intervals,
                key=lambda iv: min(abs(u_mid - float(iv["start_sec"])), abs(u_mid - float(iv["end_sec"])))
            )
            best_slide = int(nearest["slide_number"])
            confidence = 0.65
        else:
            overlap_ratio = best_overlap / u_duration
            # Nếu câu nói nằm trọn trong thời gian mở slide -> confidence = 1.0
            # Nếu câu nói bị vắt ngang qua mốc bấm chuyển slide -> confidence giảm nhẹ theo tỉ lệ
            confidence = round(min(1.0, 0.5 + 0.5 * overlap_ratio), 4)

        item = dict(seg)
        item["aligned_slide_number"] = best_slide
        item["alignment_source"] = "manual_click"
        item["alignment_confidence"] = confidence
        aligned_results.append(item)

    return aligned_results
