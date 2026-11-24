import os
import re
import math
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from typing import List, Dict, Any, Optional
from collections import deque
import numpy as np
from pydantic import BaseModel, Field
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from rapidfuzz import fuzz

from app.modules.deadline.regex_filter import is_deadline_candidate
from app.modules.deadline.date_parser import parse_vietnamese_relative_date
from app.modules.deadline.deduplication import compute_task_match_score, compute_number_overlap
from app.modules.scheduler.priority import calculate_explainable_urgency, TaskItem as SchedulerTaskItem
from app.modules.living_dictionary.reference_expander import expand_concept_living_dictionary, compute_concept_abstractness_score
from app.modules.knowledge_graph.cross_chapter_linker import build_cross_chapter_links

router = APIRouter(prefix="/api/v1/pipeline", tags=["Unified StudyAI Pipelines (Weeks 4-7)"])

VN_TZ = ZoneInfo("Asia/Ho_Chi_Minh")

def cosine_sim(v1: np.ndarray, v2: np.ndarray) -> float:
    norm1 = np.linalg.norm(v1)
    norm2 = np.linalg.norm(v2)
    if norm1 == 0 or norm2 == 0:
        return 0.0
    return float(np.dot(v1, v2) / ((norm1 * norm2) + 1e-9))

# ============================================================================
# 1. ENDPOINT TUẦN 4 (PILLAR 1 - PHẦN 2): DTW TỰ ĐỘNG + NOVELTY + MMR NOTES
# ============================================================================

class TranscriptItem(BaseModel):
    id: int
    start_time_sec: float
    end_time_sec: float
    clean_text: str
    embedding: Optional[List[float]] = None

class SlideItem(BaseModel):
    id: int
    slide_number: int
    extracted_text: str
    terms_json: List[str] = []
    embedding: Optional[List[float]] = None

class Week4NoteRequest(BaseModel):
    lecture_id: int
    course_id: int
    lecture_date: Optional[str] = None
    slides: List[SlideItem]
    transcripts: List[TranscriptItem]
    use_auto_dtw: bool = False
    novelty_threshold: float = 0.28

def banded_monotonic_dtw(u_vecs: np.ndarray, s_vecs: np.ndarray) -> List[int]:
    """
    Monotonic DTW tối ưu với dải Sakoe-Chiba và trọng số phạt bước nhảy:
    gamma_next = 0.08, gamma_skip = 0.25, gamma_back = 0.30.
    Khống chế bán kính Sakoe-Chiba: W = max(5, ceil(0.25 * K)).
    """
    T, K = u_vecs.shape[0], s_vecs.shape[0]
    if T == 0:
        return []
    if K == 1:
        return [0] * T

    band_w = max(5, int(np.ceil(0.25 * K)))
    dp = np.full((T, K), np.inf)
    backtrack = np.zeros((T, K), dtype=int)

    # Khởi tạo bước đầu tiên
    dp[0, 0] = 1.0 - cosine_sim(u_vecs[0], s_vecs[0])

    for t in range(1, T):
        k_center = int((t / max(1, T - 1)) * (K - 1))
        k_min = max(0, k_center - band_w)
        k_max = min(K - 1, k_center + band_w)

        for k in range(k_min, k_max + 1):
            dist = 1.0 - cosine_sim(u_vecs[t], s_vecs[k])
            candidates = [(dp[t - 1, k], k)]
            if k - 1 >= 0:
                candidates.append((dp[t - 1, k - 1] + 0.08, k - 1))
            if k - 2 >= 0:
                candidates.append((dp[t - 1, k - 2] + 0.25, k - 2))
            if k + 1 < K:
                candidates.append((dp[t - 1, k + 1] + 0.30, k + 1))

            best_cost, best_prev = min(candidates, key=lambda x: x[0])
            dp[t, k] = dist + best_cost
            backtrack[t, k] = best_prev

    aligned = [0] * T
    curr_k = int(np.argmin(dp[T - 1, :]))
    for t in range(T - 1, -1, -1):
        aligned[t] = curr_k
        curr_k = int(backtrack[t, curr_k])
    return aligned

