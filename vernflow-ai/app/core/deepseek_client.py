from __future__ import annotations

import json
import urllib.error
import urllib.request

from .settings import get_settings


class DeepSeekError(RuntimeError):
    pass


class DeepSeekClient:
    def __init__(self) -> None:
        self.settings = get_settings()

    def is_enabled(self) -> bool:
        return self.settings.deepseek_enabled

    def chat(
        self,
        messages: list[dict[str, str]],
        *,
        temperature: float = 0.2,
        max_tokens: int = 1200,
    ) -> str:
        if not self.is_enabled():
            raise DeepSeekError("DeepSeek API key is not configured.")

        payload = {
            "model": self.settings.deepseek_model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        request = urllib.request.Request(
            url=f"{self.settings.deepseek_base_url}/chat/completions",
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self.settings.deepseek_api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=self.settings.deepseek_timeout_seconds) as response:
                body = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            details = error.read().decode("utf-8", errors="ignore")
            raise DeepSeekError(f"DeepSeek request failed with {error.code}: {details}") from error
        except urllib.error.URLError as error:
            raise DeepSeekError(f"Could not reach DeepSeek: {error.reason}") from error

        try:
            payload = json.loads(body)
            content = payload["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError, json.JSONDecodeError) as error:
            raise DeepSeekError("DeepSeek returned an unexpected response payload.") from error

        if not isinstance(content, str) or not content.strip():
            raise DeepSeekError("DeepSeek returned an empty answer.")

        return content.strip()
