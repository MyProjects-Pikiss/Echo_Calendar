from __future__ import annotations

import time
from collections import defaultdict, deque


class SimpleRateLimiter:
    def __init__(self, max_requests: int, window_seconds: int = 60) -> None:
        self.max_requests = max_requests
        self.window_seconds = window_seconds
        self._events: dict[str, deque[float]] = defaultdict(deque)

    def allow(self, key: str) -> bool:
        now = time.time()
        threshold = now - self.window_seconds
        events = self._events[key]
        while events and events[0] < threshold:
            events.popleft()
        if len(events) >= self.max_requests:
            return False
        events.append(now)
        return True