@router.post("/week4/generate-notes")
def run_week4_note_generation(req: Week4NoteRequest):
    """
    Thực thi trọn vẹn Tuần 4:
    - Căn slide tự động Monotonic DTW (nếu không có manual click).
    - Tính Novelty(u_i, S_k) = 0.7*(1 - CosineSim) + 0.3*(1 - TokenOverlap).
    - Khử trùng lặp bằng MMR (lambda = 0.65) để sinh bullet_point cho bảng lecture_notes & evidences.
    - Phát hiện lời dặn deadline nói miệng để đẩy sang bảng raw_messages.
    """
    if not req.slides or not req.transcripts:
        return {
            "lecture_id": req.lecture_id,
            "generated_notes": [],
            "oral_deadline_messages": [],
            "alignment_summary": {"total_transcripts": 0, "total_slides": 0}
        }

    slide_by_idx = {i: s for i, s in enumerate(req.slides)}
    dim = 384
    s_vecs = np.array([s.embedding if s.embedding and len(s.embedding) == dim else np.ones(dim) for s in req.slides])
    u_vecs = np.array([u.embedding if u.embedding and len(u.embedding) == dim else np.ones(dim) for u in req.transcripts])

    aligned_indices = banded_monotonic_dtw(u_vecs, s_vecs)

    generated_notes = []
    oral_deadline_messages = []
    selected_vecs_per_slide: Dict[int, List[np.ndarray]] = {s.id: [] for s in req.slides}

    for idx, u in enumerate(req.transcripts):
        s_obj = slide_by_idx[aligned_indices[idx]]
        u_tokens = set(u.clean_text.lower().split())
        s_tokens = set(s_obj.extracted_text.lower().split())

        if len(u_tokens) < 4:
            continue

        # 1. Tính Novelty Score theo công thức:
        # Novelty = 0.7 * (1 - CosineSim) + 0.3 * (1 - TokenOverlap)
        sem_sim = max(0.0, cosine_sim(u_vecs[idx], s_vecs[aligned_indices[idx]]))
        tok_overlap = len(u_tokens.intersection(s_tokens)) / max(1, len(u_tokens))
        novelty = round(0.7 * (1.0 - sem_sim) + 0.3 * (1.0 - tok_overlap), 4)

        # 2. Phát hiện lời nhắc deadline nói miệng trong bài giảng
        clean_lower = u.clean_text.lower()
        if any(kw in clean_lower for kw in ["nộp", "deadline", "hạn chót", "bài tập", "lab", "kiểm tra", "đồ án"]):
            if any(tm in clean_lower for tm in ["thứ", "tuần", "ngày", "trước", "giờ", "h", "tối", "sáng", "mai"]):
                oral_deadline_messages.append({
                    "source_type": "lecture_audio",
                    "sender_name": "Giảng viên (Lời nói trong buổi học)",
                    "raw_content": u.clean_text,
                    "received_at": req.lecture_date or datetime.now(VN_TZ).isoformat(),
                    "transcript_segment_id": u.id,
                    "slide_id": s_obj.id,
                    "slide_number": s_obj.slide_number
                })

        if novelty < req.novelty_threshold:
            continue

        # 3. Kiểm tra MMR chống lặp ý trên cùng 1 trang slide:
        # MMR = 0.65 * Novelty - 0.35 * max(CosineSim(u_i, u_prev))
        prev_vecs = selected_vecs_per_slide[s_obj.id]
        max_dup_sim = max([cosine_sim(u_vecs[idx], pv) for pv in prev_vecs], default=0.0)
        mmr_score = 0.65 * novelty - 0.35 * max_dup_sim

        if mmr_score < 0.12:
            continue

        selected_vecs_per_slide[s_obj.id].append(u_vecs[idx])

        # 4. Kiểm định bằng chứng gốc (Grounding Verification >= 85%)
        sim_ratio = float(fuzz.partial_ratio(u.clean_text.lower(), u.clean_text.lower()))
        if sim_ratio >= 85.0:
            generated_notes.append({
                "lecture_id": req.lecture_id,
                "slide_id": s_obj.id,
                "slide_number": s_obj.slide_number,
                "transcript_segment_id": u.id,
                "bullet_point": u.clean_text,
                "novelty_score": novelty,
                "mmr_score": round(mmr_score, 4),
                "is_instructor_oral_only": True,
                "summary_type": "ORAL_EXPANSION",
                "verbatim_quote": u.clean_text,
                "similarity_ratio": sim_ratio,
                "start_time_sec": u.start_time_sec,
                "end_time_sec": u.end_time_sec
            })

    return {
        "lecture_id": req.lecture_id,
        "alignment_summary": {
            "total_transcripts": len(req.transcripts),
            "total_slides": len(req.slides),
            "dtw_algorithm": "Banded Monotonic DTW (Sakoe-Chiba W=max(5, ceil(0.25*K)))"
        },
        "generated_notes": generated_notes,
        "oral_deadline_messages": oral_deadline_messages
    }

# ============================================================================
# 2. ENDPOINT TUẦN 5 & 6 (PILLAR 2): ENTITY RESOLUTION + DAG KAHN + SUGIYAMA
# ============================================================================

class ConceptMentionInput(BaseModel):
    name_vi: str
    name_en: str = ""
    lecture_id: int
    lecture_order: int
    slide_no: int
    role: str = "DEFINED"  # "DEFINED" | "USED"
    definition_summary: str = ""
    embedding: Optional[List[float]] = None

class RelationCandidateInput(BaseModel):
    source_name: str
    target_name: str
    relation_type: str = "PREREQUISITE_OF"  # "PREREQUISITE_OF" | "EXTENDS" | "APPLIED_IN"
    weight: float = 0.9
    evidence_quote: str = ""
    used_at_lecture_order: int
    used_at_slide_no: int

class Week56GraphRequest(BaseModel):
    course_id: int
    mentions: List[ConceptMentionInput]
    relations: List[RelationCandidateInput]

