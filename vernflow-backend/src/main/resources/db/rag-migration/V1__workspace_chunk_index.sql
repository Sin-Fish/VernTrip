CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS workspace_document_index (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_path VARCHAR(1024) NOT NULL,
    source_label VARCHAR(1024) NOT NULL,
    section_title VARCHAR(255),
    source_signature VARCHAR(128) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, source_path)
);

CREATE TABLE IF NOT EXISTS workspace_chunk_index (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES workspace_document_index(id) ON DELETE CASCADE,
    sort_index INT NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding_id VARCHAR(120),
    embedding vector(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, sort_index)
);

CREATE INDEX IF NOT EXISTS idx_workspace_document_index_user
    ON workspace_document_index (user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_workspace_chunk_index_document
    ON workspace_chunk_index (document_id, sort_index);
