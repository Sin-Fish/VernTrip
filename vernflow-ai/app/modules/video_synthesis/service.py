from __future__ import annotations

import asyncio
import base64
import json
import logging
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

from ...core.deepseek_client import DeepSeekClient, DeepSeekError
from ...core.text_utils import normalize_whitespace
from .schemas import VideoSynthesisRequest

logger = logging.getLogger(__name__)

# 目标画幅（宽 x 高）
SIZES = {"16:9": (1280, 720), "9:16": (720, 1280), "1:1": (1024, 1024)}

# 运镜参数：输入先放大 4 倍再缓慢 zoom，避免 zoompan 逐帧取整抖动
ZOOM_PRESCALE = 4.0
ZOOM_STEP = 0.0009
ZOOM_MAX = 1.12
FPS = 25

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\Deng.ttf",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
]

CATEGORY_HINTS = {
    "SCENIC": "自然风光与四季景致",
    "CULTURE": "历史人文与建筑细节",
    "EXPERIENCE": "手作体验与互动场景",
    "FOOD": "美食风味与烟火气息",
    "LODGING": "住宿环境与旅居氛围",
    "LEISURE": "城市休闲与漫步时光",
}

CATEGORY_FALLBACK = {
    "SCENIC": [
        "晨光初照，{name}在薄雾中缓缓苏醒，湖光山色层层铺开。",
        "沿着小径慢慢深入，一步一景，每一帧都像走进画里。",
        "当夕阳落下，{name}归于宁静，把整座城市的温柔都收进眼底。",
    ],
    "CULTURE": [
        "推开{name}的大门，千年的故事在砖瓦与飞檐间静静流淌。",
        "每一件展品都藏着时光的印记，值得你放慢脚步细细品味。",
        "离开前回望一眼，{name}的余韵会陪你走很远。",
    ],
    "EXPERIENCE": [
        "走进{name}，亲手体验一次传统手艺，感受指尖的温度。",
        "在专注的时光里，时间慢了下来，成就感一点点长出来。",
        "带着作品离开，也带走一份关于{city}的独特记忆。",
    ],
    "FOOD": [
        "循着香气走进{name}，地道风味在热气里扑面而来。",
        "一口下去，是{city}最朴实也最动人的烟火气。",
        "好味道值得专程而来，{name}的这口鲜，等你自己尝。",
    ],
    "LODGING": [
        "抵达{name}，窗外的风景成了房间里最好的装饰。",
        "一盏暖灯、一张好床，旅途的疲惫在这里慢慢放下。",
        "醒来便是新的一天，{name}的清晨安静又治愈。",
    ],
    "LEISURE": [
        "在{name}随意走走，街头巷尾都是{city}的生活气息。",
        "找一个角落坐下来，看人流往来，时间忽然变得很轻。",
        "黄昏时分灯光亮起，{name}的夜晚才刚刚开始。",
    ],
}

DEFAULT_SHOTS = [
    "欢迎来到{city}{name}，一段值得慢慢展开的旅程从这里开始。",
    "这里的一砖一瓦、一草一木，都藏着只属于它的故事。",
    "期待你亲临其境，把这份美好收进自己的旅行记忆。",
]


def _ffmpeg_path() -> str:
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


def synthesize(request: VideoSynthesisRequest) -> bytes:
    """AI 成片主流程：解说词 → 配音 → 运镜/转场/字幕合成，返回 mp4 字节。"""
    if not request.images:
        raise ValueError("至少需要一张图片素材。")
    images = [image for image in request.images if image.strip()][:4]
    if not images:
        raise ValueError("图片素材为空。")

    shots = _build_shots(request, len(images))
    width, height = SIZES.get(request.aspect_ratio, SIZES["16:9"])
    font_path = _find_font()

    with tempfile.TemporaryDirectory(prefix="verntrip-video-") as work_dir:
        root = Path(work_dir)
        image_dir = root / "images"
        image_dir.mkdir()
        for index, image in enumerate(images):
            (image_dir / f"shot{index}.jpg").write_bytes(_decode_image(image))

        audio_path = None
        try:
            audio_path = root / "narration.mp3"
            _tts("。".join(shot["narration"] for shot in shots), request.voice, audio_path)
        except Exception:
            logger.exception("TTS 合成失败，将生成无配音视频。")
            audio_path = None

        total = max(4.0, _audio_duration(audio_path) if audio_path else len(images) * 4.0)
        segments = _make_segments(root, image_dir, len(images), total, width, height, font_path)
        if not segments:
            raise RuntimeError("视频分段渲染失败。")
        output = _compose(root, segments, shots, total, width, height, font_path, audio_path)
        return output.read_bytes()


