"""Offline verification: locale coverage, Android format arguments and quantity fallback."""
from pathlib import Path
from xml.etree import ElementTree as ET
import json,re
ROOT=Path(__file__).resolve().parents[2]
RES=ROOT/'app/src/main/res'
DIRECTORIES={'zh-CN':'b+zh+Hans','hi':'hi','es':'es','ar':'ar','pt':'pt','bn':'bn','ur':'ur','id':'in','ru':'ru','fr':'fr','de':'de','ja':'ja','ko':'ko','vi':'vi','tr':'tr'}
FORMAT=re.compile(r'%(?:\d+\$)?[-+0-9.]*[dsf]')
base={}
for path in (RES/'values').glob('*.xml'):
    for item in ET.parse(path).getroot():
        if item.get('translatable')!='false' and item.tag in ('string','plurals'):
            base[item.get('name')]=item
errors=[]
for language,directory in DIRECTORIES.items():
    path=RES/('values-'+directory)/'strings.xml'
    if not path.exists():errors.append(f'Missing {directory}');continue
    translated={item.get('name'):item for item in ET.parse(path).getroot()}
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
