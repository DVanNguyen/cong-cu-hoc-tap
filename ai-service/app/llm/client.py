import abc
import hashlib
import json
import os
from typing import Type, TypeVar, Optional, Any
from pydantic import BaseModel
from app.core.config import settings

T = TypeVar("T", bound=BaseModel)

class BaseLLMClient(abc.ABC):
    def __init__(self, cache_dir: str = "cache/llm"):
        self.cache_dir = cache_dir
        if settings.LLM_CACHE_ENABLED:
            os.makedirs(self.cache_dir, exist_ok=True)

    def _get_cache_key(self, prompt: str, schema_name: str) -> str:
        content = f"{prompt}_{schema_name}"
        return hashlib.sha256(content.encode("utf-8")).hexdigest()

    def _check_cache(self, cache_key: str) -> Optional[dict]:
        if not settings.LLM_CACHE_ENABLED:
            return None
        cache_file = os.path.join(self.cache_dir, f"{cache_key}.json")
        if os.path.exists(cache_file):
            try:
                with open(cache_file, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception:
                return None
        return None

    def _save_cache(self, cache_key: str, data: dict):
        if not settings.LLM_CACHE_ENABLED:
            return
        cache_file = os.path.join(self.cache_dir, f"{cache_key}.json")
        try:
            with open(cache_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    @abc.abstractmethod
    def generate_structured(self, prompt: str, schema: Type[T], system_instruction: str = "") -> T:
        """Sinh kết quả có cấu trúc theo schema Pydantic."""
        pass


class MockLLMClient(BaseLLMClient):
    """Client giả lập phục vụ kiểm thử đơn vị và benchmark độc lập không tốn token."""
    def generate_structured(self, prompt: str, schema: Type[T], system_instruction: str = "") -> T:
        cache_key = self._get_cache_key(prompt, schema.__name__)
        cached = self._check_cache(cache_key)
        if cached:
            return schema.model_validate(cached)

        # Trích xuất đơn giản dựa trên quy tắc cho Mock
        result_dict = {}
        prompt_lower = prompt.lower()
        
        if "intelligent agents" in prompt_lower or "chương 2" in prompt_lower:
            result_dict = {
                "title": "Bài tập chương 2: Intelligent Agents",
                "course": "Trí tuệ nhân tạo",
                "type": "assignment",
                "due_expression": "23h59 ngày 20/10/2026",
                "due_iso_candidate": "2026-10-20T23:59:00+07:00",
                "intent": "create",
                "effort_est_min": 120,
                "confidence": 0.95,
                "evidence_quote": "nộp bài tập chương 2 (Intelligent Agents) lên Google Drive trước 23h59 ngày 20/10/2026"
            }
        elif "dời hạn" in prompt_lower or "gia hạn" in prompt_lower:
            result_dict = {
                "title": "Bài tập dời hạn",
                "course": "Trí tuệ nhân tạo",
                "type": "assignment",
                "due_expression": "23h59 Chủ nhật ngày 25/10",
                "due_iso_candidate": "2026-10-25T23:59:00+07:00",
                "intent": "reschedule",
                "effort_est_min": 120,
                "confidence": 0.95,
                "evidence_quote": "dời hạn bài tập 1 đến 23h59 Chủ nhật ngày 25/10"
            }
        elif "hủy" in prompt_lower:
            result_dict = {
                "title": "Hủy bài tập",
                "course": "Trí tuệ nhân tạo",
                "type": "assignment",
                "due_expression": "",
                "due_iso_candidate": None,
                "intent": "cancel",
                "effort_est_min": 0,
                "confidence": 0.90,
                "evidence_quote": "Hủy bài tập cá nhân tuần 3"
            }
        else:
            result_dict = {
                "title": "Nhiệm vụ chung",
                "course": "Trí tuệ nhân tạo",
                "type": "assignment",
                "due_expression": "tuần sau",
                "due_iso_candidate": "2026-10-20T23:59:00+07:00",
                "intent": "create",
                "effort_est_min": 60,
                "confidence": 0.80,
                "evidence_quote": prompt[:80]
            }

        self._save_cache(cache_key, result_dict)
        return schema.model_validate(result_dict)


def get_llm_client() -> BaseLLMClient:
    provider = settings.LLM_PROVIDER.lower()
    if provider == "mock":
        return MockLLMClient()
    # Mở rộng cho Gemini/OpenAI khi cung cấp API Key
    return MockLLMClient()
