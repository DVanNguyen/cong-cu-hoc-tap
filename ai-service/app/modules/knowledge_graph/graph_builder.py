import re
from typing import List, Dict, Set, Optional, Tuple, Any
from collections import defaultdict, deque
from pydantic import BaseModel, Field
from rapidfuzz import fuzz, distance

class ConceptOccurrence(BaseModel):
    lecture_order: int
    slide_number: int
    role: str  # "DEFINED" or "USED"

class CanonicalConceptNode(BaseModel):
    id: Optional[str] = None
    canonical_name: Optional[str] = None
    name: Optional[str] = None
    aliases: List[str] = Field(default_factory=list)
    definition_summary: Optional[str] = None
    definition: Optional[str] = None
    first_defined_pos: Optional[int] = None  # L * 1000 + S
    first_used_pos: Optional[int] = None     # L * 1000 + S
    first_defined_lecture: Optional[int] = None
    first_defined_slide: Optional[int] = None
    first_used_lecture: Optional[int] = None
    first_used_slide: Optional[int] = None
    defined_in_lecture_idx: int = 1
    is_used_before_defined: bool = False
    warning_message: Optional[str] = None

    def get_canonical_name(self) -> str:
        return self.canonical_name or self.name or ""

class ConceptEdgeRelation(BaseModel):
    source_concept: Optional[str] = None
    target_concept: Optional[str] = None
    from_concept: Optional[str] = None
    to_concept: Optional[str] = None
    relation_type: str = "prerequisite"  # prerequisite, part_of, example_of, contrast
    relation: Optional[str] = None
    evidence_quote: str = ""
    confidence: float = 1.0

    def get_source(self) -> str:
        return self.source_concept or self.from_concept or ""

    def get_target(self) -> str:
        return self.target_concept or self.to_concept or ""

ConceptNode = CanonicalConceptNode
ConceptEdge = ConceptEdgeRelation

class GraphValidationResult(BaseModel):
    is_dag: bool
    topological_order: List[str]
    used_before_defined_warnings: List[str]
    total_concepts: int
    total_relations: int

# ==============================================================================
# THUẬT TOÁN 2.1: CHUẨN HÓA THỰC THỂ SONG NGỮ VIỆT - ANH (BILINGUAL ENTITY RESOLUTION)
# ==============================================================================
def parse_direct_bilingual_alias(term: str) -> Tuple[str, Optional[str]]:
    """
    Bước 1 - Chuẩn hóa chuỗi & Tách cặp song ngữ trực tiếp:
    Dạng: 'tiến trình (process)' -> canonical: 'tiến trình', alias: 'process'
    """
    term_clean = " ".join(term.strip().split())
    match = re.search(r"^(.*?)\s*[\(\[\{](.*?)[\)\]\}]$", term_clean)
    if match:
        main_term = match.group(1).strip()
        alias_term = match.group(2).strip()
        return main_term, alias_term
    return term_clean, None

def compute_hybrid_bilingual_similarity(term1: str, term2: str) -> float:
    """
    Bước 2 - Tính độ tương đồng lai:
    S_total = 0.65 * CosineSim + 0.35 * JaroWinkler
    (CosineSim được xấp xỉ bằng RapidFuzz token_set_ratio khi chạy offline).
    """
    cosine_sim = fuzz.token_set_ratio(term1.lower(), term2.lower()) / 100.0
    jaro_sim = distance.JaroWinkler.similarity(term1.lower(), term2.lower())
    return 0.65 * cosine_sim + 0.35 * jaro_sim

