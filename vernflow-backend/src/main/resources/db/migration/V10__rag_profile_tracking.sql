ALTER TABLE assistant_session
    ADD COLUMN rag_profile_id VARCHAR(64) NOT NULL DEFAULT 'tfidf_topk_basic';

ALTER TABLE workbench_run
    ADD COLUMN rag_profile_id VARCHAR(64) NOT NULL DEFAULT 'tfidf_topk_basic';
