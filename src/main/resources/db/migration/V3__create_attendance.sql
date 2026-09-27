CREATE TABLE attendance (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student(id),
    purpose VARCHAR(20) NOT NULL,
    operating_day DATE NOT NULL,
    attended BOOLEAN NOT NULL,
    first_verified_at TIMESTAMPTZ,
    method VARCHAR(20),
    manual_updated_at TIMESTAMPTZ,
    CONSTRAINT uk_attendance_student_purpose_day UNIQUE (student_id, purpose, operating_day),
    CONSTRAINT ck_attendance_purpose CHECK (purpose IN ('DORMITORY', 'STUDY_ROOM')),
    CONSTRAINT ck_attendance_method CHECK (method IN ('QR', 'FACE', 'MANUAL'))
);
