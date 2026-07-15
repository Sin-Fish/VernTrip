CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS paper (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    authors VARCHAR(500) NOT NULL DEFAULT '',
    publication_year INT,
    venue VARCHAR(255),
    abstract_text TEXT,
    ai_summary TEXT,
    summary_problem TEXT,
    summary_method TEXT,
    summary_conclusion TEXT,
    summary_value TEXT,
    keywords VARCHAR(500),
    original_file_name VARCHAR(260) NOT NULL,
    file_path VARCHAR(1024) NOT NULL UNIQUE,
    parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS paper_chunk (
    id BIGSERIAL PRIMARY KEY,
    paper_id BIGINT NOT NULL REFERENCES paper(id) ON DELETE CASCADE,
    section_title VARCHAR(255),
    section_type VARCHAR(64),
    page_no INT,
    chunk_text TEXT NOT NULL,
    token_count INT,
    embedding_id VARCHAR(120),
    embedding vector(1024),
    sort_index INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS paper_reference (
    id BIGSERIAL PRIMARY KEY,
    paper_id BIGINT NOT NULL REFERENCES paper(id) ON DELETE CASCADE,
    raw_reference TEXT NOT NULL,
    ref_title VARCHAR(500),
    ref_authors VARCHAR(500),
    ref_year INT,
    matched_paper_id BIGINT REFERENCES paper(id),
    match_score DOUBLE PRECISION,
    status VARCHAR(32) NOT NULL DEFAULT 'UNMATCHED'
);

CREATE TABLE IF NOT EXISTS paper_relation (
    id BIGSERIAL PRIMARY KEY,
    source_paper_id BIGINT NOT NULL REFERENCES paper(id) ON DELETE CASCADE,
    target_paper_id BIGINT NOT NULL REFERENCES paper(id) ON DELETE CASCADE,
    relation_type VARCHAR(64) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0,
    evidence TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(source_paper_id, target_paper_id, relation_type)
);

CREATE TABLE IF NOT EXISTS chunk_info_point (
    chunk_id BIGINT NOT NULL REFERENCES paper_chunk(id) ON DELETE CASCADE,
    info_point VARCHAR(64) NOT NULL,
    score DOUBLE PRECISION NOT NULL DEFAULT 0,
    PRIMARY KEY (chunk_id, info_point)
);

CREATE TABLE IF NOT EXISTS parse_task (
    id BIGSERIAL PRIMARY KEY,
    paper_id BIGINT NOT NULL REFERENCES paper(id) ON DELETE CASCADE,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    queue_order BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS qa_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    paper_id BIGINT REFERENCES paper(id) ON DELETE SET NULL,
    question TEXT NOT NULL,
    answer TEXT,
    scope VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS qa_evidence (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES qa_session(id) ON DELETE CASCADE,
    chunk_id BIGINT REFERENCES paper_chunk(id) ON DELETE SET NULL,
    paper_id BIGINT REFERENCES paper(id) ON DELETE SET NULL,
    relation_path TEXT,
    score DOUBLE PRECISION,
    quote_text TEXT
);

CREATE TABLE IF NOT EXISTS external_paper (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    authors TEXT,
    year INT,
    abstract_text TEXT,
    url TEXT,
    source TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_paper_chunk_paper ON paper_chunk(paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_reference_paper ON paper_reference(paper_id);
CREATE INDEX IF NOT EXISTS idx_relation_source ON paper_relation(source_paper_id);
CREATE INDEX IF NOT EXISTS idx_relation_target ON paper_relation(target_paper_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON parse_task(status);
CREATE INDEX IF NOT EXISTS idx_task_queue_order ON parse_task(queue_order);
CREATE INDEX IF NOT EXISTS idx_qa_session_paper ON qa_session(paper_id);
CREATE INDEX IF NOT EXISTS idx_external_paper_status ON external_paper(status);
