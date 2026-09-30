-- REQ-AUTH-004 동의. 필수 두 항목은 처음 동의한 시각, 공지 알림 수신은 선택(기본 해제)이다.
ALTER TABLE student ADD COLUMN privacy_agreed_at TIMESTAMPTZ;
ALTER TABLE student ADD COLUMN face_agreed_at TIMESTAMPTZ;
ALTER TABLE student ADD COLUMN notice_alarm_agreed BOOLEAN NOT NULL DEFAULT FALSE;
