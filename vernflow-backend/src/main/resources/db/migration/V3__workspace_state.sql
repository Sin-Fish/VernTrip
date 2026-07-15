CREATE TABLE IF NOT EXISTS workspace_state (
    id BIGINT PRIMARY KEY,
    working_files_json TEXT NOT NULL DEFAULT '[]',
    global_scope BOOLEAN NOT NULL DEFAULT FALSE,
    selected_tree_path VARCHAR(1024),
    home_task_draft TEXT,
    workbench_prompt TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
