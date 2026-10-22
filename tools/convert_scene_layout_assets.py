"""Figma 7136:4768 原版素材的离线密度转换及 XML 动效。
layout-source 中 SVG 的同名 PNG 是通过 sharp 以 density=288 导出，不在运行时转换。
"""
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw
import json, hashlib, math
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/figma/scene-push/layout-source'
RES = ROOT / 'notification/src/main/res'
manifest = []
for name, size in [('logo', (28, 28)), ('arrow-left', (24, 24)), ('arrow-right', (24, 24))]:
    path = SOURCE / (name + '.png')
    with Image.open(path) as source:
        if name.startswith('arrow'):
            canvas = Image.new('RGBA', (96, 96))
            canvas.alpha_composite(source.convert('RGBA').resize((85, 48), Image.Resampling.LANCZOS), (5, 24))
        else:
            canvas = ImageOps.fit(source.convert('RGBA'), (size[0]*4, size[1]*4), Image.Resampling.LANCZOS)
            if name == 'logo':
                # Figma 圆形裁切，保持原图内容；不是重绘图标。
                mask = Image.new('L', canvas.size); ImageDraw.Draw(mask).ellipse((0, 0, canvas.width-1, canvas.height-1), fill=255)
                canvas.putalpha(mask)
        resource = 'scene_layout_' + name.replace('-', '_')
        for density, scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
            canvas.resize((round(size[0]*scale),round(size[1]*scale)),Image.Resampling.LANCZOS).save(RES/f'drawable-{density}'/(resource+'.webp'), 'WEBP',lossless=True,exact=True,method=6)
        manifest.append(dict(node='7136:4768', source=str(path.relative_to(ROOT)),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),resource=resource,dp=size))
(SOURCE.parent/'layout-assets.json').write_text(json.dumps(manifest,indent=2)+'\n')
# 按钮 XML 由独立生成器统一维护，重跑资产转换不能退回旧版动效。
from generate_notification_motion import generate
generate(RES)
frames=[]
for index in range(32):
    offset=3*(1-math.cos(2*math.pi*index/32))/2
    frames.append(f'    <item android:duration="30"><layer-list><item android:width="24dp" android:height="24dp" android:gravity="center" android:left="{offset:.3f}dp" android:right="{-offset:.3f}dp"><bitmap android:src="@drawable/scene_layout_arrow_left" android:gravity="fill" /></item></layer-list></item>')
(RES/'drawable/scene_arrow_motion.xml').write_text('<animation-list xmlns:android="http://schemas.android.com/apk/res/android" android:oneshot="false">\n'+'\n'.join(frames)+'\n</animation-list>\n')
(RES/'drawable/scene_arrow_motion_end.xml').write_text((RES/'drawable/scene_arrow_motion.xml').read_text().replace('scene_layout_arrow_left','scene_layout_arrow_right'))
