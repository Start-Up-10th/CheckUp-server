-- REQ-COM-006 당일 봉사자 지정(DEC-020). 학생·운영일(08:00 KST 기준)마다 한 행이다.
-- 지정하면 ASSIGNED, 자치위원이 봉사 완료를 확인하면 COMPLETED가 되고 봉사 횟수를 1 줄인다.
CREATE TABLE volunteer_duty
(
    id            BIGSERIAL PRIMARY KEY,
    student_id    BIGINT      NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    operating_day DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    completed_at  TIMESTAMPTZ,
    CONSTRAINT uk_volunteer_duty_student_day UNIQUE (student_id, operating_day),
    CONSTRAINT ck_volunteer_duty_status CHECK (status IN ('ASSIGNED', 'COMPLETED'))
);

CREATE INDEX idx_volunteer_duty_operating_day ON volunteer_duty (operating_day);
