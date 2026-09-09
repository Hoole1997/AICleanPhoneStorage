"""Development-only resource translation. Never imported or executed by the Android app.
Uses the public Google Translate text endpoint for an initial translation draft; committed
resources and overrides are the build inputs. No user files, telemetry or credentials are sent.
"""
from pathlib import Path
import concurrent.futures, hashlib, json, re, time, urllib.parse, urllib.request
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'app/src/main/res'
CACHE = ROOT / 'tools/i18n/cache'
LOCALES = {'zh-CN':'b+zh+Hans','hi':'hi','es':'es','ar':'ar','pt':'pt','bn':'bn','ur':'ur','id':'in','ru':'ru','fr':'fr','de':'de','ja':'ja','ko':'ko','vi':'vi','tr':'tr'}
PLACEHOLDER = re.compile(r'%(?:\d+\$)?[-+0-9.]*[dsf]')

def sources():
    result = {}
    for path in sorted((RES/'values').glob('*.xml')):
        for element in ET.parse(path).getroot():
            if element.get('translatable') == 'false': continue
            if element.tag == 'string': result[element.get('name')] = element.text or ''
            elif element.tag == 'plurals':
                for item in element:
                    result[element.get('name')+'#'+item.get('quantity')] = item.text or ''
    return result

def _translate_batch(lang, entries):
    query = '\n'.join(f'[{i:03d}] {value}' for i, (_, value) in enumerate(entries))
    url = 'https://translate.googleapis.com/translate_a/single?' + urllib.parse.urlencode({'client':'gtx','sl':'en','tl':lang,'dt':'t','q':query})
    with urllib.request.urlopen(url, timeout=30) as response:
        raw = json.loads(response.read())
    text = ''.join(part[0] for part in raw[0] if part and part[0])
    pieces = list(re.finditer(r'[\[［【]\s*(\d{1,3})\s*[\]］】]', text))
    if len(pieces) != len(entries): raise ValueError(f'{lang}: translation markers changed: {text[:160]!r}')
    result = {}
    for index, marker in enumerate(pieces):
        if int(marker.group(1)) != index: raise ValueError('Translation order changed')
        end = pieces[index+1].start() if index+1 < len(pieces) else len(text)
        translated = text[marker.end():end].strip()
        translated = re.sub(r'%\s*(\d+)\s*\$\s*([dsf])', r'%\1$\2', translated)
        key, original = entries[index]
        if sorted(PLACEHOLDER.findall(original)) != sorted(PLACEHOLDER.findall(translated)):
            raise ValueError(f'{lang}/{key}: format placeholders changed: {translated!r}')
        result[key] = translated
    return result

def translate_one(lang, entry):
    key, text = entry
    replacements = {}
    def mask(match):
        token = str(981000 + len(replacements))
        replacements[token] = match.group()
        return token
    query = PLACEHOLDER.sub(mask, text)
    url = 'https://translate.googleapis.com/translate_a/single?' + urllib.parse.urlencode({'client':'gtx','sl':'en','tl':lang,'dt':'t','q':query})
    with urllib.request.urlopen(url, timeout=30) as response: raw = json.loads(response.read())
    translated = ''.join(part[0] for part in raw[0] if part and part[0])
    translated = re.sub(r'\d+', lambda m: replacements.get(str(int(m.group())), m.group()), translated)
    if sorted(PLACEHOLDER.findall(text)) != sorted(PLACEHOLDER.findall(translated)):
        raise ValueError(f'{lang}/{key}: standalone placeholders changed')
    return {key:translated}

def translate_batch(lang, entries):
    try: return _translate_batch(lang, entries)
    except ValueError:
        # Some languages move markers across sentence boundaries. Split only those groups;
        # a standalone string masks placeholders to keep Android format contracts intact.
        if len(entries) == 1: return translate_one(lang, entries[0])
        middle = len(entries)//2
        return translate_batch(lang, entries[:middle]) | translate_batch(lang, entries[middle:])

def android_escape(value):
    return value.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')

def write_resources(lang, values, originals):
    ET.register_namespace('tools','http://schemas.android.com/tools')
    root = ET.Element('resources')
    groups = {}
    for key, original in originals.items():
        value = values[key]
        if '#' in key:
            name, quantity = key.split('#')
            if name not in groups: groups[name] = ET.SubElement(root, 'plurals', name=name)
            element = ET.SubElement(groups[name], 'item', quantity=quantity)
        else: element = ET.SubElement(root, 'string', name=key)
        element.text = android_escape(value)
    quantities = ({'zero','one','two','few','many','other'} if lang=='ar' else
                  {'one','few','many','other'} if lang=='ru' else
                  {'one','many','other'} if lang in ('fr','es','pt') else
                  {'other'} if lang in ('zh-CN','ja','ko','vi','id') else {'one','other'})
    for name, group in groups.items():
        other = values[name+'#other']
        group.clear(); group.set('name',name)
        # Completion count is already rendered in a separate large number view.
        if name in ('completion_files','completion_photos','completion_apps'):
            group.set('{http://schemas.android.com/tools}ignore','ImpliedQuantity')
        for quantity in ('zero','one','two','few','many','other'):
            if quantity in quantities:
                ET.SubElement(group,'item',quantity=quantity).text = android_escape(values.get(name+'#'+quantity,other))
    ET.indent(root, space='    ')
    directory = RES/('values-'+LOCALES[lang]); directory.mkdir(exist_ok=True)
    ET.ElementTree(root).write(directory/'strings.xml', encoding='utf-8', xml_declaration=True)

def generate(lang, originals):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE/(lang+'.json')
    cached = json.loads(path.read_text()) if path.exists() else {}
    values = {}
    pending = []
    hints = json.loads((ROOT/'tools/i18n/translation_hints.json').read_text())
    for key, raw in originals.items():
        raw = hints.get(key, raw)
        # Convert Android escapes to text before translation, then restore when serializing XML.
        text = raw.replace('\\n','\n').replace("\\'", "'").replace('\\"','"')
        if cached.get(key, {}).get('source') == text:
            values[key] = cached[key]['translation']; continue
        if not re.search('[A-Za-z]', PLACEHOLDER.sub('', text)):
            values[key] = text
        else: pending.append((key, text))
    batches, batch, length = [], [], 0
    for row in pending:
        if batch and length + len(row[1]) > 1400:
            batches.append(batch); batch=[]; length=0
        batch.append(row); length += len(row[1]) + 8
    if batch: batches.append(batch)
    for batch in batches:
        translated = translate_batch(lang, batch)
        values.update(translated)
        for key, source in batch: cached[key] = {'source':source, 'translation':translated[key]}
        path.write_text(json.dumps(cached, ensure_ascii=False, indent=2)+'\n')
        time.sleep(0.4)
    override_path = ROOT/'tools/i18n/overrides.json'
    overrides = json.loads(override_path.read_text()).get(lang,{}) if override_path.exists() else {}
    values.update(overrides)
    write_resources(lang, values, originals)
    print(f'{lang}: {len(values)} localized entries', flush=True)

if __name__ == '__main__':
    originals = sources()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = {pool.submit(generate, lang, originals):lang for lang in LOCALES}
        for future in concurrent.futures.as_completed(futures):
            try: future.result()
            except Exception as error: print(f'FAILED {futures[future]}: {error}', flush=True)
