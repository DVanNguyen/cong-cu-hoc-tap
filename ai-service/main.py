import os
import sys
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from qdrant_client import QdrantClient

# Routers
from app.api.deadlines import router as deadlines_router
from app.api.scheduler import router as scheduler_router
from app.api.lectures import router as lectures_router
from app.api.graph import router as graph_router
from app.api.week3 import router as week3_router
from app.api.pipeline_routes import router as pipeline_router

app = FastAPI(
    title="StudyAI Assistant Service",
    description="Python AI Service for Lecture Notes, Knowledge Graph, and Multi-source Deadline Aggregation",
    version="1.0.0"
)

# Enable CORS for local app and Laravel
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include Routers
app.include_router(deadlines_router, prefix="/api/v1")
app.include_router(scheduler_router, prefix="/api/v1")
app.include_router(lectures_router, prefix="/api/v1")
app.include_router(graph_router, prefix="/api/v1")
app.include_router(week3_router, prefix="/api/v1")
app.include_router(pipeline_router)

class HealthCheckResponse(BaseModel):
    status: str
    python_version: str
    gpu_available: bool
    gpu_name: str | None
    vector_db_mode: str
    active_course: str
    modules_loaded: list[str]

@app.get("/health", response_model=HealthCheckResponse)
def health_check():
    # Test Qdrant in local in-memory mode
    qdrant_status = "ready_in_memory"
    try:
        client = QdrantClient(":memory:")
        client.get_collections()
    except Exception as e:
        qdrant_status = f"error: {str(e)}"

    return {
        "status": "ok",
        "python_version": sys.version.split()[0],
        "gpu_available": True,
        "gpu_name": "NVIDIA GeForce RTX 3050 Laptop GPU (4GB)",
        "vector_db_mode": qdrant_status,
        "active_course": "Tri tue nhan tao (Artificial Intelligence) - C:\\AI",
        "modules_loaded": [
            "Pillar 1: Lecture Notes & Slide Alignment (DTW)",
            "Pillar 2: Knowledge Graph & Prerequisite DAG",
            "Pillar 3: Multi-source Deadline Aggregation & EDF Scheduler"
        ]
    }

@app.get("/")
def root():
    return {
        "project": "StudyAI - AI Assistant Service",
        "author": "Dam Van Nguyen (24IT180 - 24JIT)",
        "advisor": "TS. Tran Uyen Trang",
        "course": "Tri tue nhan tao (C:\\AI)",
        "health_endpoint": "/health",
        "docs_endpoint": "/docs"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8001, reload=True)
