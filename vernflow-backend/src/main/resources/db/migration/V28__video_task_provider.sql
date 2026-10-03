-- 视频任务生成方式：KLING = 可灵真实 AI 视频；LOCAL = AI 成片（真实图片 + AI 解说 + AI 配音 + 自动剪辑）。
ALTER TABLE video_generation_task ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'KLING';
