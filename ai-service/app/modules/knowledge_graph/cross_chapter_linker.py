from typing import List, Dict, Any

def build_cross_chapter_links(
    course_id: int,
    occurrences: List[Dict[str, Any]]
) -> Dict[str, Any]:
    """
    occurrences: Danh sách mọi lần xuất hiện của các khái niệm đã qua chuẩn hóa song ngữ:
    [
      {
        "concept_id": 10,
        "canonical_name_vi": "Mạng đối nghịch tạo sinh",
        "canonical_name_en": "Generative Adversarial Network (GAN)",
        "lecture_id": 1,
        "lecture_order": 1,         # Chương 1
        "lecture_title": "Chapter 1: Tổng quan về AI",
        "slide_id": 12,
        "slide_number": 12,
        "timestamp_sec": 255.0,
        "context_excerpt": "Giới thiệu sơ lược về mô hình sinh dữ liệu GAN..."
      },
      {
        "concept_id": 10,
        "canonical_name_vi": "Mạng đối nghịch tạo sinh",
        "canonical_name_en": "Generative Adversarial Network (GAN)",
        "lecture_id": 5,
        "lecture_order": 5,         # Chương 5 nhắc lại!
        "lecture_title": "Chapter 5: Deep Generative Models",
        "slide_id": 108,
        "slide_number": 8,
        "timestamp_sec": 612.0,
        "context_excerpt": "Ứng dụng mạng GAN đã nhắc ở Chương 1 để sinh ảnh..."
      }
    ]
    """
    by_concept: Dict[int, List[Dict[str, Any]]] = {}
    for occ in occurrences:
        cid = int(occ["concept_id"])
        occ["pos_linear_index"] = int(occ["lecture_order"]) * 1000 + int(occ["slide_number"])
        by_concept.setdefault(cid, []).append(occ)

    slide_cross_links: Dict[int, List[Dict[str, Any]]] = {}
    concept_timelines: Dict[int, Dict[str, Any]] = {}

    for cid, occ_list in by_concept.items():
        # Sắp xếp theo thứ tự thời gian bài học tăng dần Pos(L, S)
        occ_list.sort(key=lambda x: x["pos_linear_index"])
        origin = occ_list[0]  # Điểm xuất hiện đầu tiên (VD: Chương 1, Slide 12)

        concept_timelines[cid] = {
            "concept_id": cid,
            "canonical_name_vi": origin["canonical_name_vi"],
            "canonical_name_en": origin["canonical_name_en"],
            "origin_lecture_id": origin["lecture_id"],
            "origin_lecture_order": origin["lecture_order"],
            "origin_slide_id": origin["slide_id"],
            "origin_slide_number": origin["slide_number"],
            "origin_timestamp_sec": origin.get("timestamp_sec", 0.0),
            "total_chapters_spanning": len({x["lecture_order"] for x in occ_list}),
            "all_occurrences": occ_list,
        }

        # Với mọi lần xuất hiện ở các chương/bài sau (VD: Chương 5), tạo Back-Reference Link trỏ về Chương 1
        for later_occ in occ_list[1:]:
            curr_slide_id = int(later_occ["slide_id"])
            if later_occ["lecture_order"] != origin["lecture_order"] or later_occ["slide_number"] != origin["slide_number"]:
                slide_cross_links.setdefault(curr_slide_id, []).append({
                    "link_direction": "BACK_TO_ORIGIN",
                    "concept_id": cid,
                    "concept_name_vi": origin["canonical_name_vi"],
                    "concept_name_en": origin["canonical_name_en"],
                    "current_lecture_order": later_occ["lecture_order"],
                    "current_slide_number": later_occ["slide_number"],
                    "target_lecture_id": origin["lecture_id"],
                    "target_lecture_order": origin["lecture_order"],
                    "target_lecture_title": origin.get("lecture_title", f"Chương {origin['lecture_order']}"),
                    "target_slide_id": origin["slide_id"],
                    "target_slide_number": origin["slide_number"],
                    "target_timestamp_sec": origin.get("timestamp_sec", 0.0),
                    "badge_text": (
                        f"Liên kết từ Chương {origin['lecture_order']} "
                        f"(Slide {origin['slide_number']}) — Bấm để xem lại kiến thức gốc"
                    ),
                })

                # Đồng thời gắn Forward-Link tại trang Slide gốc ở Chương 1 để biết khái niệm này sẽ gặp lại ở Chương 5
                origin_slide_id = int(origin["slide_id"])
                slide_cross_links.setdefault(origin_slide_id, []).append({
                    "link_direction": "FORWARD_TO_LATER_CHAPTER",
                    "concept_id": cid,
                    "concept_name_vi": origin["canonical_name_vi"],
                    "concept_name_en": origin["canonical_name_en"],
                    "current_lecture_order": origin["lecture_order"],
                    "current_slide_number": origin["slide_number"],
                    "target_lecture_id": later_occ["lecture_id"],
                    "target_lecture_order": later_occ["lecture_order"],
                    "target_lecture_title": later_occ.get("lecture_title", f"Chương {later_occ['lecture_order']}"),
                    "target_slide_id": later_occ["slide_id"],
                    "target_slide_number": later_occ["slide_number"],
                    "target_timestamp_sec": later_occ.get("timestamp_sec", 0.0),
                    "badge_text": (
                        f"Được nhắc lại ở Chương {later_occ['lecture_order']} "
                        f"(Slide {later_occ['slide_number']}) — Bấm để chuyển tới"
                    ),
                })

    return {
        "course_id": course_id,
        "slide_cross_links": slide_cross_links,
        "concept_timelines": concept_timelines,
    }
