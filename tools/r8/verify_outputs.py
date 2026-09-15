#!/usr/bin/env python3
"""校验真实构建产物：双渠道 R8 装配、组件类名和库四变体 consumer 规则导出。"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    for module in ("notification", "metrics"):
        expected = (ROOT / module / "consumer-rules.pro").read_text().strip()
        for channel in ("local", "google"):
            for build_type in ("debug", "release"):
                aar = ROOT / module / f"build/outputs/aar/{module}-{channel}-{build_type}.aar"
                with zipfile.ZipFile(aar) as archive:
                    rules = archive.read("proguard.txt").decode().strip()
                require(expected in rules, f"Consumer rules absent/different: {aar}")
                print(f"PASS AAR consumer rules: {aar.relative_to(ROOT)}")
    component_names = []
    namespaces = {"app": "com.example.aicleanphonestorage", "notification": "io.docview.push", "metrics": "net.corekit.metrics"}
    for module, namespace in namespaces.items():
        manifest = ET.parse(ROOT / module / "src/main/AndroidManifest.xml")
        for node in manifest.iter():
            if node.tag in ("application", "activity", "service", "provider", "receiver"):
                name = node.get(ANDROID + "name", "")
                if name.startswith("."):
                    name = namespace + name
                if name.startswith(namespace + "."):
                    component_names.append(name)
    for variant in ("localRelease", "googleRelease"):
        output = ROOT / "app/build/outputs/mapping" / variant
        config = (output / "configuration.txt").read_text()
        for path in ("app/src/main/keepRules/rules.keep", "notification/consumer-rules.pro", "metrics/consumer-rules.pro"):
            # 配置中保留来源及规则正文，保证没有只创建文件而未接入 Gradle。
            require((ROOT / path).read_text().strip() in config, f"Rules not merged in {variant}: {path}")
        require("-keepnames class com.example.aicleanphonestorage.** extends android.app.Activity" in config,
                f"Activity name contract missing: {variant}")
        mappings = dict(re.findall(r"^(\S+) -> (\S+):$", (output / "mapping.txt").read_text(), re.M))
        for name in component_names + ["io.docview.push.worker.KeepAliveWorker"]:
            require(mappings.get(name) == name, f"Missing/renamed persistent component in {variant}: {name}")
        for model in ("io.docview.push.config.Config", "io.docview.push.config.NotificationConfig",
                      "io.docview.push.config.Content", "net.corekit.metrics.revenue.RevenueConfigItem"):
            require(model in mappings, f"Gson model removed in {variant}: {model}")
        # 自定义 View 的 XML 构造入口必须出现在 AAPT 规则里；Android SDK View 无需应用规则。
        aapt = ROOT / f"app/build/intermediates/aapt_proguard_file/{variant}/process{variant[0].upper() + variant[1:]}Resources/aapt_rules.txt"
        aapt_rules = aapt.read_text()
        views = set()
        for layout in (ROOT / "app/src/main/res").glob("layout*/*.xml"):
            for node in ET.parse(layout).iter():
                if node.tag.startswith("com.example.aicleanphonestorage."):
                    views.add(node.tag)
        for view in views:
            require(view in aapt_rules, f"AAPT constructor rule missing in {variant}: {view}")
        print(f"PASS {variant}: 3 rule sources, {len(component_names) + 1} components/Worker, 4 active DTOs, {len(views)} XML Views")


if __name__ == "__main__":
    main()
