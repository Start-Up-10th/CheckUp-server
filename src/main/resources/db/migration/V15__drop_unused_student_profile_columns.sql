-- 저장도 조회도 하지 않는 학생 프로필 컬럼을 지운다. 필요하면 DataGSM에서 그때 받아 온다.
-- 쓰지 않는 개인정보(이메일·성별 등)를 보관하지 않는다.
ALTER TABLE student
    DROP COLUMN email,
    DROP COLUMN sex,
    DROP COLUMN dormitory_floor,
    DROP COLUMN specialty;
