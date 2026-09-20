"""Convert original Figma permission artwork to transparent, bounded Android WebP assets.
Requires Pillow and CairoSVG; run from any directory. No runtime conversion.
"""
from pathlib import Path
from io import BytesIO
from PIL import Image, ImageOps
import cairosvg, hashlib, json
ROOT = Path(__file__).resolve().parents[1]
assets = [
    ('permission_sheet_photos', 'design/figma/permission-sheet/source/photos.png', (93, 100), '6044:6989'),
    ('permission_sheet_shadow', 'design/figma/permission-sheet/source/shadow.svg', (63, 8), '6044:6988'),
    ('permission_sheet_video', 'design/figma/video-cleaner/source/ic_tool_videos.png', (93, 100), '6194:1711'),
]
records=[]
for name, source, dp, node in assets:
    path=ROOT/source
    data=path.read_bytes()
    for density, scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
        size=tuple(round(x*scale) for x in dp)
        encoded=cairosvg.svg2png(bytestring=data,output_width=size[0],output_height=size[1]) if path.suffix=='.svg' else data
        with Image.open(BytesIO(encoded)) as original:
            icon=ImageOps.contain(original.convert('RGBA'),size,Image.Resampling.LANCZOS)
            canvas=Image.new('RGBA',size)
            canvas.alpha_composite(icon,((size[0]-icon.width)//2,(size[1]-icon.height)//2))
            target=ROOT/f'app/src/main/res/drawable-{density}/{name}.webp'
            canvas.save(target,'WEBP',lossless=True,exact=True)
    records.append(dict(resource=name,source=source,node=node,file_key='dVsTL6ggoDPXVPcEKg856G',dp=dp,sha256=hashlib.sha256(data).hexdigest()))
(ROOT/'design/figma/permission-sheet/assets.json').write_text(json.dumps(records,indent=2)+'\n')