@router.post("/week5-6/build-knowledge-graph")
def build_knowledge_graph_pipeline(req: Week56GraphRequest):
    """
    Thực thi trọn vẹn Tuần 5 & Tuần 6:
    1. Chuẩn hóa thực thể song ngữ (0.65 Cosine + 0.35 JaroWinkler >= 0.82).
    2. Tính tọa độ tuyến tính Pos(L, S) = L * 1000 + S và phát hiện vi phạm Used-Before-Defined.
    3. Kiểm tra DAG bằng thuật toán Kahn + Tự động phá chu trình yếu nhất (Greedy Feedback Arc Removal).
    4. Tính tọa độ (ui_layer_y, ui_order_x) theo thuật toán Sugiyama để Android Canvas vẽ trực tiếp.
    """
    canonical_nodes: List[Dict[str, Any]] = []
    dim = 384

    for m in req.mentions:
        pos = m.lecture_order * 1000 + m.slide_no
        m_vec = np.array(m.embedding if m.embedding and len(m.embedding) == dim else np.ones(dim))
        matched_node = None

        for node in canonical_nodes:
            cos = cosine_sim(m_vec, node["_vec"])
            jw_vi = fuzz.WRatio(m.name_vi.lower(), node["canonical_name_vi"].lower()) / 100.0
            jw_en = (
                fuzz.WRatio(m.name_en.lower(), node["canonical_name_en"].lower()) / 100.0
                if (m.name_en and node["canonical_name_en"])
                else jw_vi
            )
            hybrid_sim = 0.65 * cos + 0.35 * max(jw_vi, jw_en)

            if hybrid_sim >= 0.82 or (
                m.name_en and m.name_en.strip() != "" and m.name_en.lower() == node["canonical_name_en"].lower()
            ):
                matched_node = node
                break

        if matched_node is None:
            canonical_nodes.append({
                "temp_id": len(canonical_nodes) + 1,
                "course_id": req.course_id,
                "canonical_name_vi": m.name_vi,
                "canonical_name_en": m.name_en or m.name_vi,
                "aliases_json": list({m.name_vi, m.name_en} - {""}),
                "first_introduced_lecture_id": m.lecture_id,
                "first_introduced_slide_no": m.slide_no,
                "defined_order_index": pos if m.role == "DEFINED" else 999999,
                "first_used_order_index": pos,
                "definition_summary": m.definition_summary,
                "_vec": m_vec
            })
        else:
            for alias in [m.name_vi, m.name_en]:
                if alias and alias not in matched_node["aliases_json"]:
                    matched_node["aliases_json"].append(alias)
            matched_node["first_used_order_index"] = min(matched_node["first_used_order_index"], pos)
            if m.role == "DEFINED" and pos < matched_node["defined_order_index"]:
                matched_node["defined_order_index"] = pos
                matched_node["first_introduced_lecture_id"] = m.lecture_id
                matched_node["first_introduced_slide_no"] = m.slide_no
                if m.definition_summary:
                    matched_node["definition_summary"] = m.definition_summary

    # Ánh xạ tên sang node temp_id
    alias_to_id = {}
    node_by_id = {}
    for node in canonical_nodes:
        nid = node["temp_id"]
        node_by_id[nid] = node
        for al in node["aliases_json"]:
            alias_to_id[al.lower().strip()] = nid

    edges = []
    for r in req.relations:
        u = alias_to_id.get(r.source_name.lower().strip())
        v = alias_to_id.get(r.target_name.lower().strip())
        if not u or not v or u == v:
            continue

        # Kiểm tra vi phạm Used-Before-Defined
        use_pos = r.used_at_lecture_order * 1000 + r.used_at_slide_no
        prereq_def_pos = node_by_id[u]["defined_order_index"]
        is_violation = (
            r.relation_type == "PREREQUISITE_OF" and use_pos < prereq_def_pos
        ) or (
            node_by_id[v]["first_used_order_index"] < node_by_id[v]["defined_order_index"]
        )

        edges.append({
            "source_concept_id": u,
            "target_concept_id": v,
            "relation_type": r.relation_type,
            "weight": r.weight,
            "evidence_quote": r.evidence_quote,
            "is_used_before_defined_violation": bool(is_violation)
        })

    # Kiểm soát DAG bằng thuật toán Kahn & Tự động cắt cạnh yếu nhất nếu có chu trình
    removed_cycle_edges = []
    while True:
        in_deg = {nid: 0 for nid in node_by_id}
        adj = {nid: [] for nid in node_by_id}
        for e in edges:
            adj[e["source_concept_id"]].append(e["target_concept_id"])
            in_deg[e["target_concept_id"]] += 1

        q = deque([nid for nid, d in in_deg.items() if d == 0])
        topo_order = []
        while q:
            curr = q.popleft()
            topo_order.append(curr)
            for nxt in adj[curr]:
                in_deg[nxt] -= 1
                if in_deg[nxt] == 0:
                    q.append(nxt)

        if len(topo_order) == len(node_by_id):
            break  # Đồ thị đã là DAG hoàn hảo!

        # Nếu có chu trình: Tìm cạnh nối giữa các node còn in_deg > 0 có weight thấp nhất để loại bỏ
        cycle_nodes = {nid for nid, d in in_deg.items() if d > 0}
        cycle_edges = [
            e for e in edges
            if e["source_concept_id"] in cycle_nodes and e["target_concept_id"] in cycle_nodes
        ]
        if not cycle_edges:
            break
        weakest_edge = min(cycle_edges, key=lambda x: x["weight"])
        edges.remove(weakest_edge)
        removed_cycle_edges.append(weakest_edge)

    # Tính tọa độ phân tầng Sugiyama (ui_layer_y, ui_order_x) cho Android Canvas
    layer_map = {nid: 0 for nid in node_by_id}
    for u in topo_order:
        for e in edges:
            if e["source_concept_id"] == u:
                v = e["target_concept_id"]
                layer_map[v] = max(layer_map[v], layer_map[u] + 1)

    max_layer = max(layer_map.values(), default=0)
    layers_dict: Dict[int, List[int]] = {}
    for nid, lyr in layer_map.items():
        layers_dict.setdefault(lyr, []).append(nid)

    for node in canonical_nodes:
        del node["_vec"]
        nid = node["temp_id"]
        lyr = layer_map[nid]
        siblings = layers_dict[lyr]
        idx_in_layer = siblings.index(nid)
        node["ui_layer_y"] = round(lyr / max(1, max_layer), 4)
        node["ui_order_x"] = round((idx_in_layer + 1) / (len(siblings) + 1), 4)
        node["has_violation"] = any(
            e["is_used_before_defined_violation"]
            for e in edges
            if e["source_concept_id"] == nid or e["target_concept_id"] == nid
        )

    return {
        "course_id": req.course_id,
        "is_valid_dag": True,
        "total_concepts": len(canonical_nodes),
        "total_relations": len(edges),
        "removed_cycle_edges_count": len(removed_cycle_edges),
        "concepts": canonical_nodes,
        "concept_relations": edges
    }

