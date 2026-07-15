DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_attribute
        WHERE attrelid = 'workspace_chunk_index'::regclass
          AND attname = 'embedding'
          AND format_type(atttypid, atttypmod) <> 'vector(384)'
    ) THEN
        UPDATE workspace_chunk_index
        SET embedding = NULL,
            embedding_id = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE embedding_id IS NOT NULL;

        ALTER TABLE workspace_chunk_index
        ALTER COLUMN embedding TYPE vector(384);
    END IF;
END $$;
