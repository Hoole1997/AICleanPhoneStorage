"""Build-time conversion of original Figma SVG exports into density-specific WebP."""
from pathlib import Path
import io
import json
import cairosvg
from PIL import Image
ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'design/figma/app-manager'
for name, asset in json.loads((source / 'source.json').read_text())['assets'].items():
    for density, scale in {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}.items():
        size = round(asset['sizeDp'] * scale)
        png = cairosvg.svg2png(url=str(source / 'source' / f'{name}.svg'), output_width=size, output_height=size)
        target = ROOT / f'app/src/main/res/drawable-{density}/ic_app_manager_{name}.webp'
        target.parent.mkdir(parents=True, exist_ok=True)
        Image.open(io.BytesIO(png)).convert('RGBA').save(target, 'WEBP', lossless=True)
