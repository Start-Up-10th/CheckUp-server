-- 관리자 카메라가 맡은 층(#253). 이전에 만든 세션은 층이 없으므로 NULL을 허용한다.
ALTER TABLE face_recognition_session
    ADD COLUMN floor INTEGER CHECK (floor BETWEEN 3 AND 5);
