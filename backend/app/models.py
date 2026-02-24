from __future__ import annotations

from datetime import date
from typing import Literal

from pydantic import BaseModel, Field, field_validator, model_validator


AiMode = Literal["input", "search", "refine"]
DraftField = Literal["summary", "time", "category", "place", "labels", "body"]
CrudIntent = Literal["create", "update", "delete"]


class InputInterpretRequest(BaseModel):
    mode: AiMode
    transcript: str = Field(min_length=1)
    selectedDate: str

    @field_validator("selectedDate")
    @classmethod
    def validate_selected_date(cls, value: str) -> str:
        _require_iso_date(value)
        return value


class InputInterpretResponse(BaseModel):
    mode: Literal["input"] = "input"
    intent: CrudIntent = "create"
    date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    summary: str = Field(min_length=1)
    time: str = Field(default="", pattern=r"^$|^\d{2}:\d{2}$")
    categoryId: str = "other"
    placeText: str = ""
    body: str = Field(min_length=1)
    labels: list[str] = []
    missingRequired: list[str] = []

    @field_validator("date")
    @classmethod
    def validate_date(cls, value: str) -> str:
        _require_iso_date(value)
        return value

    @field_validator("time")
    @classmethod
    def validate_time(cls, value: str) -> str:
        _require_hhmm_or_empty(value)
        return value


class SearchInterpretRequest(BaseModel):
    mode: AiMode
    transcript: str = Field(min_length=1)


class SearchInterpretResponse(BaseModel):
    mode: Literal["search"] = "search"
    query: str = Field(min_length=1)
    dateFrom: str | None = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")
    dateTo: str | None = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")
    categoryIds: list[str] = []

    @field_validator("dateFrom", "dateTo")
    @classmethod
    def validate_optional_date(cls, value: str | None) -> str | None:
        if value is None:
            return None
        _require_iso_date(value)
        return value


class RefineFieldRequest(BaseModel):
    mode: AiMode
    transcript: str = Field(min_length=1)
    field: DraftField
    currentValue: str = ""
    selectedDate: str

    @field_validator("selectedDate")
    @classmethod
    def validate_selected_date(cls, value: str) -> str:
        _require_iso_date(value)
        return value


class RefineFieldResponse(BaseModel):
    mode: Literal["refine"] = "refine"
    field: DraftField
    value: str = Field(min_length=1)
    missingRequired: list[str] = []

    @model_validator(mode="after")
    def validate_field_value(self) -> "RefineFieldResponse":
        if self.field == "time":
            _require_hhmm_or_empty(self.value)
            if not self.value:
                raise ValueError("time field value must not be empty")
        return self


class StableErrorResponse(BaseModel):
    mode: AiMode
    errorCode: str
    message: str


def _require_iso_date(value: str) -> None:
    try:
        date.fromisoformat(value.strip())
    except ValueError as exc:
        raise ValueError("must be a valid ISO date (YYYY-MM-DD)") from exc


def _require_hhmm_or_empty(value: str) -> None:
    text = value.strip()
    if not text:
        return
    if ":" not in text:
        raise ValueError("must be HH:mm")
    hour_text, minute_text = text.split(":", 1)
    if not (hour_text.isdigit() and minute_text.isdigit()):
        raise ValueError("must be HH:mm")
    hour = int(hour_text)
    minute = int(minute_text)
    if hour < 0 or hour > 23 or minute < 0 or minute > 59:
        raise ValueError("must be HH:mm")
