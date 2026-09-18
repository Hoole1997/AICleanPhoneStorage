"""Figma original transparent Similar icon -> Android density WebP. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageOps
import hashlib,json
root=Path(__file__).resolve().parents[1]
source=root/'design/figma/similar-cleaner/source/ic_tool_similar.png'
with Image.open(source) as original:
    for density,scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
        size=round(32*scale)
        image=Image.new('RGBA',(size,size))
        icon=ImageOps.contain(original.convert('RGBA'),(size,size),Image.Resampling.LANCZOS)
        image.alpha_composite(icon,((size-icon.width)//2,(size-icon.height)//2))
        image.save(root/f'app/src/main/res/drawable-{density}/ic_tool_similar.webp','WEBP',lossless=True,exact=True)
(root/'design/figma/similar-cleaner/assets.json').write_text(json.dumps({'file_key':'dVsTL6ggoDPXVPcEKg856G','node':'6194:1725','source':str(source.relative_to(root)),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'dp':32,'screen_node':'5986:996','reused_icons':['cleanup_photo_check_on','cleanup_photo_check_off','ic_traffic_back']},indent=2)+'\n')