def _build_shots(request: VideoSynthesisRequest, image_count: int) -> list[dict]:
    """优先用 DeepSeek 按已核验事实生成分镜解说；失败时按类型模板兜底。"""
    shots = _build_shots_with_deepseek(request, image_count)
    if shots:
        return shots
    templates = CATEGORY_FALLBACK.get(request.category)
    if not templates:
        shots = [{"narration": item.format(name=request.name, city=request.city)} for item in DEFAULT_SHOTS]
    else:
        shots = [
            {"narration": item.format(name=request.name, city=request.city)}
            for item in templates[:image_count]
        ]
    return shots[:image_count]


def _build_shots_with_deepseek(request: VideoSynthesisRequest, image_count: int) -> list[dict] | None:
    client = DeepSeekClient()
    if not client.is_enabled():
        return None
    facts = normalize_whitespace(request.facts) or "暂无补充事实"
    messages = [
        {
            "role": "system",
            "content": (
                "你是文旅短视频解说编剧。根据给定的景点事实，为 AI 成片撰写分镜口播解说。"
                "严格输出 JSON 数组，每项只有 narration 一个字段；"
                "每句 25-50 字，口语化、有画面感，按视觉顺序递进；"
                "不得编造事实，不得出现价格数字以外的营销承诺。"
            ),
        },
        {
            "role": "user",
            "content": (
                f"景点：{request.name}（{request.city}）\n"
                f"类型：{CATEGORY_HINTS.get(request.category, '通用')}\n"
                f"已核验事实：{facts}\n"
                f"分镜数量：{image_count}\n"
                "请输出 JSON 数组。"
            ),
        },
    ]
    try:
        content = client.chat(messages, temperature=0.7, max_tokens=600)
        payload = _extract_json_object(content)
        shots = []
        for item in payload if isinstance(payload, list) else []:
            if not isinstance(item, dict):
                continue
            narration = str(item.get("narration", "")).strip()
            if narration:
                shots.append({"narration": narration[:80]})
        if not shots:
            return None
        return shots[:image_count]
    except (DeepSeekError, ValueError, json.JSONDecodeError):
        return None


def _extract_json_object(content: str) -> object:
    """剥掉 markdown 围栏后解析 JSON（与 planning/summary 模块同款思路）。"""
    text = content.strip()
    fence = re.search(r"```(?:json)?\s*(.*?)```", text, re.DOTALL)
    if fence:
        text = fence.group(1).strip()
    start = text.find("[")
    if start < 0:
        raise ValueError("no json array found")
    return json.loads(text[start:])


def _tts(text: str, voice: str, output: Path) -> None:
    import edge_tts

    async def run() -> None:
        communicate = edge_tts.Communicate(text, voice)
        await communicate.save(str(output))

    asyncio.run(run())


