from __future__ import annotations

import json
from typing import Any

from kafka import KafkaProducer
from kafka.errors import KafkaError

from .config import settings
from .logging_utils import logger


class KafkaUsageProducer:
    def __init__(self) -> None:
        self._producer: KafkaProducer | None = None

    @property
    def enabled(self) -> bool:
        return settings.kafka_enabled and bool(settings.kafka_bootstrap_servers.strip())

    def publish(self, event: dict[str, Any]) -> bool:
        if not self.enabled:
            return False
        try:
            future = self._producer_instance().send(settings.kafka_usage_topic, event)
            future.add_errback(self._on_send_error)
            return True
        except Exception as exc:  # noqa: BLE001
            logger.warning("usage_kafka_publish_failure reason=%s", str(exc))
            return False

    def close(self) -> None:
        if self._producer is None:
            return
        try:
            self._producer.flush(timeout=5)
            self._producer.close(timeout=5)
        except Exception as exc:  # noqa: BLE001
            logger.warning("usage_kafka_close_failure reason=%s", str(exc))
        finally:
            self._producer = None

    def _producer_instance(self) -> KafkaProducer:
        if self._producer is None:
            self._producer = KafkaProducer(
                bootstrap_servers=_parse_bootstrap_servers(settings.kafka_bootstrap_servers),
                client_id=settings.kafka_client_id,
                value_serializer=_serialize_event,
                acks=1,
                retries=3,
                linger_ms=20,
            )
        return self._producer

    @staticmethod
    def _on_send_error(exc: BaseException) -> None:
        detail = str(exc).strip() or exc.__class__.__name__
        if isinstance(exc, KafkaError):
            logger.warning("usage_kafka_delivery_failure reason=%s", detail)
            return
        logger.warning("usage_kafka_delivery_failure reason=%s", detail)


def _serialize_event(event: dict[str, Any]) -> bytes:
    return json.dumps(event, ensure_ascii=False).encode("utf-8")


def _parse_bootstrap_servers(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]
