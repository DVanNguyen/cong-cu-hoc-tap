import os
from pydantic import BaseModel

# Load .env file if present
_env_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".env")
if os.path.exists(_env_path):
    with open(_env_path, "r", encoding="utf-8") as _f:
        for _line in _f:
            _line = _line.strip()
            if _line and not _line.startswith("#") and "=" in _line:
                _k, _v = _line.split("=", 1)
                os.environ.setdefault(_k.strip(), _v.strip())

class Settings(BaseModel):
    PROJECT_NAME: str = "StudyAI Assistant Service"
    VERSION: str = "1.0.0"
    API_V1_PREFIX: str = "/api/v1"
    
    # LLM Settings
    LLM_PROVIDER: str = os.getenv("LLM_PROVIDER", "gemini")  # "gemini", "openai", "ollama", "mock"
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")
    OLLAMA_BASE_URL: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    LLM_MODEL_NAME: str = os.getenv("LLM_MODEL_NAME", "gemini-1.5-flash")
    LLM_CACHE_ENABLED: bool = os.getenv("LLM_CACHE_ENABLED", "true").lower() == "true"
    
    # Semantic Embedding (Week 3)
    EMBEDDING_MODEL_NAME: str = os.getenv("EMBEDDING_MODEL_NAME", "BAAI/bge-m3")
    EMBEDDING_DEVICE: str = os.getenv("EMBEDDING_DEVICE", "auto")  # "auto" | "cuda" | "cpu"

    # Qdrant Settings
    QDRANT_MODE: str = os.getenv("QDRANT_MODE", "memory")  # "memory" or "http://localhost:6333"
    
    # Laravel Gateway
    LARAVEL_API_URL: str = os.getenv("LARAVEL_API_URL", "http://127.0.0.1:8000/api/v1")
    LARAVEL_WEBHOOK_SECRET: str = os.getenv("LARAVEL_WEBHOOK_SECRET", "studyai_secret_token_2026")
    
    # Timezone
    TIMEZONE: str = "Asia/Ho_Chi_Minh"

settings = Settings()
