-- REQ-COM-005 웹 내부 알림. 같은 학생·유형·원본에는 알림을 하나만 둔다(재시도·중복 인증 방지).
-- source_key는 알림 원본이다. 출석은 '용도:운영일'(예: STUDY_ROOM:2026-09-30), 봉사·공지는 원본 id.
CREATE TABLE notification
(
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT       NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL,
    source_key VARCHAR(100) NOT NULL,
    message    VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    read_at    TIMESTAMPTZ,
    CONSTRAINT uk_notification_student_type_source UNIQUE (student_id, type, source_key),
    CONSTRAINT ck_notification_type CHECK (type IN ('ATTENDANCE', 'VOLUNTEER', 'NOTICE'))
);

CREATE INDEX idx_notification_student_created ON notification (student_id, created_at DESC);
