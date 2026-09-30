ALTER TABLE student ADD COLUMN face_consent_at TIMESTAMPTZ;
ALTER TABLE student ADD COLUMN face_consent_version VARCHAR(40);

CREATE TABLE face_template (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL UNIQUE REFERENCES student(id) ON DELETE CASCADE,
    model_id VARCHAR(120) NOT NULL,
    model_version VARCHAR(80) NOT NULL,
    dimension INT NOT NULL CHECK (dimension = 256),
    normalization VARCHAR(20) NOT NULL CHECK (normalization = 'l2'),
    vectors_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE face_recognition_session (
    session_id UUID PRIMARY KEY,
    admin_member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('DORMITORY', 'STUDY_ROOM')),
    created_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_face_recognition_session_idle ON face_recognition_session(last_activity_at);

CREATE TABLE face_recognition_session_candidate (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES face_recognition_session(session_id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    CONSTRAINT uk_face_session_candidate UNIQUE(session_id, student_id)
);

CREATE INDEX idx_face_session_candidate_student ON face_recognition_session_candidate(student_id);
