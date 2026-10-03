# -*- coding: utf-8 -*-
"""本地免费合成演示：图片 + edge-tts 配音 + ffmpeg 运镜/转场/字幕 → mp4"""
import asyncio
import os
import subprocess
import sys

sys.path.insert(0, r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\.tmp-video-demo-venv\Lib\site-packages")
import edge_tts
import imageio_ffmpeg

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
BASE = os.path.dirname(os.path.abspath(__file__))
IMAGES = [
    r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\vernflow-web\public\images\banner1.jpg",
    r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\vernflow-web\public\images\banner2.jpg",
    r"D:\Aeolyn\MaraudersMap\Work\Computer\project\VernTrip\vernflow-web\public\images\banner3.jpg",
]
FONT = r"C:\Windows\Fonts\msyh.ttc"

NARRATION = (
    "欢迎来到杭州灵隐寺。晨雾未散，古刹钟声从青翠山间传来，千年禅意在石阶上缓缓铺开。"
    "沿着飞来峰的林间小径拾级而上，阳光穿过古木，把飞檐斗拱染成温柔的金色。"
    "这里的一草一木都带着故事，等你亲临，慢慢感受江南佛国的静谧与悠远。"
)
SENTENCES = [
    "欢迎来到杭州灵隐寺，晨雾未散，古刹钟声从青翠山间传来。",
    "沿飞来峰林间小径拾级而上，阳光穿过古木，把飞檐染成温柔的金色。",
    "这里的一草一木都带着故事，等你亲临，感受江南佛国的静谧与悠远。",
]
SEG_DURATION = 5.0
FADE = 0.8


def run(args):
    result = subprocess.run(args, capture_output=True)
    if result.returncode != 0:
        stderr = result.stderr.decode("utf-8", errors="replace")
        print("FFMPEG STDERR (head):")
        print(stderr[:2000])
        raise SystemExit(result.returncode)


async def make_audio():
    communicate = edge_tts.Communicate(NARRATION, "zh-CN-YunxiNeural", rate="+0%")
    await communicate.save(os.path.join(BASE, "narration.mp3"))
    print("narration.mp3 done")


def make_segments():
    for i, image in enumerate(IMAGES):
        out = os.path.join(BASE, f"seg{i}.mp4")
        run([
            FFMPEG, "-y", "-loop", "1", "-i", image,
            "-vf",
            "zoompan=z='min(zoom+0.0016,1.18)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
            ":d=125:s=1280x720:fps=25",
            "-t", str(SEG_DURATION), "-c:v", "libx264", "-preset", "veryfast",
            "-pix_fmt", "yuv420p", out,
        ])
    print("segments done")


def make_subtitles():
    for i, sentence in enumerate(SENTENCES):
        with open(os.path.join(BASE, f"sub{i}.txt"), "w", encoding="utf-8") as fh:
            fh.write(sentence)
    print("subtitles done")


def compose():
    total = len(IMAGES) * SEG_DURATION
    off1 = SEG_DURATION - FADE
    off2 = off1 + SEG_DURATION - FADE
    sub_filters = []
    for i in range(len(SENTENCES)):
        start = max(0.0, i * SEG_DURATION - (i * FADE) + 0.4)
        end = start + SEG_DURATION
        # 该 ffmpeg 构建的过滤器参数无法处理盘符冒号：字体复制到工作目录用相对路径；
        # enable 表达式内的逗号必须反斜杠转义，否则会被图解析器切分。
        sub_filters.append(
            f"drawtext=fontfile=Deng.ttf:textfile=sub{i}.txt:"
            f"fontsize=30:fontcolor=white:borderw=2:bordercolor=black@0.6:"
            f"x=(w-text_w)/2:y=h-110:enable=between(t\\,{start:.1f}\\,{end:.1f})"
        )
    filters = (
        f"[0:v][1:v]xfade=transition=fade:duration={FADE}:offset={off1}[v01];"
        f"[v01][2:v]xfade=transition=fade:duration={FADE}:offset={off2}[v01b];"
        f"[v01b]{','.join(sub_filters)},format=yuv420p[v]"
    )
    run([
        FFMPEG, "-y",
        "-i", "seg0.mp4",
        "-i", "seg1.mp4",
        "-i", "seg2.mp4",
        "-i", "narration.mp3",
        "-filter_complex", filters,
        "-map", "[v]", "-map", "3:a",
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "22",
        "-pix_fmt", "yuv420p", "-profile:v", "main", "-color_range", "tv",
        "-c:a", "aac", "-b:a", "128k",
        "-t", str(total),
        "-movflags", "+faststart",
        "lingyin-demo.mp4",
    ])
    print("composed: lingyin-demo.mp4")


def main():
    os.chdir(BASE)
    import shutil
    shutil.copy(FONT, os.path.join(BASE, "Deng.ttf"))
    asyncio.run(make_audio())
    make_segments()
    make_subtitles()
    compose()
    size = os.path.getsize(os.path.join(BASE, "lingyin-demo.mp4"))
    print(f"demo size: {size / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
