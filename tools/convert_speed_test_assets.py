"""将测网速页面（Figma 6554:757 / 6554:793 / 6565:1676）与首页入口图标的原版资源转为透明 WebP。

开发时运行，应用内不做转换。来源记录在 design/figma/speed-test/assets.json：
- 位图填充（入口图标、Wi-Fi 插画、失败盾牌）取 Figma 原图，按节点尺寸等比 contain 到 4x 母版；
- 矢量图标用 tools/render_svg_png.js 以 4 倍栅格化；结果头部背景为原版渐变 + 遮罩组矢量，2x nodpi。
从仓库根目录运行 python3 tools/convert_speed_test_assets.py（需要 Pillow）。
"""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
DENSITIES = [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]

for asset in json.loads((ROOT / 'design/figma/speed-test/assets.json').read_text()):
    source = ROOT / asset['source']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == asset['sha256'], source
    with Image.open(source) as original:
        bitmap = original.convert('RGBA')
        scale = asset['sourceScale']
        assert bitmap.size == tuple(dp * scale for dp in asset['dp']), (source, bitmap.size)
        # 低频渐变背景由 ImageView 拉伸，单份 nodpi 控制解码内存；其余按密度输出。
        variants = [('nodpi', None)] if asset['density'] == 'nodpi' else DENSITIES
        for density, factor in variants:
            size = bitmap.size if density == 'nodpi' else tuple(round(dp * factor) for dp in asset['dp'])
            output = bitmap if size == bitmap.size else bitmap.resize(size, Image.Resampling.LANCZOS)
            target = RES / f'drawable-{density}' / f"{asset['resource']}.webp"
            target.parent.mkdir(parents=True, exist_ok=True)
            output.save(target, 'WEBP', lossless=True, method=6, exact=True)
            print(target.relative_to(ROOT), output.size, target.stat().st_size, 'bytes')
