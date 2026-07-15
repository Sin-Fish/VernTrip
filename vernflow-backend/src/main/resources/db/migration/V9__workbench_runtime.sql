ALTER TABLE workbench_run
    ADD COLUMN IF NOT EXISTS runtime_json TEXT NOT NULL DEFAULT '{}';