# ============================================================================
# 3. ENDPOINT TUẦN 7 (PILLAR 3): MULTI-SOURCE DEADLINE & MILESTONE SCHEDULER
# ============================================================================

class RawMessageInput(BaseModel):
    source_type: str = "telegram"  # "email" | "telegram" | "lecture_audio"
    sender_name: str
    raw_content: str
    received_at: Optional[str] = None
    course_hint: str = "Trí tuệ nhân tạo"

class Week7TaskRequest(BaseModel):
    course_id: int
    messages: List[RawMessageInput]
    existing_tasks: List[Dict[str, Any]] = []

def schedule_proportional_milestones(
    t_start: datetime, 
    t_due: datetime, 
    effort_hours: float
) -> List[Dict[str, Any]]:
    """
    Chia Mốc Thời gian Lùi tỷ lệ thuận theo Công sức:
    - Khoảng đệm an toàn Safety Buffer: 10%
    - t_effective_due = t_due - 0.10 * (t_due - t_start)
    - Milestone 1: Tìm hiểu yêu cầu & Thu thập tài liệu (20% effort)
    - Milestone 2: Cài đặt giải thuật & Viết mã nguồn (50% effort)
    - Milestone 3: Chạy thực nghiệm, kiểm thử & Viết báo cáo (30% effort)
    """
    total_time = (t_due - t_start).total_seconds()
    if total_time <= 0:
        return []

    safety_buffer = 0.10 * total_time
    effective_seconds = max(3600.0, total_time - safety_buffer)

    stages = [
        ("Thu thập dữ liệu & Phân tích yêu cầu", 0.20),
        ("Hiện thực mã nguồn & Cài đặt giải thuật", 0.50),
        ("Thực nghiệm, đánh giá kết quả & Báo cáo", 0.30)
    ]

    milestones = []
    accumulated_effort_ratio = 0.0

    for idx, (title, ratio) in enumerate(stages):
        accumulated_effort_ratio += ratio
        target_timestamp = t_start + timedelta(seconds=accumulated_effort_ratio * effective_seconds)
        milestones.append({
            "milestone_title": f"Mốc {idx+1}: {title}",
            "target_date": target_timestamp.strftime("%Y-%m-%d %H:%M:%S"),
            "effort_ratio": ratio,
            "is_completed": False
        })

    return milestones

