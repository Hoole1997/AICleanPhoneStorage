"""Convert original Figma settings SVGs at build time; runtime uses local WebP only.

Install tools/requirements-app-manager-assets.txt in the asset environment first.
"""
from pathlib import Path
import io
import json
import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/figma/settings'
for name, asset in json.loads((SOURCE / 'source.json').read_text())['assets'].items():
    if 'reuseDrawable' in asset:
        continue
    for density, scale in {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}.items():
        size = round(asset['sizeDp'] * scale)
        png = cairosvg.svg2png(url=str(SOURCE / 'source' / f'{name}.svg'), output_width=size, output_height=size)
        target = ROOT / f'app/src/main/res/drawable-{density}/ic_settings_{name}.webp'
        Image.open(io.BytesIO(png)).convert('RGBA').save(target, 'WEBP', lossless=True)
