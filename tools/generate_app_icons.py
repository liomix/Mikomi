#!/usr/bin/env python3
"""生成 Mikomi 的应用图标资源（Pillow）。

用法：
    python tools/generate_app_icons.py [原图路径] [样式 id]

    python tools/generate_app_icons.py D:/AAA/mikomi-green.png
        样式 id 从文件名推断（mikomi-green → green）
    python tools/generate_app_icons.py D:/AAA/mikomi-green.png green
        也可显式指定样式 id

默认原图：D:/AAA/mikomi-pink.png

产物（`<id>` 为样式 id）：
    app/src/main/res/drawable-nodpi/ic_launcher_<id>_foreground.png
        自适应图标前景层：432x432 画布，内容缩到 288 居中（对应 108dp 画布的
        72dp 安全区），四周留透明，避免被系统裁掉边角。
    app/src/main/res/mipmap-<dpi>/ic_launcher_<id>.png
        API < 26 的方形图标（48 / 72 / 96 / 144 / 192）。
    feature/settings/src/main/res/drawable-nodpi/settings_icon_<id>.png
        设置页预览图（256x256）：裁掉透明留白后铺满画布。
    feature/settings/src/main/res/drawable-nodpi/settings_icon_<id>_glyph.png
        无底色图标（256x256，透明背景），供提示弹窗等需要"浮在背景上"的场景使用。
        素材的图形区域内部通常是白底（透明只出现在画布外圈），所以用小容差
        洪泛填充把与边缘连通的白色剥离。

每次运行还会顺带刷新默认图标（`default`）的预览图与无底色图标。
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

# 项目根目录（tools/ 的上一级）。
PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = Path("D:/AAA/mikomi-pink.png")

APP_RES = PROJECT_ROOT / "app/src/main/res"
SETTINGS_RES = PROJECT_ROOT / "feature/settings/src/main/res"

# 自适应图标：108dp 画布按 4x 处理，前景内容限制在中央 72dp（288px）。
FOREGROUND_CANVAS = 432
FOREGROUND_CONTENT = 288

# 设置页预览尺寸。
PREVIEW_SIZE = 256

# 默认图标（蓝色那张）已在仓库中的前景层，预览由它生成，无需外部原图。
DEFAULT_FOREGROUND = APP_RES / "drawable-nodpi/ic_launcher_foreground.png"

# 抠背景容差：原图背景是均匀的浅灰白，发丝边缘同色，容差必须很小
# （粉色素材的发丝是 (254,254,253)、背景是 (253,253,253)，只差 1）。
GLYPH_BG_THRESH = 4

# 方形兜底图标的各 dpi 尺寸。
SQUARE_ICONS = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def derive_key(source: Path) -> str:
    """从文件名推断样式 id：mikomi-green.png → green。"""
    stem = source.stem
    return stem.rsplit("-", 1)[-1] if "-" in stem else stem


def load_source(path: Path) -> Image.Image:
    if not path.is_file():
        raise SystemExit(f"原图不存在: {path}")
    image = Image.open(path).convert("RGBA")
    print(f"原图: {path}  {image.width}x{image.height}")
    return image


def load_default_foreground() -> Image.Image:
    """读取默认图标的前景层（缺失时提示先构建资源）。"""
    if not DEFAULT_FOREGROUND.is_file():
        raise SystemExit(f"默认图标前景不存在: {DEFAULT_FOREGROUND}")
    return Image.open(DEFAULT_FOREGROUND).convert("RGBA")


def save_square(image: Image.Image, size: int, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    image.resize((size, size), Image.LANCZOS).save(target, "PNG", optimize=True)
    print(f"  -> {target.relative_to(PROJECT_ROOT)}  {size}x{size}")


def save_foreground(image: Image.Image, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    canvas = Image.new("RGBA", (FOREGROUND_CANVAS, FOREGROUND_CANVAS), (0, 0, 0, 0))
    offset = (FOREGROUND_CANVAS - FOREGROUND_CONTENT) // 2
    canvas.paste(
        image.resize((FOREGROUND_CONTENT, FOREGROUND_CONTENT), Image.LANCZOS),
        (offset, offset),
    )
    canvas.save(target, "PNG", optimize=True)
    print(
        f"  -> {target.relative_to(PROJECT_ROOT)}  "
        f"{FOREGROUND_CANVAS}x{FOREGROUND_CANVAS}（内容 {FOREGROUND_CONTENT}）"
    )


def save_preview(image: Image.Image, target: Path) -> None:
    """生成设置页预览图：裁掉透明留白后铺满画布。

    默认图标的前景是 108dp 画布里的 72dp 内容（四周有透明边），直接当预览会
    比其它样式小一圈；这里统一裁掉留白再缩放，卡片里各图标视觉尺寸一致。
    """
    target.parent.mkdir(parents=True, exist_ok=True)
    content = image.convert("RGBA")
    bbox = content.getbbox()
    if bbox:
        content = content.crop(bbox)
    # 非正方形时补透明边成正方形，避免缩放变形。
    side = max(content.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(content, ((side - content.width) // 2, (side - content.height) // 2))
    square.resize((PREVIEW_SIZE, PREVIEW_SIZE), Image.LANCZOS).save(
        target, "PNG", optimize=True
    )
    print(f"  -> {target.relative_to(PROJECT_ROOT)}  {PREVIEW_SIZE}x{PREVIEW_SIZE}")


def save_glyph(image: Image.Image, target: Path, remove_background: bool) -> None:
    """生成无底色图标：透明背景、裁掉留白后铺满画布。

    @param remove_background 是否从四角洪泛填充剥离白底。素材的图形区域内部
        通常是白底（透明只出现在画布外圈），所以需要剥离。
    """
    target.parent.mkdir(parents=True, exist_ok=True)
    content = image.convert("RGBA")
    # 先裁掉画布外的透明留白，让洪泛填充能从白底的四个角开始。
    bbox = content.getbbox()
    if bbox:
        content = content.crop(bbox)
    if remove_background:
        width, height = content.size
        for corner in ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)):
            ImageDraw.floodfill(content, corner, (0, 0, 0, 0), thresh=GLYPH_BG_THRESH)
        # 抠掉背景后再收紧一次边界。
        trimmed = content.getbbox()
        if trimmed:
            content = content.crop(trimmed)
    side = max(content.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(content, ((side - content.width) // 2, (side - content.height) // 2))
    square.resize((PREVIEW_SIZE, PREVIEW_SIZE), Image.LANCZOS).save(
        target, "PNG", optimize=True
    )
    print(f"  -> {target.relative_to(PROJECT_ROOT)}  {PREVIEW_SIZE}x{PREVIEW_SIZE}(透明)")


def main(argv: list[str]) -> int:
    source = Path(argv[1]) if len(argv) > 1 else DEFAULT_SOURCE
    key = argv[2] if len(argv) > 2 else derive_key(source)
    image = load_source(source)
    print(f"样式 id: {key}")

    print("生成方形启动图标：")
    for density, size in SQUARE_ICONS.items():
        save_square(image, size, APP_RES / f"mipmap-{density}/ic_launcher_{key}.png")

    print("生成自适应图标前景：")
    save_foreground(image, APP_RES / f"drawable-nodpi/ic_launcher_{key}_foreground.png")

    print("生成设置页预览：")
    save_preview(image, SETTINGS_RES / f"drawable-nodpi/settings_icon_{key}.png")
    save_preview(
        load_default_foreground(),
        SETTINGS_RES / "drawable-nodpi/settings_icon_default.png",
    )

    print("生成无底色图标（提示弹窗用）：")
    save_glyph(
        image,
        SETTINGS_RES / f"drawable-nodpi/settings_icon_{key}_glyph.png",
        remove_background=True,
    )
    save_glyph(
        load_default_foreground(),
        SETTINGS_RES / "drawable-nodpi/settings_icon_default_glyph.png",
        remove_background=True,
    )

    print("完成。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