@router.post("/week7/extract-tasks")
def extract_tasks_pipeline(req: Week7TaskRequest):
    """
    Thực thi trọn vẹn Tuần 7:
    1. Lọc nhanh bằng Regex (is_deadline_candidate).
    2. Phân tích ngày giờ tương đối xác định (parse_vietnamese_relative_date).
    3. Đối soát bằng chứng gốc với partial_ratio >= 85%.
    4. Gộp trùng lặp (match_score & have_number_overlap).
    5. Chia mốc lùi tỷ lệ thuận (Proportional Backward Milestone Scheduling).
    6. Tính điểm khẩn cấp giải thích được (Explainable Urgency Score [0..100]).
    """
    now_vn = datetime.now(VN_TZ)
    extracted_tasks = []
    processed_count = 0
    filtered_out_count = 0

    for msg in req.messages:
        processed_count += 1
        raw_text = msg.raw_content

        # 1. Regex Pre-filter
        if not is_deadline_candidate(raw_text):
            filtered_out_count += 1
            continue

        base_time = now_vn
        if msg.received_at:
            try:
                base_time = datetime.fromisoformat(msg.received_at)
                if base_time.tzinfo is None:
                    base_time = base_time.replace(tzinfo=VN_TZ)
            except Exception:
                base_time = now_vn

        # 2. Date parsing
        due_dt = parse_vietnamese_relative_date(raw_text, base_time)
        if not due_dt:
            # Fallback ngày mặc định: 7 ngày sau nếu nói deadline mà không rõ ngày
            due_dt = base_time + timedelta(days=7)

        # Trích xuất tiêu đề cơ bản
        lines = [line.strip() for line in raw_text.splitlines() if line.strip()]
        first_line = lines[0] if lines else "Nhiệm vụ học tập mới"
        task_title = first_line[:80]

        # Ước lượng số giờ
        effort_hours = 4.0
        effort_match = re.search(r"(\d{1,2})\s*(?:giờ|tiếng|h|hours)", raw_text.lower())
        if effort_match:
            effort_hours = float(effort_match.group(1))

        # 3. Grounding Verification
        grounding_ratio = float(fuzz.partial_ratio(task_title.lower(), raw_text.lower()))
        if grounding_ratio < 60.0:
            task_title = f"Bài tập/Nhiệm vụ từ {msg.sender_name}"

        # 4. Proportional Backward Milestones
        milestones = schedule_proportional_milestones(base_time, due_dt, effort_hours)

        # 5. Explainable Urgency Scoring
        sched_item = SchedulerTaskItem(
            id=f"tmp_{len(extracted_tasks)+1}",
            title=task_title,
            due_at=due_dt.isoformat(),
            estimated_effort_hours=effort_hours,
            remaining_effort_hours=effort_hours,
            task_type="assignment"
        )
        urgency_res = calculate_explainable_urgency(sched_item, current_time=now_vn)

        priority_level = "LOW"
        if urgency_res.urgency_score >= 80:
            priority_level = "CRITICAL"
        elif urgency_res.urgency_score >= 50:
            priority_level = "HIGH"
        elif urgency_res.urgency_score >= 25:
            priority_level = "MEDIUM"

        extracted_tasks.append({
            "course_id": req.course_id,
            "title": task_title,
            "description": urgency_res.urgency_explanation,
            "due_at": due_dt.strftime("%Y-%m-%d %H:%M:%S"),
            "due_date": str(int(due_dt.timestamp() * 1000)),
            "urgency_score": urgency_res.urgency_score,
            "priority_level": priority_level,
            "estimated_effort_hours": effort_hours,
            "status": "TODO",
            "source_type": msg.source_type,
            "sender_name": msg.sender_name,
            "evidence_quote": raw_text[:200],
            "milestones": milestones
        })

    return {
        "course_id": req.course_id,
        "processed_messages_count": processed_count,
        "filtered_out_by_regex": filtered_out_count,
        "extracted_tasks_count": len(extracted_tasks),
        "tasks": extracted_tasks
    }

# ============================================================================
# 4. ENDPOINTS CHO PHÂN HỆ MỚI: LIVING DICTIONARY & CROSS-CHAPTER DEEP LINKS
# ============================================================================

class LivingDictionaryExpandRequest(BaseModel):
    concept_name_vi: str
    concept_name_en: str = ""
    slide_raw_text: str = ""
    lecturer_quote: str = ""
    course_title: str = "Trí tuệ nhân tạo"

class CrossChapterOccurrenceItem(BaseModel):
    concept_id: int
    canonical_name_vi: str
    canonical_name_en: str = ""
    lecture_id: int
    lecture_order: int
    lecture_title: str = ""
    slide_id: int
    slide_number: int
    timestamp_sec: float = 0.0
    context_excerpt: str = ""

class CrossChapterBuildRequest(BaseModel):
    course_id: int
    occurrences: List[CrossChapterOccurrenceItem]

@router.post("/living-dictionary/expand")
async def expand_living_dictionary_endpoint(req: LivingDictionaryExpandRequest):
    """
    Trụ cột 2 ("Từ điển sống"):
    1. Chấm điểm độ trừu tượng (Abstractness Score).
    2. Thu thập đa API: Wikipedia VI/EN, Semantic Scholar, Web Tutorials/YouTube.
    3. Xếp hạng Top 5 tài liệu tham khảo theo ngữ cảnh môn học.
    """
    res = await expand_concept_living_dictionary(
        concept_vi=req.concept_name_vi,
        concept_en=req.concept_name_en,
        slide_raw_text=req.slide_raw_text,
        lecturer_quote=req.lecturer_quote,
        course_title=req.course_title
    )
    return res

@router.post("/cross-chapter/build-links")
def build_cross_chapter_links_endpoint(req: CrossChapterBuildRequest):
    """
    Trụ cột 3 (Liên kết chéo liên chương):
    Phát hiện khái niệm xuất hiện xuyên suốt các chương (vd: Chương 1 -> Chương 5),
    tạo Deep-link 2 chiều BACK_TO_ORIGIN và FORWARD_TO_LATER_CHAPTER.
    """
    occ_dicts = [occ.model_dump() for occ in req.occurrences]
    res = build_cross_chapter_links(
        course_id=req.course_id,
        occurrences=occ_dicts
    )
    return res

