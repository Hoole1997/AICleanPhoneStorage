#!/usr/bin/env python3
"""逐文件生成 R8 静态审计清单；不读取 build、私有本机配置或忽略文件。

它是入口排查清单，不是 Kotlin 语义分析器。命中项须结合源码调用链、依赖 consumer
规则及混淆后测试判断；“无命中”不等价于运行时已验证。二进制只核对类型/大小。
"""
from collections import Counter
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PATTERNS = {
    "Gson 模型/转换": r'\b(?:Gson|SerializedName|TypeToken|fromJson|toJson)\b',
    "反射/动态加载": r'Class\s*\.\s*forName|loadClass\s*\(|getDeclared\w*\s*\(|getMethod\s*\(|getField\s*\(|ServiceLoader|java\.lang\.reflect|kotlin\.reflect',
    "类名依赖": r'\.javaClass\s*\.\s*(?:name|simpleName)|\.qualifiedName|\.canonicalName|property\.name',
    "JNI/JS/序列化": r'external\s+fun|System\.load|JavascriptInterface|addJavascriptInterface|\b(?:Parcelable|Parcelize|Serializable|ObjectInputStream|ObjectOutputStream)\b',
    "框架恢复/工厂": r'\b(?:\w*Fragment|\w*ViewModel|ViewModelProvider|SavedStateHandle|CoroutineWorker|ListenableWorker|WorkRequestBuilder)\b',
    "资源/动画": r'getIdentifier\s*\(|ObjectAnimator|PropertyValuesHolder|RemoteViews',
    "显式保留注解": r'@Keep\b|@Subscribe\b',
}
COMPILED = {label: re.compile(pattern) for label, pattern in PATTERNS.items()}
TEXT = {".kt", ".java", ".xml", ".json", ".properties", ".gradle", ".kts", ".pro", ".keep", ".txt", ".md", ".example"}
ANDROID = "{http://schemas.android.com/apk/res/android}"


def inspect(path):
    if path.suffix not in TEXT:
        return "二进制资源", "按类型/大小核对；无 JVM 类或成员规则"
    content = path.read_text(encoding="utf-8")
    if path.suffix in {".kt", ".java"}:
        hits = []
        for label, pattern in COMPILED.items():
            lines = [str(i) for i, line in enumerate(content.splitlines(), 1) if pattern.search(line)]
            if lines:
                hits.append(label + " L" + ",".join(lines))
        stage = "生产源码" if "/main/" in str(path) else "测试源码"
        result = "；".join(hits) if hits else "静态入口扫描无上述特殊边界；静态调用由 R8 追踪"
        return stage, result
    if path.suffix == ".xml":
        tree = ET.fromstring(content)
        boundaries = set()
        for node in tree.iter():
            if "." in node.tag and not node.tag.startswith("{"):
                boundaries.add("XML View " + node.tag)
            if node.tag in {"application", "activity", "activity-alias", "service", "provider", "receiver"}:
                name = node.get(ANDROID + "name")
                if name:
                    boundaries.add("Manifest " + name)
            for key in ("class", ANDROID + "onClick", ANDROID + "propertyName"):
                if key in node.attrib:
                    boundaries.add(key.replace(ANDROID, "android:") + "=" + node.attrib[key])
        return "XML", "；".join(sorted(boundaries)) or "已解析 XML；无自定义类/反射属性入口"
    if path.suffix == ".json" or path.name.endswith(".json.example"):
        import json
        json.loads(content)
        return "JSON", "已解析 JSON；配置 DTO 见审计正文，Firebase/Lottie 交由 SDK 规则"
    return "构建/配置", "完整文本扫描；规则装配/静态配置见审计正文"


def main():
    result = subprocess.check_output(
        ["rg", "--files", "app", "notification", "metrics", "-g", "!**/build/**"], cwd=ROOT, text=True)
    paths = sorted(Path(value) for value in result.splitlines())
    rows = []
    totals = Counter()
    for relative in paths:
        path = ROOT / relative
        kind, result = inspect(path)
        totals[(relative.parts[0], kind)] += 1
        rows.append((relative, path.stat().st_size, kind, result))
    report = ["# R8 逐文件静态扫描清单", "", "由 `python3 tools/r8/audit_files.py` 生成。",
              "", "范围：三个模块中 rg 可见的所有源码、测试、XML、配置及资源；排除 build、Git 忽略文件和本机凭据。",
              "文本文件完整读取，XML/JSON 逐个解析；二进制仅核对类型与大小。命中行包括 import/注释，须结合 [审计结论](r8-audit.md) 判断。",
              "这份自动清单不代表逐行人工语义审查，也不替代设备验证。", "", f"共 {len(paths)} 个文件。", "",
              "| 模块 | 类型 | 数量 |", "| --- | --- | ---: |"]
    report += [f"| {module} | {kind} | {count} |" for (module, kind), count in sorted(totals.items())]
    for module in ("app", "notification", "metrics"):
        report += ["", f"## {module}", "", "| 文件 | 字节数 | 类型 | 检查项 |", "| --- | ---: | --- | --- |"]
        report += [f"| `{path}` | {size} | {kind} | {result.replace('|', '/')} |"
                   for path, size, kind, result in rows if path.parts[0] == module]
    output = ROOT / "docs/r8-file-inventory.md"
    output.write_text("\n".join(report) + "\n")
    print(f"Scanned {len(paths)} files; report: {output}")
    for (module, kind), count in sorted(totals.items()):
        print(module, kind, count)


if __name__ == "__main__":
    main()