def _audio_duration(path: Path) -> float:
    result = subprocess.run(
        [_ffmpeg_path(), "-i", str(path)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    match = re.search(r"Duration:\s*(\d+):(\d+):(\d+\.\d+)", result.stderr)
    if not match:
        return 4.0
    hours, minutes, seconds = match.groups()
    return int(hours) * 3600 + int(minutes) * 60 + float(seconds)


def _make_segments(
    root: Path,
    image_dir: Path,
    count: int,
    total: float,
    width: int,
    height: int,
    font_path: str | None,
) -> list[Path]:
    """每张图渲染一段 Ken Burns 运镜片段。"""
    segments = []
    per = total / count
    duration = round(per + 0.6, 2)  # 每段略长于平均时长，供 xfade 重叠
    frames = int(per * FPS) + 1
    for index in range(count):
        output = root / f"seg{index}.mp4"
        result = subprocess.run(
            [
                _ffmpeg_path(), "-y",
                "-loop", "1", "-i", str(image_dir / f"shot{index}.jpg"),
                "-vf",
                (
                    f"scale={width * ZOOM_PRESCALE:.0f}:{height * ZOOM_PRESCALE:.0f}:force_original_aspect_ratio=increase,"
                    f"crop={width * ZOOM_PRESCALE:.0f}:{height * ZOOM_PRESCALE:.0f},"
                    f"zoompan=z='min(zoom+{ZOOM_STEP},{ZOOM_MAX})':"
                    f"x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':"
                    f"d={frames}:s={width}x{height}:fps={FPS},format=yuv420p"
                ),
                "-t", str(duration),
                "-c:v", "libx264", "-preset", "veryfast",
                "-pix_fmt", "yuv420p", "-profile:v", "main",
                str(output),
            ],
            capture_output=True,
        )
        if result.returncode != 0:
            logger.warning("segment %s failed: %s", index, result.stderr.decode("utf-8", errors="replace")[-500:])
            return []
        segments.append(output)
    return segments


def _compose(
    root: Path,
    segments: list[Path],
    shots: list[dict],
    total: float,
    width: int,
    height: int,
    font_path: str | None,
    audio_path: Path | None,
) -> Path:
    """xfade 转场拼接 + 分镜字幕 + 配音混入。工作目录内相对路径，规避 Windows 盘符冒号。"""
    fade = 0.8
    chain = []
    previous = "0:v"
    for index in range(1, len(segments)):
        offset = round(index * (total / len(segments)) - index * fade, 2)
        node = f"v{index:02d}"
        chain.append(f"[{previous}][{index}:v]xfade=transition=fade:duration={fade}:offset={offset}[{node}]")
        previous = node
    chain.append(f"[{previous}]")
    subtitle_filters = []
    if font_path and shots:
        local_font = root / "font.ttf"
        shutil.copy(font_path, local_font)
        for index, shot in enumerate(shots):
            subtitle = (root / f"sub{index}.txt")
            subtitle.write_text(shot["narration"], encoding="utf-8")
            start = round(max(0.0, index * (total / len(shots)) - index * fade + 0.4), 2)
            end = round(start + total / len(shots), 2)
            subtitle_filters.append(
                f"drawtext=fontfile=font.ttf:textfile=sub{index}.txt:"
                f"fontsize={max(24, height // 24)}:fontcolor=white:borderw=2:bordercolor=black@0.6:"
                f"x=(w-text_w)/2:y=h-{max(70, height // 9)}:enable=between(t\\,{start}\\,{end})"
            )
    filters = ";".join(chain)
    if subtitle_filters:
        filters += ",".join(subtitle_filters)
    filters += "[v]"

    args = [
        _ffmpeg_path(), "-y",
    ]
    for segment in segments:
        args += ["-i", segment.name]
    if audio_path:
        args += ["-i", audio_path.name]
    args += [
        "-filter_complex", filters,
        "-map", "[v]",
    ]
    if audio_path:
        args += ["-map", f"{len(segments)}:a", "-c:a", "aac", "-b:a", "128k"]
    args += [
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "22",
        "-pix_fmt", "yuv420p", "-profile:v", "main", "-color_range", "tv",
        "-t", str(round(total, 2)),
        "-movflags", "+faststart",
        "output.mp4",
    ]
    result = subprocess.run(args, cwd=root, capture_output=True)
    if result.returncode != 0:
        raise RuntimeError("视频合成失败：" + result.stderr.decode("utf-8", errors="replace")[-800:])
    return root / "output.mp4"


def _find_font() -> str | None:
    for candidate in FONT_CANDIDATES:
        if os.path.isfile(candidate):
            return candidate
    return None


def _decode_image(value: str) -> bytes:
    if "," in value and value[:32].lstrip().lower().startswith("data:image"):
        value = value.split(",", 1)[1]
    return base64.b64decode(value)
