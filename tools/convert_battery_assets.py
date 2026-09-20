"""Convert Figma originals to bounded WebP and extract original gauge path geometry.
Pillow required. Crops/rotation reproduce the image-fill transforms in Figma, not approximate icons.
"""
from pathlib import Path
from PIL import Image, ImageOps
import xml.etree.ElementTree as ET
import hashlib, json
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'design/figma/battery/source'
RES=ROOT/'app/src/main/res'
records=[]
# Normalized crops from get_design_context image-fill transforms.
crops={'brightness':(.4737/1.9474,.0548/1.1095,1/1.9474,1/1.1095),
       'temperature':(2.6875/6.375,.7257/3.6323,1/6.375,1/3.6323)}
nodes={'home':'6194:1737','brightness':'6192:1266','temperature':'6192:1273','voltage':'6192:1281','type':'6192:1287','capacity':'6192:1294','health_good':'6192:1300','health_bad':'6192:1382'}
for name,node in nodes.items():
    source=SOURCE/f'{name}_raw.png'
    original=Image.open(source).convert('RGBA')
    if name in crops:
        x,y,w,h=crops[name]
        original=original.crop((round(x*original.width),round(y*original.height),round((x+w)*original.width),round((y+h)*original.height)))
    elif name=='home':
        original=original.crop((original.width*7/32,original.height/16,original.width*25/32,original.height*15/16))
    for density,scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
        size=round(32*scale)
        canvas=Image.new('RGBA',(size,size))
        if name=='home':
            icon=original.resize((round(20.124*scale),round(31.303*scale)),Image.Resampling.LANCZOS).rotate(-27.09,Image.Resampling.BICUBIC,expand=True)
            canvas.alpha_composite(icon,(round(15.83*scale-icon.width/2),round(15.52*scale-icon.height/2)))
        else:
            icon=ImageOps.contain(original,(size,size),Image.Resampling.LANCZOS)
            canvas.alpha_composite(icon,((size-icon.width)//2,(size-icon.height)//2))
        resource='ic_tool_battery' if name=='home' else f'battery_{name}'
        canvas.save(RES/f'drawable-{density}/{resource}.webp','WEBP',lossless=True,exact=True)
    records.append({'resource':resource,'node':node,'source':str(source.relative_to(ROOT)),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'dp':[32,32]})
with Image.open(SOURCE/'inner_ring.png') as image:
    # Figma's node export is flattened on #F6F6F6. Recover original #0D60F1
    # stroke coverage (including its 30% opacity), preserving exported dash geometry.
    transparent = Image.new('RGBA', image.size)
    transparent.putdata([(13, 96, 241, max(0, min(255, round((246-r)*255/233))))
                         for r, g, b, a in image.convert('RGBA').getdata()])
    image = transparent
    for density,scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
        image.convert('RGBA').resize((round(146*scale),round(129*scale)),Image.Resampling.LANCZOS).save(RES/f'drawable-{density}/battery_inner_ring.webp','WEBP',lossless=True,exact=True)
ns={'s':'http://www.w3.org/2000/svg'}
arc=ET.parse(SOURCE/'outer_arc.svg').getroot().find(".//s:path[@fill='#0D60F1']",ns).get('d')
gauge=ET.parse(SOURCE/'gauge_shape.svg').getroot()
outline=gauge.find(".//s:path[@id='Vector_2']",ns).get('d')
bolt=gauge.find(".//s:path[@id='Vector_3']",ns).get('d')
root=ET.Element('resources')
root.append(ET.Comment('Exact paths exported from Figma nodes 6192:1255 / 6192:1258. Parsed once by the custom View.'))
for name,data in [('arc',arc),('outline',outline),('bolt',bolt)]:
    ET.SubElement(root,'string',{'name':f'battery_gauge_{name}_path','translatable':'false'}).text=data
ET.indent(root)
ET.ElementTree(root).write(RES/'values/battery_gauge_paths.xml',encoding='utf-8',xml_declaration=True)
(ROOT/'design/figma/battery/assets.json').write_text(json.dumps({'file_key':'dVsTL6ggoDPXVPcEKg856G','assets':records,'gauge':{'outer':'6192:1255','inner':'6192:1257','body':'6192:1258','view_box':[187,187]},'image_fill_crops':crops},indent=2)+'\n')
