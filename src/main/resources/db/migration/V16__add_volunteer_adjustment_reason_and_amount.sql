-- #139 봉사 횟수 조정에 사유와 여러 회 조정을 추가한다.
-- delta는 실제로 바뀐 횟수다. 차감은 남은 횟수까지만 하므로 요청보다 작을 수 있다.
-- requested_delta는 관리자가 요청한 횟수다. 같은 Idempotency-Key의 재시도가 같은 요청인지 확인할 때 쓴다.
-- reason은 관리자가 남긴 사유(선택)이며 비어 있으면 NULL이다.
ALTER TABLE volunteer_adjustment ADD COLUMN reason VARCHAR(100);
ALTER TABLE volunteer_adjustment ADD COLUMN requested_delta SMALLINT;

UPDATE volunteer_adjustment SET requested_delta = delta;

ALTER TABLE volunteer_adjustment ALTER COLUMN requested_delta SET NOT NULL;

ALTER TABLE volunteer_adjustment DROP CONSTRAINT ck_volunteer_adjustment_delta;
ALTER TABLE volunteer_adjustment
    ADD CONSTRAINT ck_volunteer_adjustment_delta CHECK (delta BETWEEN -99 AND 99 AND delta <> 0);
ALTER TABLE volunteer_adjustment
    ADD CONSTRAINT ck_volunteer_adjustment_requested_delta
        CHECK (requested_delta BETWEEN -99 AND 99 AND requested_delta <> 0);
