import sys
import os

if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from app.schemas import (
    SlideNoteOutputSchema, 
    ConceptGraphOutputSchema, 
    ExtractedTaskOutputSchema, 
    verify_evidence_grounding
)
from app.modules.lecture_notes.alignment import align_transcript_to_slides, compute_novelty_score
from app.modules.knowledge_graph.graph_builder import (
    resolve_concepts_to_canonical, 
    validate_and_order_knowledge_graph,
    CanonicalConceptNode,
    ConceptEdgeRelation
)
from app.modules.scheduler.priority import calculate_explainable_urgency, TaskItem
from app.modules.deadline.deduplication import match_existing_task

def run_tests():
    # 1. Test verify_evidence_grounding
    source = "Các em nộp bài tập chương 2 lên Google Drive trước 23h59 ngày 20/10/2026 nhé."
    quote = "nộp bài tập chương 2 lên Google Drive trước 23h59 ngày 20/10/2026"
    hallucinated = "thầy cho 10 điểm cả lớp"

    assert verify_evidence_grounding(quote, source) == True, "Grounding check failed for true quote"
    assert verify_evidence_grounding(hallucinated, source) == False, "Grounding check failed for hallucinated quote"
    print("1. Evidence grounding verification: PASSED [OK]")

    # 2. Test Explainable Urgency
    task = TaskItem(id="1", title="Bài tập A*", due_at="2026-10-20T23:59:00+07:00", remaining_effort_hours=3.0, task_type="assignment")
    urgency = calculate_explainable_urgency(task)
    assert 0 <= urgency.urgency_score <= 100
    assert urgency.urgency_explanation != ""
    print(f"2. Explainable Urgency: score={urgency.urgency_score}, level={urgency.urgency_level}")
    print(f"   Explanation: '{urgency.urgency_explanation}' [OK]")

    # 3. Test Task Deduplication with NumberOverlap & MatchScore
    new_task = {"title": "Bài tập thực hành 2 A*", "task_type": "assignment", "due_at": "2026-10-22T21:00:00+07:00"}
    existing = [{"id": "101", "title": "Lab 2 - A* Search Python", "task_type": "assignment", "due_at": "2026-10-21T21:00:00+07:00"}]
    matched, score, action = match_existing_task(new_task, existing)
    assert matched is not None
    assert score >= 0.78
    assert action in ["merge_evidence", "reschedule"]
    print(f"3. Task Deduplication: matched='{matched['title']}', score={score:.2f}, action={action} [OK]")

    # 4. Test Bilingual Entity Resolution & Used-Before-Defined
    raw_mentions = [
        {"name": "tiến trình (process)", "lecture_order": 1, "slide_number": 4, "mention_role": "USED", "evidence_quote": "Process chiếm CPU"},
        {"name": "Tiến trình hệ thống", "lecture_order": 2, "slide_number": 1, "mention_role": "DEFINED", "evidence_quote": "Định nghĩa tiến trình là chương trình đang thực thi"}
    ]
    canonical_concepts = resolve_concepts_to_canonical(raw_mentions)
    assert len(canonical_concepts) == 1, "Should merge into 1 canonical concept"
    c = canonical_concepts[0]
    assert c.is_used_before_defined == True, "Should detect used-before-defined warning"
    print(f"4. Bilingual Entity Resolution & Used-Before-Defined: Canonical='{c.canonical_name}', Aliases={c.aliases}")
    print(f"   Warning: '{c.warning_message}' [OK]")

    # 5. Test Monotonic DTW Alignment & Novelty Score
    transcript_segs = [
        {"text": "Hôm nay chúng ta học về Agent", "start_sec": 0.0, "end_sec": 10.0},
        {"text": "Tác tử nhận thức môi trường qua sensors và hành động qua actuators, ví dụ xe tự hành Tesla cần camera và phanh tự động", "start_sec": 11.0, "end_sec": 30.0}
    ]
    slides = [
        {"slide_number": 1, "raw_text": "Chapter 2: Intelligent Agents Overview"},
        {"slide_number": 2, "raw_text": "An agent perceives environment via sensors and acts via actuators."}
    ]
    notes = align_transcript_to_slides(transcript_segs, slides)
    assert len(notes) > 0, "Should generate supplementary notes with novelty >= 0.28"
    print(f"5. Monotonic DTW & Novelty: Generated {len(notes)} supplementary notes [OK]")
    for n in notes:
        print(f"   Slide {n.slide_page} (Novelty: {n.novelty_score}): {n.content[:60]}...")

    print("\n=======================================================")
    print("ALL 5 CORE SCIENTIFIC ALGORITHMS VERIFIED AND FUNCTIONAL!")
    print("=======================================================")

if __name__ == "__main__":
    run_tests()
