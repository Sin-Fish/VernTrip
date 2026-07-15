CREATE TABLE IF NOT EXISTS workbench_run (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    global_scope BOOLEAN NOT NULL DEFAULT FALSE,
    inspect_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    working_files_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workbench_run_created_at
    ON workbench_run (created_at DESC);
