UPDATE workspace_chunk_index
SET embedding = NULL,
    embedding_id = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE embedding_id IS NOT NULL;

ALTER TABLE workspace_chunk_index
ALTER COLUMN embedding TYPE vector(384);
