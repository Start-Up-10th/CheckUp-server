-- 프레임 간격은 시작 시각으로 제한하고, 처리 완료 시각은 세션 만료 판단에 계속 사용한다.
ALTER TABLE face_recognition_session ADD COLUMN last_frame_started_at TIMESTAMPTZ;

-- 기존 세션의 시작 시각을 알 수 없으므로 마지막 활동 시각을 보수적으로 사용한다.
-- 새 세션은 NULL로 생성하여 첫 프레임을 바로 허용한다.
UPDATE face_recognition_session SET last_frame_started_at = last_activity_at;
