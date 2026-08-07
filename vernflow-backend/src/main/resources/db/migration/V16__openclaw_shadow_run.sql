CREATE TABLE openclaw_shadow_run (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    assistant_session_id BIGINT NOT NULL REFERENCES assistant_session(id) ON DELETE CASCADE,
    request_message_id VARCHAR(96),
    legacy_message_id VARCHAR(96),
    session_key VARCHAR(96),
    status VARCHAR(16) NOT NULL,
    response_id VARCHAR(128),
    response_text TEXT,
    input_tokens BIGINT,
    output_tokens BIGINT,
    latency_ms BIGINT,
    error_code VARCHAR(64),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_openclaw_shadow_run_session
    ON openclaw_shadow_run (assistant_session_id, started_at DESC);

CREATE INDEX idx_openclaw_shadow_run_status
    ON openclaw_shadow_run (status, started_at DESC);
