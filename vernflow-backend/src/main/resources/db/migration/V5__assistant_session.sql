CREATE TABLE assistant_session (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    summary TEXT,
    global_scope BOOLEAN NOT NULL DEFAULT FALSE,
    working_files_json TEXT,
    messages_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assistant_session_updated_at
    ON assistant_session (updated_at DESC);
