ALTER TABLE face_recognition_session
    ADD COLUMN frame_lock_token UUID,
    ADD COLUMN frame_lock_until TIMESTAMPTZ,
    ADD CONSTRAINT ck_face_session_frame_lock_pair CHECK (
        (frame_lock_token IS NULL AND frame_lock_until IS NULL)
        OR (frame_lock_token IS NOT NULL AND frame_lock_until IS NOT NULL)
    );
