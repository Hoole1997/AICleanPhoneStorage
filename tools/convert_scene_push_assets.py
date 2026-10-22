"""Figma 7133:404 原版图标按 56dp 设计槽位转换；运行时只使用本地 WebP。
原始位置和尺寸记录在 assets.json，不修改图标内容。"""
from pathlib import Path
from PIL import Image,ImageOps
import json,hashlib,math
ROOT=Path(__file__).resolve().parents[1]
base=ROOT/'design/figma/scene-push'
assets=json.loads((base/'assets.json').read_text())
for a in assets:
 source=base/'source'/(a['name']+'.png')
 with Image.open(source) as raw:
  w,h,x,y=a['slot']
  image=ImageOps.fit(raw.convert('RGBA'),(w*4,h*4),Image.Resampling.LANCZOS)
  canvas=Image.new('RGBA',(224,224));canvas.alpha_composite(image,(x*4,y*4))
  for density,factor in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
   dest=ROOT/f'notification/src/main/res/drawable-{density}'/('scene_'+a['name']+'.webp');dest.parent.mkdir(parents=True,exist_ok=True)
   canvas.resize((round(56*factor),round(56*factor)),Image.Resampling.LANCZOS).save(dest,'WEBP',lossless=True,exact=True,method=6)
 a.update(source=str(source.relative_to(ROOT)),sha256=hashlib.sha256(source.read_bytes()).hexdigest(),resource='scene_'+a['name'],dp=[56,56])
 # 仅缩放原图形成轻微呼吸；ProgressBar 在不可见时自行停止，无 app 定时 notify。
 frames=[]
 for index in range(48):
  inset=2.8*(1-math.cos(2*math.pi*index/48))/2
  frames.append(f'    <item android:duration="20"><inset android:inset="{inset:.3f}dp"><bitmap android:src="@drawable/scene_{a["name"]}" android:gravity="fill" /></inset></item>')
 drawable=ROOT/f'notification/src/main/res/drawable/scene_breathe_{a["name"]}.xml'
 drawable.write_text('<animation-list xmlns:android="http://schemas.android.com/apk/res/android" android:oneshot="false">\n'+'\n'.join(frames)+'\n</animation-list>\n')
 layout=ROOT/f'notification/src/main/res/layout/scene_icon_{a["name"]}.xml'
 layout.write_text(f'<ProgressBar xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:minWidth="0dp" android:minHeight="0dp" android:indeterminate="true" android:indeterminateTint="@null" android:indeterminateOnly="true" android:indeterminateDrawable="@drawable/scene_breathe_{a["name"]}" android:importantForAccessibility="no" />\n')
(base/'assets.json').write_text(json.dumps(assets,indent=2)+'\n')
