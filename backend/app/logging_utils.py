from __future__ import annotations

import hashlib
import logging


logger = logging.getLogger("echo_calendar_backend")


def mask_text(value: str) -> str:
    cleaned = value.strip()
    if not cleaned:
        return ""
    digest = hashlib.sha256(cleaned.encode("utf-8")).hexdigest()[:12]
    preview = cleaned[:8] + ("..." if len(cleaned) > 8 else "")
    return f"{preview}#{digest}"
