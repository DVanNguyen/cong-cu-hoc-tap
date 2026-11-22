from fastapi import APIRouter
from typing import List
from pydantic import BaseModel
from app.modules.knowledge_graph.graph_builder import (
    ConceptNode, ConceptEdge, GraphValidationResult, validate_and_order_knowledge_graph
)

router = APIRouter(prefix="/graph", tags=["Knowledge Graph"])

class GraphAnalysisRequest(BaseModel):
    concepts: List[ConceptNode]
    edges: List[ConceptEdge]

@router.post("/validate", response_model=GraphValidationResult)
def validate_graph_endpoint(req: GraphAnalysisRequest):
    """Kiểm tra chu trình DAG, thứ tự học topo và cảnh báo khái niệm dùng trước khi định nghĩa."""
    return validate_and_order_knowledge_graph(req.concepts, req.edges)
