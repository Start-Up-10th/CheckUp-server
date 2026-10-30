ALTER TABLE student ADD COLUMN consecutive_volunteer_count INTEGER NOT NULL DEFAULT 0
    CHECK (consecutive_volunteer_count >= 0);
ALTER TABLE volunteer_duty ADD COLUMN consecutive BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE volunteer_adjustment ALTER COLUMN kind TYPE VARCHAR(32);
ALTER TABLE volunteer_adjustment DROP CONSTRAINT ck_volunteer_adjustment_kind;
ALTER TABLE volunteer_adjustment ADD CONSTRAINT ck_volunteer_adjustment_kind CHECK (
    kind IN ('ADMIN', 'DUTY_COMPLETION', 'DUTY_MISSED', 'CONSECUTIVE_ADMIN', 'CONSECUTIVE_COMPLETION')
);