def resolve_concepts_to_canonical(
    raw_concept_mentions: List[Dict[str, Any]]
) -> List[CanonicalConceptNode]:
    """
    Thực hiện Bilingual Entity Resolution và gắn nhãn Vị trí Pos(L, S) = L * 1000 + S.
    """
    canonical_list: List[CanonicalConceptNode] = []

    for item in raw_concept_mentions:
        raw_name = item.get("name", "").strip()
        if not raw_name:
            continue
        
        main_part, alias_part = parse_direct_bilingual_alias(raw_name)
        if item.get("english_or_vietnamese_alias"):
            alias_part = item.get("english_or_vietnamese_alias")

        l_ord = item.get("lecture_order", 1)
        s_num = item.get("slide_number", 1)
        role = item.get("mention_role", "USED").upper()
        pos = l_ord * 1000 + s_num

        # Tìm canonical concept khớp nhất
        best_match: Optional[CanonicalConceptNode] = None
        best_score = 0.0

        for can in canonical_list:
            # So sánh với canonical_name và mọi alias
            candidates = [can.canonical_name] + can.aliases
            for cand in candidates:
                score = compute_hybrid_bilingual_similarity(main_part, cand)
                if score > best_score:
                    best_score = score
                    best_match = can

        # Phân ngưỡng quyết định
        if best_match and best_score >= 0.85:
            # Gộp vào concept đã có
            target_node = best_match
            if main_part.lower() != target_node.canonical_name.lower():
                if main_part not in target_node.aliases:
                    target_node.aliases.append(main_part)
            if alias_part and alias_part not in target_node.aliases:
                target_node.aliases.append(alias_part)
        else:
            # Tạo concept mới
            aliases = [alias_part] if alias_part else []
            target_node = CanonicalConceptNode(
                canonical_name=main_part,
                aliases=aliases,
                definition_summary=item.get("definition") or item.get("evidence_quote")
            )
            canonical_list.append(target_node)

        # Cập nhật mốc Pos_first_def và Pos_first_use
        if role == "DEFINED":
            if target_node.first_defined_pos is None or pos < target_node.first_defined_pos:
                target_node.first_defined_pos = pos
                target_node.first_defined_lecture = l_ord
                target_node.first_defined_slide = s_num
        else:  # USED
            if target_node.first_used_pos is None or pos < target_node.first_used_pos:
                target_node.first_used_pos = pos
                target_node.first_used_lecture = l_ord
                target_node.first_used_slide = s_num

    # ==============================================================================
    # THUẬT TOÁN 2.2: PHÁT HIỆN DÙNG TRƯỚC KHI ĐƯỢC ĐỊNH NGHĨA (USED-BEFORE-DEFINED)
    # ==============================================================================
    for node in canonical_list:
        # Bật cờ cảnh báo nếu Pos_first_use < Pos_first_def hoặc được dùng nhưng chưa từng định nghĩa
        if node.first_used_pos is not None:
            if node.first_defined_pos is None:
                node.is_used_before_defined = True
                node.warning_message = (
                    f"Khái niệm '{node.canonical_name}' được sử dụng tại Bài {node.first_used_lecture}, "
                    f"Slide {node.first_used_slide} nhưng chưa có định nghĩa chính thức trong bài giảng."
                )
            elif node.first_used_pos < node.first_defined_pos:
                node.is_used_before_defined = True
                node.warning_message = (
                    f"Khái niệm '{node.canonical_name}' được nhắc tới ở Bài {node.first_used_lecture}, "
                    f"Slide {node.first_used_slide} nhưng định nghĩa chi tiết nằm ở "
                    f"Bài {node.first_defined_lecture}, Slide {node.first_defined_slide} — Bấm để xem trước."
                )

    return canonical_list

# ==============================================================================
# THUẬT TOÁN 2.3: TOPOLOGICAL SORT & VALIDATION
# ==============================================================================
def validate_and_order_knowledge_graph(
    concepts: List[CanonicalConceptNode],
    relations: List[ConceptEdgeRelation]
) -> GraphValidationResult:
    """
    Kiểm tra chu trình và sắp xếp topo đồ thị khái niệm (Kahn's Algorithm).
    """
    adj = defaultdict(list)
    in_degree = defaultdict(int)
    all_names = {c.get_canonical_name() for c in concepts if c.get_canonical_name()}

    for c in all_names:
        in_degree[c] = 0

    for rel in relations:
        u = rel.get_source()
        v = rel.get_target()
        if u in all_names and v in all_names:
            adj[u].append(v)
            in_degree[v] += 1

    queue = deque([node for node in all_names if in_degree[node] == 0])
    topo_order = []

    while queue:
        u = queue.popleft()
        topo_order.append(u)
        for v in adj[u]:
            in_degree[v] -= 1
            if in_degree[v] == 0:
                queue.append(v)

    is_dag = (len(topo_order) == len(all_names))
    if not is_dag:
        topo_order = list(all_names)

    warnings = [c.warning_message for c in concepts if c.is_used_before_defined and c.warning_message]

    return GraphValidationResult(
        is_dag=is_dag,
        topological_order=topo_order,
        used_before_defined_warnings=warnings,
        total_concepts=len(concepts),
        total_relations=len(relations)
    )
