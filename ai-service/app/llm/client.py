import abc
import hashlib
import json
import logging
import os
from typing import Type, TypeVar, Optional, Any, List
from pydantic import BaseModel
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type
from app.core.config import settings

logger = logging.getLogger(__name__)
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


class GeminiLLMClient(BaseLLMClient):
    """Client kết nối trực tiếp với Google Gemini API (hỗ trợ Pydantic response_schema)."""
    def __init__(self, cache_dir: str = "cache/llm"):
        super().__init__(cache_dir)
        if not settings.GEMINI_API_KEY:
            raise ValueError("GEMINI_API_KEY chưa được thiết lập trong .env. Không thể khởi tạo GeminiLLMClient!")
        
        from google import genai
        self.client = genai.Client(api_key=settings.GEMINI_API_KEY)
        self.model_name = settings.LLM_MODEL_NAME or "gemini-3.5-flash"
        logger.info(f"Initialized GeminiLLMClient with model: {self.model_name}")

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=2, max=10),
        reraise=True
    )
    def generate_structured(self, prompt: str, schema: Type[T], system_instruction: str = "") -> T:
        cache_key = self._get_cache_key(prompt, schema.__name__)
        cached = self._check_cache(cache_key)
        if cached:
            return schema.model_validate(cached)

        from google.genai import types
        config = types.GenerateContentConfig(
            temperature=0.15,
            response_mime_type="application/json",
            response_schema=schema,
        )
        if system_instruction:
            config.system_instruction = system_instruction

        try:
            response = self.client.models.generate_content(
                model=self.model_name,
                contents=prompt,
                config=config
            )
            raw_text = response.text
        except Exception as e:
            # Fallback nếu model chính tạm thời quá tải
            fallback_models = ["gemini-3.1-flash-lite", "gemini-3.5-flash"]
            success = False
            last_err = e
            for fb_m in fallback_models:
                if fb_m == self.model_name:
                    continue
                try:
                    logger.warning(f"Thử model dự phòng: {fb_m}")
                    res = self.client.models.generate_content(
                        model=fb_m,
                        contents=prompt,
                        config=config
                    )
                    raw_text = res.text
                    success = True
                    break
                except Exception as fb_err:
                    last_err = fb_err
            if not success:
                raise last_err

        # Làm sạch chuỗi json nếu có định dạng markdown
        cleaned = raw_text.strip()
        if cleaned.startswith("```json"):
            cleaned = cleaned[7:]
        if cleaned.startswith("```"):
            cleaned = cleaned[3:]
        if cleaned.endswith("```"):
            cleaned = cleaned[:-3]
        cleaned = cleaned.strip()

        parsed = json.loads(cleaned)
        self._save_cache(cache_key, parsed)
        return schema.model_validate(parsed)


class OllamaLLMClient(BaseLLMClient):
    """Client kết nối với Ollama chạy local (Qwen2.5-7B-Instruct)."""
    def __init__(self, cache_dir: str = "cache/llm"):
        super().__init__(cache_dir)
        self.base_url = settings.OLLAMA_BASE_URL.rstrip('/')
        self.model_name = settings.LLM_MODEL_NAME or "qwen2.5:7b-instruct-q4_K_M"

    def generate_structured(self, prompt: str, schema: Type[T], system_instruction: str = "") -> T:
        cache_key = self._get_cache_key(prompt, schema.__name__)
        cached = self._check_cache(cache_key)
        if cached:
            return schema.model_validate(cached)

        import requests
        payload = {
            "model": self.model_name,
            "stream": False,
            "format": schema.model_json_schema(),
            "options": {"temperature": 0.15, "num_ctx": 8192},
            "messages": [
                {"role": "system", "content": system_instruction or "Bạn là trợ lý học tập AI."},
                {"role": "user", "content": prompt}
            ]
        }

        resp = requests.post(f"{self.base_url}/api/chat", json=payload, timeout=120)
        resp.raise_for_status()
        data = resp.json()
        content = data.get("message", {}).get("content", "{}")
        parsed = json.loads(content)
        self._save_cache(cache_key, parsed)
        return schema.model_validate(parsed)


class MockLLMClient(BaseLLMClient):
    """Client giả lập phục vụ kiểm thử đơn vị độc lập không tốn token."""
    def generate_structured(self, prompt: str, schema: Type[T], system_instruction: str = "") -> T:
        cache_key = self._get_cache_key(prompt, schema.__name__)
        cached = self._check_cache(cache_key)
        if cached:
            return schema.model_validate(cached)

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
        else:
            result_dict = {
                "slide_number": 1,
                "cleaned_slide_bullets": ["Nội dung chính slide"],
                "notes": [
                    {
                        "category": "core_explanation",
                        "headline": "Ý chính bài giảng",
                        "content": "Giải thích chi tiết về khái niệm bài giảng.",
                        "evidence_quote": prompt[:80],
                        "start_time_sec": 0.0,
                        "end_time_sec": 10.0
                    }
                ]
            }

        self._save_cache(cache_key, result_dict)
        return schema.model_validate(result_dict)


def get_llm_client() -> BaseLLMClient:
    provider = settings.LLM_PROVIDER.lower()
    if provider == "gemini":
        if not settings.GEMINI_API_KEY:
            raise ValueError("GEMINI_API_KEY bị rỗng! Không thể chạy chế độ gemini nếu không có API key.")
        return GeminiLLMClient()
    elif provider == "ollama":
        return OllamaLLMClient()
    elif provider == "mock":
        return MockLLMClient()
    else:
        raise ValueError(f"Unknown LLM provider: {provider}. Chọn một trong ['gemini', 'ollama', 'mock']")
