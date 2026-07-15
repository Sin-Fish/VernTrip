ALTER TABLE workbench_run
    ADD COLUMN history_json TEXT NOT NULL DEFAULT '[]';
