from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


AiMode = Literal["input", "search", "refine"]
DraftField = Literal["summary", "time", "category", "place", "labels", "body"]
CrudIntent = Literal["create", "update", "delete"]


class InputInterpretRequest(BaseModel):
    mode: AiMode
    transcript: str = Field(min_length=1)
    selectedDate: str


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


class SearchInterpretRequest(BaseModel):
    mode: AiMode
    transcript: str = Field(min_length=1)


class SearchInterpretResponse(BaseModel):
    mode: Literal["search"] = "search"
    query: str = Field(min_length=1)
    dateFrom: str | None = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")
    dateTo: str | None = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")
    categoryIds: list[str] = []


class RefineFieldRequest(BaseModel):
    mode: AiMode
    transcript: str = Field(min_length=1)
    field: DraftField
    currentValue: str = ""
    selectedDate: str


class RefineFieldResponse(BaseModel):
    mode: Literal["refine"] = "refine"
    field: DraftField
    value: str = Field(min_length=1)
    missingRequired: list[str] = []


class StableErrorResponse(BaseModel):
    mode: AiMode
    errorCode: str
    message: str
