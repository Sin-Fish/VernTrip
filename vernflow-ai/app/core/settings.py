from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path


SERVICE_ROOT = Path(__file__).resolve().parents[2]
PROJECT_ROOT = SERVICE_ROOT.parent


def _load_env_file(path: Path, protected_keys: set[str]) -> None:
    if not path.exists():
        return

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].strip()
        if "=" not in line:
            continue

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("\"'")
        if key and key not in protected_keys:
            os.environ[key] = value


def bootstrap_env() -> None:
    protected_keys = set(os.environ.keys())
    _load_env_file(PROJECT_ROOT / ".env", protected_keys)
    _load_env_file(PROJECT_ROOT / ".env.local", protected_keys)


@dataclass(frozen=True)
class Settings:
    deepseek_api_key: str | None
    deepseek_base_url: str
    deepseek_model: str
    deepseek_timeout_seconds: float
    rag_embedding_backend: str
    rag_embedding_model: str
    rag_embedding_device: str

    @property
    def deepseek_enabled(self) -> bool:
        return bool(self.deepseek_api_key)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    bootstrap_env()
    api_key = os.getenv("DEEPSEEK_API_KEY")
    return Settings(
        deepseek_api_key=api_key.strip() if api_key else None,
        deepseek_base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/"),
        deepseek_model=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
        deepseek_timeout_seconds=float(os.getenv("DEEPSEEK_TIMEOUT_SECONDS", "60")),
        rag_embedding_backend=os.getenv("RAG_EMBEDDING_BACKEND", "sentence_transformers").strip().lower(),
        rag_embedding_model=os.getenv("RAG_EMBEDDING_MODEL", "intfloat/multilingual-e5-small").strip(),
        rag_embedding_device=os.getenv("RAG_EMBEDDING_DEVICE", "cpu").strip(),
    )
