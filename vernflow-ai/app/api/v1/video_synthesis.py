from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import Response

from ...modules.video_synthesis.schemas import VideoSynthesisRequest
from ...modules.video_synthesis.service import synthesize

router = APIRouter()


@router.post("/ai/video-synthesis")
def video_synthesis_endpoint(request: VideoSynthesisRequest) -> Response:
    """AI 成片：真实图片 + 已核验事实 → 解说配音 + 字幕 + 运镜合成，返回 mp4。"""
    mp4 = synthesize(request)
    return Response(
        content=mp4,
        media_type="video/mp4",
        headers={"Content-Disposition": 'inline; filename="verntrip-video.mp4"'},
    )
