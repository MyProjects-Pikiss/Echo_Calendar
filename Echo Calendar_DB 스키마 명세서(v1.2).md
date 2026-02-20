Echo Calendar — DB 스키마 명세서 (v1.2)
(컬럼/제약/인덱스 중심, status 제거 버전)

==================================================
1) Category
==================================================

TABLE: Category
- id            TEXT    PRIMARY KEY
- displayName   TEXT    NOT NULL
- sortOrder     INTEGER NOT NULL

1-1) 기본 카테고리 세트 (v1 · 초안)
        CategoryEntity(id = "medical", displayName = "의료", sortOrder = 1),
        CategoryEntity(id = "finance", displayName = "금융", sortOrder = 2),
        CategoryEntity(id = "spending", displayName = "소비", sortOrder = 3),
        CategoryEntity(id = "administration", displayName = "행정", sortOrder = 4),
        CategoryEntity(id = "work", displayName = "업무", sortOrder = 5),
        CategoryEntity(id = "learning", displayName = "학습", sortOrder = 6),
        CategoryEntity(id = "relationships", displayName = "개인 관계", sortOrder = 7),
        CategoryEntity(id = "gathering-event", displayName = "모임·행사", sortOrder = 8),
        CategoryEntity(id = "life", displayName = "생활", sortOrder = 9),
        CategoryEntity(id = "repair-maintenance", displayName = "수리·정비", sortOrder = 10),
        CategoryEntity(id = "delivery", displayName = "배송", sortOrder = 11),
        CategoryEntity(id = "hobby-leisure", displayName = "취미·여가", sortOrder = 12),
        CategoryEntity(id = "dining", displayName = "외식·식사", sortOrder = 13),
        CategoryEntity(id = "record", displayName = "기록", sortOrder = 14),
        CategoryEntity(id = "other", displayName = "기타", sortOrder = 15)

==================================================
2) Event
==================================================

TABLE: Event
- id            TEXT    PRIMARY KEY
- categoryId    TEXT    NOT NULL            -- FK -> Category.id
- occurredAt    INTEGER NOT NULL            -- epoch millis
- summary       TEXT    NOT NULL
- placeText     TEXT    NULL
- body          TEXT    NOT NULL            -- 원문/STT/메모 (필수)
- createdAt     INTEGER NOT NULL            -- epoch millis
- updatedAt     INTEGER NOT NULL            -- epoch millis

INDEXES:
- INDEX(categoryId, occurredAt DESC)
- INDEX(occurredAt DESC)

==================================================
3) Label
==================================================

TABLE: Label
- id            INTEGER PRIMARY KEY AUTOINCREMENT
- name          TEXT    NOT NULL UNIQUE
- createdAt     INTEGER NOT NULL            -- epoch millis

LABEL UPSERT RULE (운영 규칙):
- Label.name 기준으로 조회
  - 존재하면 해당 Label 재사용
  - 없으면 새 Label 생성 후 사용

==================================================
4) EventLabel (Many-to-Many)
==================================================

TABLE: EventLabel
- eventId       TEXT    NOT NULL            -- FK -> Event.id
- labelId       INTEGER NOT NULL            -- FK -> Label.id
PRIMARY KEY(eventId, labelId)

INDEXES:
- INDEX(labelId)

==================================================
5) EventFts
==================================================

TABLE: EventFts (FTS4)
- eventId       TEXT
- summary       TEXT
- body          TEXT
- placeText     TEXT

NOTES:
- Event의 summary/body/placeText 텍스트 검색 후보 추출 용도
- Event와의 동기화는 다음 중 하나로 구현:
  (A) External content(권장) 또는
  (B) 수동 동기화(Insert/Update/Delete 시 FTS 갱신)

==================================================
6) EventAlarm
==================================================

TABLE: EventAlarm
- id            TEXT    PRIMARY KEY
- eventId       TEXT    NOT NULL            -- FK -> Event.id
- triggerAt     INTEGER NOT NULL            -- epoch millis
- isEnabled     INTEGER NOT NULL            -- 0/1