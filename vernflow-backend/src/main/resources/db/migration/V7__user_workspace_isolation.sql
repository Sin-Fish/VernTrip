CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_user_username
    ON app_user (username);

ALTER TABLE assistant_session
    ADD COLUMN IF NOT EXISTS user_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE workbench_run
    ADD COLUMN IF NOT EXISTS user_id BIGINT NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_assistant_session_user_updated_at
    ON assistant_session (user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_workbench_run_user_created_at
    ON workbench_run (user_id, created_at DESC);
