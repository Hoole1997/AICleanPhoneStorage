"""将 Figma 原版导出转为 Android WebP；开发时运行，应用内不做转换。

pip install Pillow 后从仓库根目录运行 python3 tools/convert_home_assets.py。
PNG 原件保留在 design 中以便核对；渐变使用 nodpi 控制解码内存，图标与插画生成各密度版本。
"""
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/figma/home/source'
RES = ROOT / 'app/src/main/res'
NODES = {
    'home_robot': '5548:69', 'home_header_background': '5548:34',
    'home_hero_background': '5548:68', 'ic_home_settings': '5548:60',
    'ic_home_clean': '5548:86', 'ic_home_download': '5548:105',
    'ic_home_available': '5548:113', 'ic_home_used': '5548:121',
    'ic_home_more': '5548:142', 'ic_tool_network': '5548:134',
    'ic_tool_notifications': '5548:147', 'ic_tool_apps': '5548:161',
    'ic_tool_compress': '5548:174', 'ic_tool_large_files': '5548:188',
    'ic_tool_unused_files': '5548:201', 'ic_tool_screenshots': '5548:215',
}
manifest = []
for name, node in NODES.items():
    source = SOURCE / f'{name}.png'
    with Image.open(source) as original:
        bitmap = original.convert('RGBA')
        crop = None
        if name == 'home_robot':
            # 去掉全透明留白，保留所有发光/阴影像素；原版 PNG 始终保留。
            crop = bitmap.getbbox()
            bitmap = bitmap.crop(crop)
        if name.endswith('background'):
            size = (375, 334) if name == 'home_header_background' else (343, 286)
            variants = [('nodpi', size)]
        else:
            variants = [(density, (round(bitmap.width * scale / 3), round(bitmap.height * scale / 3)))
                        for density, scale in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]]
        for density, size in variants:
            output = bitmap.resize(size, Image.Resampling.LANCZOS) if bitmap.size != size else bitmap
            target = RES / f'drawable-{density}' / f'{name}.webp'
            target.parent.mkdir(parents=True, exist_ok=True)
            output.save(target, 'WEBP', lossless=True, method=6, exact=True)
            manifest.append({
                'resource': name, 'node': node, 'file_key': 'dVsTL6ggoDPXVPcEKg856G',
                'source': str(source.relative_to(ROOT)), 'source_size': original.size,
                'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                'crop': crop, 'density': density, 'output_size': output.size,
                'output': str(target.relative_to(ROOT)), 'webp_bytes': target.stat().st_size,
            })
(ROOT / 'design/figma/home/assets.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(f'{len(NODES)} original assets; {len(manifest)} density variants; {sum(x["webp_bytes"] for x in manifest):,} bytes total')
