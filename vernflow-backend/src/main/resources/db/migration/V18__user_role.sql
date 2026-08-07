ALTER TABLE app_user
    ADD COLUMN role VARCHAR(24) NOT NULL DEFAULT 'TOURIST';

UPDATE app_user
SET role = 'INTERNAL'
WHERE LOWER(username) = 'reviewer';
