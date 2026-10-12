"""将首页入口角标（及角标显示时的红色清理图标）的 Figma 原版导出转为各密度透明 WebP；开发时运行，应用内不做转换。

来源：design/figma/home/badges/assets.json（文件 dVsTL6ggoDPXVPcEKg856G，帧 6562:1177）。
source/ 保存 Figma 导出的原版 SVG 及其 4x（xxxhdpi）透明 PNG 渲染：
    node tools/render_svg_png.js <sharp> <svg> <png> 4   # density=288，即 4 倍
pip install Pillow 后从仓库根目录运行 python3 tools/convert_home_badge_assets.py。
"""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
DENSITIES = [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]

for asset in json.loads((ROOT / 'design/figma/home/badges/assets.json').read_text()):
    source = ROOT / asset['source']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == asset['sha256'], source
    with Image.open(source) as original:
        bitmap = original.convert('RGBA')
        scale = asset['sourceScale']
        assert bitmap.size == tuple(dp * scale for dp in asset['dp']), (source, bitmap.size)
        for density, factor in DENSITIES:
            size = tuple(round(dp * factor) for dp in asset['dp'])
            output = bitmap if size == bitmap.size else bitmap.resize(size, Image.Resampling.LANCZOS)
            target = RES / f'drawable-{density}' / f"{asset['resource']}.webp"
            output.save(target, 'WEBP', lossless=True, method=6, exact=True)
            print(target.relative_to(ROOT), output.size, target.stat().st_size, 'bytes')
