ALTER TABLE tourism_resource
    ADD COLUMN reservation_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN daily_capacity INTEGER,
    ADD COLUMN available_capacity INTEGER;

UPDATE tourism_resource
SET reservation_required = TRUE,
    daily_capacity = CASE WHEN category = 'LODGING' THEN 24 ELSE 30 END,
    available_capacity = CASE WHEN category = 'LODGING' THEN 12 ELSE 18 END
WHERE category IN ('EXPERIENCE', 'LODGING');

ALTER TABLE tourism_resource
    ADD CONSTRAINT chk_tourism_resource_daily_capacity
        CHECK (daily_capacity IS NULL OR daily_capacity >= 0),
    ADD CONSTRAINT chk_tourism_resource_available_capacity
        CHECK (available_capacity IS NULL OR available_capacity >= 0),
    ADD CONSTRAINT chk_tourism_resource_capacity_range
        CHECK (daily_capacity IS NULL OR available_capacity IS NULL OR available_capacity <= daily_capacity);
