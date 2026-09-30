-- DataGSM 학생 상세 정보. 이미 저장된 학생은 값이 채워지기 전까지 NULL이므로 NULL을 허용한다.
ALTER TABLE student ADD COLUMN email VARCHAR(100);
ALTER TABLE student ADD COLUMN sex VARCHAR(10);
ALTER TABLE student ADD COLUMN dormitory_floor INT;
ALTER TABLE student ADD COLUMN specialty VARCHAR(100);
