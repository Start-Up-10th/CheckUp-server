-- 출입 시각·인증 방식·수동 수정 시각은 보관하지 않는다. 호실은 당시 배정값이다.
CREATE TABLE attendance_summary (
    student_id BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    operating_day DATE NOT NULL,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('DORMITORY', 'STUDY_ROOM')),
    dormitory_room INTEGER NOT NULL,
    attended BOOLEAN NOT NULL,
    PRIMARY KEY (student_id, purpose, operating_day)
);
CREATE INDEX ix_attendance_summary_day_room ON attendance_summary (operating_day, dormitory_room, purpose);
