-- #143 관리자 얼굴 출석 화면의 최근 인식 기록(REQ-ATT-007). 새로고침해도 목록이 유지되도록 당일 기록만 남긴다.
-- 얼굴 이미지·좌표·벡터는 저장하지 않는다. 다음 운영일 08:00 KST에 지난 운영일 기록을 지운다.
-- result: SUCCESS(알아봤고 출석 처리됨) / FAILED(알아봤지만 출석 처리 안 됨, 또는 못 알아봐 QR 안내).
-- 못 알아본 얼굴은 student_id가 NULL이다. 인식 실패로 다른 학생의 이름을 만들지 않는다.
-- 같은 세션·같은 얼굴(track_id)·같은 결과는 한 줄만 남긴다(프레임마다 쌓이지 않게).
CREATE TABLE face_recognition_log
(
    id              BIGSERIAL PRIMARY KEY,
    session_id      UUID         NOT NULL,
    admin_member_id BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    purpose         VARCHAR(20)  NOT NULL CHECK (purpose IN ('DORMITORY', 'STUDY_ROOM')),
    operating_day   DATE         NOT NULL,
    track_id        VARCHAR(128) NOT NULL,
    result          VARCHAR(10)  NOT NULL CHECK (result IN ('SUCCESS', 'FAILED')),
    student_id      BIGINT REFERENCES student (id) ON DELETE CASCADE,
    recognized_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_face_recognition_log_track UNIQUE (session_id, track_id, result)
);

CREATE INDEX idx_face_recognition_log_admin_day
    ON face_recognition_log (admin_member_id, operating_day, purpose, recognized_at DESC);
