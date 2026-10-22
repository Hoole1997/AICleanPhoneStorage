"""Figma 卸载原版资源开发时转换，保留原稿的裁切、透明度和密度；运行时不读原图。
用法：python convert_retention_assets.py <node> <sharp>
"""
from pathlib import Path
import json, subprocess, sys, hashlib
from PIL import Image, ImageOps, ImageChops
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'design/figma/uninstall-retention'
assets = json.loads((BASE / 'assets.json').read_text())
images = {}
for a in assets:
    source = ROOT/a['originalRaster'] if 'originalRaster' in a else BASE / 'source' / (a['name'] + Path(a['url']).suffix)
    rendered = source
    if source.suffix == '.svg':
        rendered = source.with_suffix('.png')
        subprocess.run([sys.argv[1], str(ROOT/'tools/render_svg_png.js'), sys.argv[2], str(source), str(rendered), '4'],check=True)
    with Image.open(rendered) as im: images[a['name']] = im.convert('RGBA').copy()
    a.update(source=str(source.relative_to(ROOT)),sha256=hashlib.sha256(source.read_bytes()).hexdigest())

for a in assets:
    name=a['name']; bitmap=images[name]
    if name=='hero_mask': continue
    if name=='hero':
        # 原稿 x=-33、y=-78 的 433×445 图层，按 375×330 透明蒙版裁切。
        bitmap=ImageOps.fit(bitmap,(1732,1780),Image.Resampling.LANCZOS).crop((132,312,1632,1632))
        mask=images['hero_mask'].getchannel('A')
        bitmap.putalpha(ImageChops.multiply(bitmap.getchannel('A'),mask))
    elif name in ['junk','malware','similar','screenshots','large','notifications']:
        canvas=Image.new('RGBA',(180,180))
        if name=='malware':
            slot=Image.new('RGBA',(156,152))
            scaled=bitmap.resize((176,174),Image.Resampling.LANCZOS)
            slot.alpha_composite(scaled,(3,0))
            canvas.alpha_composite(slot,(12,16))
        elif name=='junk':
            slot=ImageOps.fit(bitmap,(138,152),Image.Resampling.LANCZOS)
            canvas.alpha_composite(slot,(20,16))
        elif name=='screenshots':
            slot=ImageOps.fit(bitmap,(155,152),Image.Resampling.LANCZOS)
            canvas.alpha_composite(slot,(12,16))
        else:
            slot=ImageOps.contain(bitmap,(152,152),Image.Resampling.LANCZOS)
            canvas.alpha_composite(slot,(16+(152-slot.width)//2,16+(152-slot.height)//2))
        bitmap=canvas
    a['resource']='retention_'+name
    a['pixels4x']=list(bitmap.size)
    variants=[('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]
    if name == 'hero': variants=[('nodpi',2)]
    if name == 'reason_background':
        variants=[('nodpi',4)]
        a['pixels']=list(bitmap.size)
        a['pixels4x']=None
    for density,factor in variants:
        size=tuple(round(px*factor/4) for px in bitmap.size)
        target=ROOT/f'app/src/main/res/drawable-{density}'/(a['resource']+'.webp')
        target.parent.mkdir(parents=True,exist_ok=True)
        bitmap.resize(size,Image.Resampling.LANCZOS).save(target,'WEBP',lossless=True,exact=True,method=6)
(BASE/'assets.json').write_text(json.dumps(assets,indent=2)+'\n')
