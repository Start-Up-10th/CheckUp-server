-- DataGSM student.id(canonical studentId). 이미 저장된 학생은 다음 로그인 때 채워지므로 NULL을 허용한다.
ALTER TABLE student ADD COLUMN datagsm_student_id BIGINT UNIQUE;
