-- REQ-COM-002 봉사 횟수 조정 기록(DEC-020). 횟수 +1/-1을 한 번 할 때마다 한 행을 남긴다.
-- request_key는 웹이 보낸 Idempotency-Key다. 같은 키의 재시도는 한 번만 반영한다. 키가 없으면 NULL이고 중복 검사를 하지 않는다.
-- 학생의 가장 최근 created_at이 봉사 관리 명단의 "최근 활동"이다.
CREATE TABLE volunteer_adjustment
(
    id          BIGSERIAL PRIMARY KEY,
    student_id  BIGINT       NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    delta       SMALLINT     NOT NULL,
    request_key VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_volunteer_adjustment_request_key UNIQUE (request_key),
    CONSTRAINT ck_volunteer_adjustment_delta CHECK (delta IN (1, -1))
);

CREATE INDEX idx_volunteer_adjustment_student_created ON volunteer_adjustment (student_id, created_at DESC);
