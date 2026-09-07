"""从已归档的 Google Fonts Roboto 生成静态字体，使 API 24 与 OEM 字体替换下仍使用设计字重。

仅开发时运行；保留 Latin/Latin Extended 和常用标点，其余文字由 Android 字体回退处理。
原字体和 OFL 许可位于 design/figma/home/fonts；运行时不联网下载或转换字体。
"""
from pathlib import Path
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/figma/home/fonts'
OUTPUT = ROOT / 'app/src/main/res/font'
OUTPUT.mkdir(parents=True, exist_ok=True)
for name, weight in [('regular', 400), ('medium', 500), ('semibold', 600), ('bold', 700), ('black', 900)]:
    font = TTFont(SOURCE / 'Roboto[wdth,wght].ttf')
    font = instantiateVariableFont(font, {'wght': weight, 'wdth': 100}, inplace=True)
    options = subset.Options()
    options.name_IDs = ['*']
    options.name_languages = ['*']
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=list(range(0x0000, 0x0250)) + list(range(0x2000, 0x2070)))
    subsetter.subset(font)
    font.save(OUTPUT / f'home_roboto_{name}.ttf')
license_folder = ROOT / 'app/src/main/assets/licenses'
license_folder.mkdir(parents=True, exist_ok=True)
(license_folder / 'Roboto-OFL.txt').write_bytes((SOURCE / 'OFL.txt').read_bytes())
