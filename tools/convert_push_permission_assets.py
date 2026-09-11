"""Development-only Figma export conversion. Uses requirements-app-manager-assets.txt."""
from pathlib import Path
from PIL import Image
import io
import cairosvg
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/figma/push-permission/source'
for density, scale in {'mdpi':1, 'hdpi':1.5, 'xhdpi':2, 'xxhdpi':3, 'xxxhdpi':4}.items():
    image = Image.open(SOURCE / 'bell.png').convert('RGBA')
    image.thumbnail((round(100 * scale), round(100 * scale)), Image.Resampling.LANCZOS)
    image.save(ROOT / f'app/src/main/res/drawable-{density}/push_permission_bell.webp', 'WEBP', lossless=True)
    size = round(24 * scale)
    png = cairosvg.svg2png(url=str(SOURCE / 'close.svg'), output_width=size, output_height=size)
    Image.open(io.BytesIO(png)).convert('RGBA').save(ROOT / f'app/src/main/res/drawable-{density}/ic_push_permission_close.webp', 'WEBP', lossless=True)
