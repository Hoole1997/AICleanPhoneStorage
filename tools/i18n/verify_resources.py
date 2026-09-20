"""Offline verification: locale coverage, Android format arguments and quantity fallback."""
from pathlib import Path
from xml.etree import ElementTree as ET
import json,re,argparse
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--prefix",default="",help="Only validate resource keys with this prefix")
args=parser.parse_args()
ROOT=Path(__file__).resolve().parents[2]
RES=ROOT/'app/src/main/res'
DIRECTORIES={'zh-CN':'b+zh+Hans','hi':'hi','es':'es','ar':'ar','pt':'pt','bn':'bn','ur':'ur','id':'in','ru':'ru','fr':'fr','de':'de','ja':'ja','ko':'ko','vi':'vi','tr':'tr'}
FORMAT=re.compile(r'%(?:\d+\$)?[-+0-9.]*[dsf]')
base={}
for path in (RES/'values').glob('*.xml'):
    for item in ET.parse(path).getroot():
        if item.get('translatable')!='false' and item.tag in ('string','plurals'):
            base[item.get('name')]=item
base={name:item for name,item in base.items() if name.startswith(args.prefix)}
errors=[]
for language,directory in DIRECTORIES.items():
    folder=RES/('values-'+directory)
    if not folder.exists():errors.append(f'Missing {directory}');continue
    translated={}
    # Android 合并 values 目录内全部 XML；按功能拆分的文案也必须验证覆盖和重复键。
    for path in folder.glob('*.xml'):
        for item in ET.parse(path).getroot():
            if item.tag not in ('string','plurals'):continue
            name=item.get('name')
            if name in translated:errors.append(f'{language}: duplicate {name}')
            translated[name]=item
    translated={name:item for name,item in translated.items() if name.startswith(args.prefix)}
    if set(base)!=set(translated):errors.append(f'{language}: keys differ {set(base)^set(translated)}')
    for name, original in base.items():
        if name not in translated:continue
        value=translated[name]
        pairs=[(original,value)] if original.tag=='string' else [(original.find(f"item[@quantity='{child.get('quantity')}']") if original.find(f"item[@quantity='{child.get('quantity')}']") is not None else original.find("item[@quantity='other']"),child) for child in value]
        if original.tag=='plurals' and value.find("item[@quantity='other']") is None:errors.append(f'{language}/{name}: missing other')
        for src,dst in pairs:
            expected=sorted(FORMAT.findall(src.text or ''));actual=sorted(FORMAT.findall(dst.text or ''))
            # Arabic can express zero/one/two in words instead of repeating a numeric counter.
            omission=language=='ar' and name in ('home_app_count','home_selected_apps') and dst.get('quantity') in ('zero','one','two') and not actual
            if expected!=actual and not omission: errors.append(f'{language}/{name}: {expected} != {actual}')
            if not (dst.text or '').strip():errors.append(f'{language}/{name}: empty translation')
            if re.search(r'\[\d{3}\]|9810\d\d',dst.text or ''):errors.append(f'{language}/{name}: translation token leaked')
if errors:raise SystemExit('\n'.join(errors))
print(f'Validated {len(base)} resource keys in {len(DIRECTORIES)} localized resource sets; English is the default.')
