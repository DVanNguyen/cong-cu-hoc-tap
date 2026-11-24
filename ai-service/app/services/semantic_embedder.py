import logging
import threading
from typing import List, Optional

import numpy as np
import torch

from app.core.config import settings

logger = logging.getLogger("semantic_embedder")


class SemanticEmbedderService:
    """
    Singleton tạo vector nhúng đa ngôn ngữ (Việt - Anh) bằng SentenceTransformer.
    Hỗ trợ BAAI/bge-m3 (1024-d) và intfloat/multilingual-e5-base (768-d).
    """

    def __init__(self, model_name: Optional[str] = None, device: Optional[str] = None):
        self.model_name = model_name or settings.EMBEDDING_MODEL_NAME
        dev = device or settings.EMBEDDING_DEVICE
        if dev and dev != "auto":
            self.device = dev
        else:
            self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self._model = None
        self._cache = {}
        self._lock = threading.Lock()
        self.load_count = 0

    def _load_model(self):
        if self._model is None:
            with self._lock:
                if self._model is None:
                    from sentence_transformers import SentenceTransformer
                    logger.info(f"Loading embedding model {self.model_name} on {self.device}...")
                    model = SentenceTransformer(self.model_name, device=self.device)
                    if self.device == "cuda":
                        model.half()  # fp16: tiết kiệm VRAM cho GPU 4-6GB
                    model.max_seq_length = min(getattr(model, "max_seq_length", 512) or 512, 512)
                    self._model = model
                    self.load_count += 1
        return self._model

    def _prepare_texts(self, texts: List[str], is_query: bool = False) -> List[str]:
        if "e5" in self.model_name.lower():
            prefix = "query: " if is_query else "passage: "
            return [f"{prefix}{t.strip()}" if t and t.strip() else f"{prefix}empty" for t in texts]
        return [t.strip() if t and t.strip() else "empty" for t in texts]

    def encode(self, texts: List[str], is_query: bool = False, batch_size: int = 32) -> np.ndarray:
        if not texts:
            return np.zeros((0, 1024), dtype=np.float32)
        model = self._load_model()
        prepared = self._prepare_texts(texts, is_query=is_query)
        # Cache vector theo văn bản: slide lặp lại giữa các request không phải mã hóa lại
        missing = [p for p in dict.fromkeys(prepared) if p not in self._cache]
        if missing:
            vecs = model.encode(
                missing,
                batch_size=batch_size,
                normalize_embeddings=True,
                show_progress_bar=False,
            )
            for p, v in zip(missing, vecs):
                self._cache[p] = np.asarray(v, dtype=np.float32)
            if len(self._cache) > 20000:
                self._cache.clear()
        return np.stack([self._cache[p] for p in prepared]).astype(np.float32)

    def similarity_matrix(self, queries: List[str], passages: List[str]) -> np.ndarray:
        """Ma trận cosine similarity (len(queries), len(passages)) trong [0, 1]."""
        if not queries or not passages:
            return np.zeros((len(queries), len(passages)), dtype=np.float32)
        q_vecs = self.encode(queries, is_query=True)
        p_vecs = self.encode(passages, is_query=False)
        return np.clip(np.matmul(q_vecs, p_vecs.T), 0.0, 1.0)


_EMBEDDER_SINGLETON: Optional[SemanticEmbedderService] = None


def get_semantic_embedder() -> SemanticEmbedderService:
    global _EMBEDDER_SINGLETON
    if _EMBEDDER_SINGLETON is None:
        _EMBEDDER_SINGLETON = SemanticEmbedderService()
    return _EMBEDDER_SINGLETON
