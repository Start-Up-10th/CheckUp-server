-- #250 당일 봉사 미이행. 지정을 지우지 않고 MISSED로 남기고, 미이행으로 추가한 횟수는 그 지정에 연결한 조정 기록으로 남긴다.
-- 운영일이 지났는데 ASSIGNED인 지정은 저장값을 바꾸지 않고 조회할 때 미이행(횟수 미처리)으로 본다.
ALTER TABLE volunteer_duty DROP CONSTRAINT ck_volunteer_duty_status;
ALTER TABLE volunteer_duty
    ADD CONSTRAINT ck_volunteer_duty_status CHECK (status IN ('ASSIGNED', 'COMPLETED', 'MISSED'));

-- duty_id는 미이행 처리(DUTY_MISSED)로 생긴 조정 기록의 지정이다. 지정 하나에 미이행 조정은 하나뿐이다.
ALTER TABLE volunteer_adjustment
    ADD COLUMN duty_id BIGINT REFERENCES volunteer_duty (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uk_volunteer_adjustment_missed_duty ON volunteer_adjustment (duty_id) WHERE kind = 'DUTY_MISSED';

ALTER TABLE volunteer_adjustment DROP CONSTRAINT ck_volunteer_adjustment_kind;
ALTER TABLE volunteer_adjustment
    ADD CONSTRAINT ck_volunteer_adjustment_kind CHECK (kind IN ('ADMIN', 'DUTY_COMPLETION', 'DUTY_MISSED'));
