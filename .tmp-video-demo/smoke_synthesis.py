# -*- coding: utf-8 -*-
"""AI 服务合成模块离线冒烟：直接调用 synthesize()"""
import base64
import sys

sys.path.insert(0, r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\vernflow-ai")

from app.modules.video_synthesis.schemas import VideoSynthesisRequest
from app.modules.video_synthesis.service import synthesize

IMAGES = [
    r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\vernflow-web\public\images\banner1.jpg",
    r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\vernflow-web\public\images\banner2.jpg",
    r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\vernflow-web\public\images\banner3.jpg",
]

encoded = []
for path in IMAGES:
    with open(path, "rb") as fh:
        encoded.append(base64.b64encode(fh.read()).decode("ascii"))

request = VideoSynthesisRequest(
    name="灵隐寺",
    city="杭州",
    category="CULTURE",
    facts="开放时间08:00-17:00；参考花费¥30-45；特色标签：禅意、古迹、祈福",
    images=encoded,
    aspect_ratio="16:9",
)
print("synthesizing...")
mp4 = synthesize(request)
out = r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\.tmp-video-demo\synthesis-smoke.mp4"
with open(out, "wb") as fh:
    fh.write(mp4)
print(f"OK: {out} ({len(mp4) / 1024 / 1024:.2f} MB)")