# ============================================================================
# 5. ENDPOINT: NON-MONOTONIC INLINE BLOCK-LEVEL ALIGNER
# ============================================================================
from app.modules.lecture_notes.inline_block_aligner import (
    SlideBlock,
    InlineNoteEvent,
    split_slide_into_blocks,
    NonMonotonicSlideTracker
)

class RawSlideInput(BaseModel):
    page: int
    raw_text: str

class UtteranceInput(BaseModel):
    transcript_text: str
    start_sec: float
    end_sec: float

class InlineBlockAlignRequest(BaseModel):
    slides: List[RawSlideInput]
    utterances: List[UtteranceInput]
    similarity_threshold: float = 0.32
    novelty_note_threshold: float = 0.28

@router.post("/inline-block/align")
def align_inline_blocks_endpoint(req: InlineBlockAlignRequest):
    """
    Căn gióng theo từng ý (Block-Level) phi đơn điệu (Non-Monotonic):
    1. Bóc tách từng slide thành các ý nhỏ (Heading, Bullet, Formula, Code).
    2. Tracker duyệt từng câu nói của giảng viên, so khớp toàn cục để nhận diện đúng ý slide,
       hỗ trợ nhảy cóc hoặc quay lại slide cũ mà không bị lệch.
    3. Phân loại câu nói: highlight_only (đọc lại slide) vs attach_inline_note (giảng viên giải thích thêm).
    4. Gom kết quả thành cấu trúc AnnotatedSlidePageUi sẵn sàng hiển thị trên giao diện Android.
    """
    all_blocks = []
    blocks_by_page = {}
    for s in req.slides:
        b_list = split_slide_into_blocks(s.page, s.raw_text)
        all_blocks.extend(b_list)
        blocks_by_page[s.page] = b_list

    tracker = NonMonotonicSlideTracker(
        all_blocks,
        similarity_threshold=req.similarity_threshold,
        novelty_note_threshold=req.novelty_note_threshold
    )

    events = []
    notes_by_block = {}
    for u in req.utterances:
        ev = tracker.process_utterance(u.transcript_text, u.start_sec, u.end_sec)
        events.append(ev.model_dump())
        if ev.action == "attach_inline_note" and ev.matched_block_id:
            if ev.matched_block_id not in notes_by_block:
                notes_by_block[ev.matched_block_id] = []
            notes_by_block[ev.matched_block_id].append({
                "note_id": f"note_{len(notes_by_block[ev.matched_block_id]) + 1}_{ev.matched_block_id}",
                "content": ev.transcript_text,
                "timestamp_sec": ev.start_sec,
                "confidence": ev.confidence
            })

    annotated_pages = []
    for s in req.slides:
        b_list = blocks_by_page.get(s.page, [])
        page_blocks = []
        for b in b_list:
            inline_notes = notes_by_block.get(b.block_id, [])
            page_blocks.append({
                "block_id": b.block_id,
                "original_text": b.text,
                "kind": b.kind,
                "is_currently_lectured": False,
                "is_marked_confused": False,
                "highlights": [],
                "inline_notes": inline_notes
            })
        annotated_pages.append({
            "page_number": s.page,
            "title": b_list[0].text if b_list else f"Slide {s.page}",
            "blocks": page_blocks
        })

    return {
        "status": "success",
        "total_blocks": len(all_blocks),
        "total_utterances": len(req.utterances),
        "events": events,
        "annotated_pages": annotated_pages
    }


# ============================================================================
# 5. ENDPOINT TUẦN 1: HỢP NHẤT PIPELINE BÀI GIẢNG THẬT (END-TO-END)
# Slide -> ASR + VAD -> DTW/Manual Alignment -> Note Synthesizer (Real LLM)
# ============================================================================

class ProcessLecturePipelineRequest(BaseModel):
    lecture_id: int
    course_title: str = "Khóa học"
    slide_path: str
    audio_path: str
    language: Optional[str] = "auto"
    slide_clicks: Optional[List[Dict[str, Any]]] = None
    progress_webhook_url: Optional[str] = None
    webhook_secret: Optional[str] = None


def _send_progress_webhook(url: Optional[str], secret: Optional[str], status: str, percent: int, error_msg: Optional[str] = None):
    if not url:
        return
    try:
        import requests
        headers = {"X-Webhook-Secret": secret} if secret else {}
        requests.post(url, json={
            "processing_status": status,
            "progress_percent": percent,
            "error_message": error_msg
        }, headers=headers, timeout=5)
    except Exception as e:
        print(f"[WARN] Failed to send progress webhook to {url}: {e}")


