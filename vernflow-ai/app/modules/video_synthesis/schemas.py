from __future__ import annotations

from pydantic import BaseModel, Field


class VideoSynthesisRequest(BaseModel):
    """AI 成片请求：真实图片素材 + 已核验事实 → 解说配音 + 字幕 + 运镜合成 mp4。"""

    name: str = Field(description="景点/产品名称")
    city: str = Field(default="", description="所在城市")
    category: str = Field(default="SCENIC", description="资源类型（SCENIC/CULTURE/EXPERIENCE/FOOD/LODGING/LEISURE）")
    facts: str = Field(default="", description="已核验事实文本（开放时间、价格、标签等，用于生成解说词）")
    images: list[str] = Field(default_factory=list, description="分镜图片（JPEG base64，不含 data: 前缀），1-4 张")
    aspect_ratio: str = Field(default="16:9", pattern="^(16:9|9:16|1:1)$")
    voice: str = Field(default="zh-CN-YunxiNeural", description="edge-tts 中文音色")
