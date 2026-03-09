from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

from kafka import KafkaConsumer

from app.config import settings
from app.logging_utils import logger
from app.usage_store import init_usage_db, log_usage_event


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


def _parse_bootstrap_servers(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def _deserialize_event(raw: bytes) -> dict[str, Any] | None:
    try:
        parsed = json.loads(raw.decode("utf-8"))
    except Exception as exc:  # noqa: BLE001
        logger.warning("usage_consumer_invalid_json reason=%s", str(exc))
        return None
    if not isinstance(parsed, dict):
        logger.warning("usage_consumer_invalid_payload reason=payload_not_object")
        return None
    return parsed


def main() -> None:
    if not settings.kafka_enabled:
        raise SystemExit("KAFKA_ENABLED must be true for usage consumer")

    usage_db_path = Path(settings.usage_db_path)
    init_usage_db(usage_db_path)

    consumer = KafkaConsumer(
        settings.kafka_usage_topic,
        bootstrap_servers=_parse_bootstrap_servers(settings.kafka_bootstrap_servers),
        group_id=settings.kafka_usage_group_id,
        client_id=f"{settings.kafka_client_id}-usage-consumer",
        auto_offset_reset="earliest",
        enable_auto_commit=True,
        value_deserializer=_deserialize_event,
    )

    logger.info(
        "usage_consumer_started topic=%s bootstrap=%s",
        settings.kafka_usage_topic,
        settings.kafka_bootstrap_servers,
    )

    for message in consumer:
        event = message.value
        if event is None:
            continue
        try:
            log_usage_event(usage_db_path, **event)
        except Exception as exc:  # noqa: BLE001
            logger.warning("usage_consumer_write_failure reason=%s", str(exc))


if __name__ == "__main__":
    main()
