CREATE TABLE member (
    id BIGSERIAL PRIMARY KEY,
    datagsm_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE student (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL UNIQUE REFERENCES member(id),
    number INT NOT NULL,
    grade INT NOT NULL,
    class_number INT NOT NULL,
    student_number INT NOT NULL,
    room_number INT
);