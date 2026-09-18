"""Convert original Figma video icons to transparent density-specific WebP assets.

Run with Pillow and CairoSVG installed. Sources and node IDs are recorded in
 design/figma/video-cleaner/assets.json; no runtime asset conversion is used.
"""
from pathlib import Path
from io import BytesIO
import json
import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "design/figma/video-cleaner"
for asset in json.loads((DESIGN / "assets.json").read_text()):
    name = asset["resource"]
    source = next((DESIGN / "source").glob(name + ".*"))
    data = source.read_bytes()
    for density, scale in [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]:
        size = round(asset["dp"] * scale)
        encoded = cairosvg.svg2png(bytestring=data, output_width=size, output_height=size) if source.suffix == ".svg" else data
        with Image.open(BytesIO(encoded)) as original:
            image = original.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
            target = ROOT / f"app/src/main/res/drawable-{density}/{name}.webp"
            target.parent.mkdir(parents=True, exist_ok=True)
            image.save(target, "WEBP", lossless=True, exact=True)
