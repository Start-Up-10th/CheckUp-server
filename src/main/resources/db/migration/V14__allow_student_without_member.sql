-- 로그인 전 학생도 DataGSM 학생 목록으로 미리 저장할 수 있게 계정 연결을 선택으로 바꾼다.
-- member_id UNIQUE는 그대로 둔다. PostgreSQL UNIQUE는 NULL을 여러 개 허용한다.
ALTER TABLE student ALTER COLUMN member_id DROP NOT NULL;

-- 계정이 없는 학생도 명단에 이름을 보여야 하므로 이름을 학생에 둔다. 기존 학생은 회원 이름을 복사한다.
ALTER TABLE student ADD COLUMN name VARCHAR(50);

UPDATE student s
SET name = m.name
FROM member m
WHERE s.member_id = m.id;

ALTER TABLE student ALTER COLUMN name SET NOT NULL;
