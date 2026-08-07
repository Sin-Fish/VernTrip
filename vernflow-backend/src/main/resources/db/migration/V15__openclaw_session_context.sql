CREATE TABLE openclaw_session_context (
    id BIGSERIAL PRIMARY KEY,
    session_key VARCHAR(96) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    assistant_session_id BIGINT REFERENCES assistant_session(id) ON DELETE SET NULL,
    global_scope BOOLEAN NOT NULL DEFAULT FALSE,
    working_files_json TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_openclaw_session_context_user_session
    ON openclaw_session_context (user_id, assistant_session_id, updated_at DESC);

CREATE INDEX idx_openclaw_session_context_expires_at
    ON openclaw_session_context (expires_at);
