-- #176 봉사 조정 이력에 조정 종류를 남긴다. 화면은 종류별 기본 문구를 활동명으로 쓴다.
-- ADMIN: 관리자의 +/−·여러 회 조정. DUTY_COMPLETION: 자치위원의 당일 봉사 완료로 생긴 차감(-1).
ALTER TABLE volunteer_adjustment ADD COLUMN kind VARCHAR(20);

-- 기존 기록: 당일 봉사 완료는 지정 완료 시각과 같은 시각에 -1로 기록했으므로 그 기록만 완료로 본다.
UPDATE volunteer_adjustment a
SET kind = 'DUTY_COMPLETION'
WHERE a.delta = -1
  AND a.request_key IS NULL
  AND EXISTS (SELECT 1
              FROM volunteer_duty d
              WHERE d.student_id = a.student_id
                AND d.status = 'COMPLETED'
                AND d.completed_at = a.created_at);

UPDATE volunteer_adjustment SET kind = 'ADMIN' WHERE kind IS NULL;

ALTER TABLE volunteer_adjustment ALTER COLUMN kind SET NOT NULL;
ALTER TABLE volunteer_adjustment
    ADD CONSTRAINT ck_volunteer_adjustment_kind CHECK (kind IN ('ADMIN', 'DUTY_COMPLETION'));
