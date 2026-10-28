-- 프레임 최소 간격을 이전 프레임의 시작 시각부터 센다(#262). 아직 프레임을 받지 않은 세션은 NULL이다.
ALTER TABLE face_recognition_session
    ADD COLUMN last_frame_started_at TIMESTAMPTZ;