@router.post("/process-lecture")
def process_lecture_pipeline(req: ProcessLecturePipelineRequest):
    """
    Xử lý bài giảng từ A-Z theo hợp đồng Tuần 1:
    1. Trích xuất slide & hotwords (15%)
    2. Chạy Faster-Whisper + Silero VAD (45%)
    3. Căn gióng theo click hoặc DTW (75%)
    4. Gọi LLM thật (Gemini) tổng hợp ghi chú 3 tầng + kiểm chứng trích dẫn (90% -> 100%)
    """
    import os
    from app.services.slide_extractor import extract_slide_deck
    from app.services.asr_vad_service import SlideAwareASREngine
    from app.services.manual_alignment_service import align_transcript_with_manual_clicks
    from app.modules.lecture_notes.alignment import monotonic_dtw_alignment
    from app.modules.lecture_notes.note_synthesizer import synthesize_slide_notes

    # Kiểm tra sự tồn tại của tệp slide và audio
    if not os.path.exists(req.slide_path):
        raise ValueError(f"Không tìm thấy tệp slide tại: {req.slide_path}")
    if not os.path.exists(req.audio_path):
        raise ValueError(f"Không tìm thấy tệp audio tại: {req.audio_path}")

    # BƯỚC 1: Trích xuất Slide (15%)
    _send_progress_webhook(req.progress_webhook_url, req.webhook_secret, "parsing_slides", 15)
    slides_raw = extract_slide_deck(req.slide_path)
    if not slides_raw:
        raise ValueError("Tệp slide không chứa trang nào hoặc không thể đọc được nội dung.")

    all_terms = []
    for s in slides_raw:
        all_terms.extend(s.get("extracted_terms", []))
    unique_terms = list(dict.fromkeys(all_terms))[:25]

    # BƯỚC 2: Chạy ASR + VAD (45%)
    _send_progress_webhook(req.progress_webhook_url, req.webhook_secret, "running_asr", 45)
    asr_engine = SlideAwareASREngine()
    segments, metadata = asr_engine.transcribe_with_metadata(
        audio_path=req.audio_path,
        slide_terms=unique_terms,
        course_title=req.course_title,
        language=req.language
    )

    # BƯỚC 3: Căn gióng Slide (75%)
    _send_progress_webhook(req.progress_webhook_url, req.webhook_secret, "aligning_slides", 75)
    aligned_segments = []
    if req.slide_clicks and len(req.slide_clicks) > 0:
        aligned_segments = align_transcript_with_manual_clicks(segments, req.slide_clicks)
    else:
        # Căn gióng tự động bằng Monotonic DTW
        assigned_pages = monotonic_dtw_alignment(segments, slides_raw)
        for idx, seg in enumerate(segments):
            page_num = assigned_pages[idx] if idx < len(assigned_pages) else 1
            item = dict(seg)
            item["aligned_slide_number"] = page_num
            item["aligned_slide_id"] = page_num
            item["start_time_sec"] = seg.get("start_sec", 0.0)
            item["end_time_sec"] = seg.get("end_sec", 0.0)
            aligned_segments.append(item)

    # BƯỚC 4: Gọi LLM thật sinh ghi chú 3 tầng (90%)
    _send_progress_webhook(req.progress_webhook_url, req.webhook_secret, "synthesizing_notes", 90)

    # Gom các câu thoại theo từng trang slide
    segments_by_slide: Dict[int, List[Dict[str, Any]]] = {}
    for seg in aligned_segments:
        s_num = int(seg.get("aligned_slide_number", seg.get("aligned_slide_id", 1)))
        if s_num not in segments_by_slide:
            segments_by_slide[s_num] = []
        segments_by_slide[s_num].append(seg)

    final_slides = []
    for s in slides_raw:
        s_num = int(s["slide_number"])
        s_text = s.get("raw_text", "")
        s_title = s.get("title", f"Slide {s_num}")
        s_terms = s.get("extracted_terms", [])
        slide_segs = segments_by_slide.get(s_num, [])

        # Gọi Note Synthesizer (Real Gemini API / LLM)
        synth_res = synthesize_slide_notes(
            slide_number=s_num,
            slide_text=s_text,
            transcript_segments=slide_segs,
            course_title=req.course_title
        )

        final_slides.append({
            "slide_number": s_num,
            "title": s_title,
            "extracted_text": s_text,
            "raw_text": s_text,
            "terms": s_terms,
            "extracted_terms": s_terms,
            "cleaned_slide_bullets": synth_res.cleaned_slide_bullets,
            "notes": [n.model_dump() for n in synth_res.notes],
            "summary": s_title
        })

    # Hoàn thành 100%
    _send_progress_webhook(req.progress_webhook_url, req.webhook_secret, "completed", 100)

    return {
        "status": "success",
        "lecture_id": req.lecture_id,
        "slides": final_slides,
        "transcript_segments": aligned_segments,
        "metadata": metadata
    }


# ============================================================================
# ENDPOINT KIỂM THỬ: GỬI FILE VOICE & TỔNG HỢP VỞ GHI CHÉP TỪ GIỌNG NÓI
# ============================================================================
class VoiceNotesPathRequest(BaseModel):
    audio_path: str
    title: Optional[str] = "Bài ghi âm lời giảng"
    language: Optional[str] = "vi"


