"""Figma 7121:9029 / 7121:9151 原版资源，开发时转为各密度透明 WebP。

用法：python tools/convert_new_user_assets.py <node> <sharp模块路径>
不修改 SVG 路径，不合成整页截图；来源与实际尺寸记录在 assets.json。
"""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
manifest = ROOT / 'design/figma/new-user/assets.json'
assets = json.loads(manifest.read_text())
for asset in assets:
    source = ROOT / asset['originalRaster'] if 'originalRaster' in asset else ROOT / 'design/figma/new-user/source' / (asset['name'] + Path(asset['url']).suffix)
    rendered = source
    if source.suffix == '.svg':
        rendered = source.with_suffix('.png')
        subprocess.run([sys.argv[1], str(ROOT / 'tools/render_svg_png.js'), sys.argv[2], str(source), str(rendered), '4'], check=True)
    with Image.open(rendered) as original:
        bitmap = original.convert('RGBA')
        # Figma phone 是图片填充；设计槽位为 52×66，按原稿 object-cover 裁切。
        if asset['name'] == 'phone':
            from PIL import ImageOps
            bitmap = ImageOps.fit(bitmap, (208, 264), Image.Resampling.LANCZOS)
        asset.update(source=str(source.relative_to(ROOT)), sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                     resource='new_user_' + asset['name'], pixels4x=list(bitmap.size))
        variants = [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]
        if asset['name'] == 'background':
            # 使用 Figma 单独导出的背景图层，保留原生高斯模糊；单份 nodpi 足以表达低频渐变。
            asset['pixels4x'] = None
            asset['pixels'] = list(bitmap.size)
            variants = [('nodpi', 4)]
        for density, factor in variants:
            size = tuple(round(px * factor / 4) for px in bitmap.size)
            target = ROOT / f'app/src/main/res/drawable-{density}' / (asset['resource'] + '.webp')
            target.parent.mkdir(parents=True, exist_ok=True)
            bitmap.resize(size, Image.Resampling.LANCZOS).save(target, 'WEBP', lossless=True, exact=True, method=6)
manifest.write_text(json.dumps(assets, indent=2) + '\n')
