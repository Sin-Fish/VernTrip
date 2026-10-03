-- AI 视频生成任务（可灵 Kling）。
-- resource_id / product_id 二选一关联内容来源；
-- created_by 记录发起人（游客或商家），商家端历史列表按此过滤；
-- 无 KLING_API_KEY 时任务走 mock 模式（mock=true），由派发器在模拟延迟后置为 SUCCEEDED。
CREATE TABLE video_generation_task (
    id BIGSERIAL PRIMARY KEY,
    resource_id BIGINT REFERENCES tourism_resource(id) ON DELETE SET NULL,
    product_id BIGINT REFERENCES tourism_product(id) ON DELETE SET NULL,
    created_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    task_type VARCHAR(24) NOT NULL,
    prompt TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL DEFAULT 5,
    aspect_ratio VARCHAR(16) NOT NULL DEFAULT '16:9',
    resolution VARCHAR(16) NOT NULL DEFAULT '720p',
    kling_task_id VARCHAR(80),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    video_path TEXT,
    kling_video_url TEXT,
    error_message TEXT,
    mock BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX idx_video_generation_task_status
    ON video_generation_task (status, created_at);

CREATE INDEX idx_video_generation_task_resource
    ON video_generation_task (resource_id, status, updated_at DESC);

CREATE INDEX idx_video_generation_task_created_by
    ON video_generation_task (created_by, created_at DESC);
