"""Convert the two official Figma logo exports into bounded density-specific WebP resources."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/figma/app-logo/source'
RES = ROOT / 'app/src/main/res'
square=Image.open(SOURCE/'square.png').convert('RGBA')
rounded_export=Image.open(SOURCE/'rounded.png').convert('RGBA')
# Figma export flattens the canvas gray into the corner pixels. Both supplied nodes have
# identical artwork; restore the rounded node's exact 106px clip from the square original.
mask=Image.new('L',(512*4,512*4),0)
ImageDraw.Draw(mask).rounded_rectangle((0,0,512*4,512*4),radius=106*4,fill=255)
mask=mask.resize((512,512),Image.Resampling.LANCZOS)
rounded=square.copy()
rounded.putalpha(mask)
for density,scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
    drawable=RES/f'drawable-{density}'; drawable.mkdir(exist_ok=True)
    mipmap=RES/f'mipmap-{density}'; mipmap.mkdir(exist_ok=True)
    for name,source in [('app_logo_square',square),('app_logo_rounded',rounded)]:
        size=round(128*scale)
        source.resize((size,size),Image.Resampling.LANCZOS).save(drawable/f'{name}.webp',lossless=True,method=6)
    for name,source in [('ic_launcher',square),('ic_launcher_round',rounded)]:
        size=round(48*scale)
        source.resize((size,size),Image.Resampling.LANCZOS).save(mipmap/f'{name}.webp',lossless=True,method=6)
    # Small startup brand image avoids decoding the 128dp About/Splash asset for a 58dp icon.
    size=round(58*scale)
    rounded.resize((size,size),Image.Resampling.LANCZOS).save(drawable/'startup_logo.webp',lossless=True,method=6)
(ROOT/'design/figma/app-logo/source.json').write_text(json.dumps({'fileKey':'dVsTL6ggoDPXVPcEKg856G','squareNode':'5619:3150','roundedNode':'5619:3156','sourcePixels':[512,512],'displaySizesDp':{'app_logo_square':128,'app_logo_rounded':128,'startup_logo':58,'legacy_launcher':48},'adaptiveForegroundInset':'25% to keep the full original artwork inside circular and OEM masks','roundedAlpha':'Exact 106px Figma corner clip, canvas gray excluded','usage':{'launcher':'square + native adaptive background/mask','startup_brand':'rounded','system_splash':'rounded','about':'rounded'}},indent=2)+'\n')