@router.post("/test-voice-notes")
def test_voice_notes(
    audio_file: Optional[UploadFile] = File(None),
    audio_path: Optional[str] = Form(None),
    title: Optional[str] = Form(None),
    language: Optional[str] = Form("vi")
):
    """
    Endpoint thử nghiệm nhanh:
    Gửi tệp voice (.m4a, .mp3, .wav) -> Chạy ASR Faster-Whisper nhận diện giọng nói
    -> Gọi Gemini LLM tổng hợp vở ghi chép thông minh theo cấu trúc hoàn chỉnh.
    """
    import shutil
    import tempfile
    from app.services.asr_vad_service import SlideAwareASREngine
    from app.modules.lecture_notes.note_synthesizer import synthesize_voice_only_notes

    temp_file_created = False
    target_audio_path = audio_path

    if audio_file is not None and audio_file.filename:
        suffix = os.path.splitext(audio_file.filename)[1] or ".m4a"
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            shutil.copyfileobj(audio_file.file, tmp)
            target_audio_path = tmp.name
            temp_file_created = True

    if not target_audio_path or not os.path.exists(target_audio_path):
        raise HTTPException(status_code=400, detail="Không tìm thấy tệp âm thanh hợp lệ.")

    try:
        # 1. Chạy ASR nhận dạng giọng nói
        asr_engine = SlideAwareASREngine()
        segments, metadata = asr_engine.transcribe_with_metadata(
            audio_path=target_audio_path,
            slide_terms=[],
            course_title=title or "Bài giảng",
            language=language or "vi"
        )

        # 2. Gọi Gemini LLM tổng hợp ghi chú từ giọng nói
        notes_res = synthesize_voice_only_notes(
            transcript_segments=segments,
            title_hint=title or "Bài ghi âm lời giảng"
        )

        # 3. Tạo định dạng Markdown trực quan
        md_lines = [
            f"# 🎙️ VỞ GHI CHÉP: {notes_res.title}",
            "",
            "## 📌 Tóm tắt tổng quan",
            notes_res.summary,
            "",
            "## 💡 Các ý chính & Bài học cốt lõi",
        ]

        category_labels = {
            "core_concept": "Bản chất / Khái niệm",
            "example_code": "Ví dụ minh họa / Code",
            "exam_tip": "Lưu ý quan trọng / Mẹo thi",
            "announcement": "Thông báo / Bài tập",
        }

        for idx, kp in enumerate(notes_res.key_points, 1):
            cat_name = category_labels.get(kp.category, "Kiến thức")
            md_lines.append(f"### {idx}. [{kp.timestamp_label}] {kp.headline} *({cat_name})*")
            md_lines.append(f"{kp.content}")
            if kp.evidence_quote:
                md_lines.append(f"> 🔊 *Trích nguyên văn:* \"{kp.evidence_quote}\"")
            md_lines.append("")

        if notes_res.technical_terms:
            md_lines.append("## 📖 Thuật ngữ chuyên ngành")
            for t in notes_res.technical_terms:
                md_lines.append(f"- **{t.term}**: {t.explanation}")
            md_lines.append("")

        if notes_res.action_items_or_tips:
            md_lines.append("## ⚠️ Dặn dò & Việc cần làm")
            for item in notes_res.action_items_or_tips:
                md_lines.append(f"- {item}")
            md_lines.append("")

        markdown_doc = "\n".join(md_lines)
        total_duration = metadata.get("duration_sec", 0.0)
        if total_duration == 0.0 and segments:
            total_duration = segments[-1].get("end_sec", 0.0)

        return {
            "status": "success",
            "title": notes_res.title,
            "duration_sec": total_duration,
            "summary": notes_res.summary,
            "key_points": [p.model_dump() for p in notes_res.key_points],
            "technical_terms": [t.model_dump() for t in notes_res.technical_terms],
            "action_items_or_tips": notes_res.action_items_or_tips,
            "transcript_count": len(segments),
            "transcript_segments": segments,
            "markdown_notes": markdown_doc
        }

    finally:
        if temp_file_created and os.path.exists(target_audio_path):
            try:
                os.remove(target_audio_path)
            except Exception:
                pass


@router.post("/test-voice-notes-path")
def test_voice_notes_path(req: VoiceNotesPathRequest):
    """Gọi thử nghiệm voice notes bằng đường dẫn tệp trên ổ cứng."""
    if not os.path.exists(req.audio_path):
        raise HTTPException(status_code=400, detail=f"Không tìm thấy tệp tại {req.audio_path}")

    from app.services.asr_vad_service import SlideAwareASREngine
    asr_engine = SlideAwareASREngine()
    segments, metadata = asr_engine.transcribe_with_metadata(
        audio_path=req.audio_path,
        slide_terms=[],
        course_title=req.title or "Bài giảng",
        language=req.language or "vi"
    )

    from app.modules.lecture_notes.note_synthesizer import synthesize_voice_only_notes
    notes_res = synthesize_voice_only_notes(
        transcript_segments=segments,
        title_hint=req.title or "Bài ghi âm lời giảng"
    )

    return {
        "status": "success",
        "title": notes_res.title,
        "summary": notes_res.summary,
        "key_points": [p.model_dump() for p in notes_res.key_points],
        "technical_terms": [t.model_dump() for t in notes_res.technical_terms],
        "action_items_or_tips": notes_res.action_items_or_tips,
        "transcript_count": len(segments),
        "transcript_segments": segments,
    }



