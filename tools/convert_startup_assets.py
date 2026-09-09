"""Build-time export of the original Figma startup illustration, without any UI text or system bars."""
from pathlib import Path
from PIL import Image
import json
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'design/figma/startup/source'
RES=ROOT/'app/src/main/res'
background=Image.open(SOURCE/'background-original.png').convert('RGBA')
for density,scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
    folder=RES/f'drawable-{density}';folder.mkdir(exist_ok=True)
    ratio=min(375*scale/background.width,1)
    background.resize((round(background.width*ratio),round(background.height*ratio)),Image.Resampling.LANCZOS).save(folder/'startup_background.webp',quality=92,method=6)
(ROOT/'design/figma/startup/source.json').write_text(json.dumps({'fileKey':'dVsTL6ggoDPXVPcEKg856G','screenNode':'5619:3118','backgroundNode':'5619:3119','backgroundMaxPixels':[852,1846],'officialLogoNodes':['5619:3150','5619:3156'],'notes':'Original background raster without system bars or text. Logo uses convert_app_logo_assets.py. Text and progress are native views.'},indent=2)+'\n')
