#!/usr/bin/env python3
"""校验指定渠道的真实 R8 产物；默认仅 local，google 产物由 GitHub Actions 构建。"""
import argparse
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--channels", nargs="+", choices=("local", "google"), default=["local"])
    args = parser.parse_args()
    for module in ("notification", "metrics"):
        expected = (ROOT / module / "consumer-rules.pro").read_text().strip()
        for channel in args.channels:
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
    rule_paths = [str(p.relative_to(ROOT)) for p in sorted((ROOT / "app/src/main/keepRules").glob("*.keep"))]
    rule_paths += ["notification/consumer-rules.pro", "metrics/consumer-rules.pro"]
    for variant in (channel + "Release" for channel in args.channels):
        output = ROOT / "app/build/outputs/mapping" / variant
        config = (output / "configuration.txt").read_text()
        for path in rule_paths:
            # 配置中保留来源及规则正文，保证没有只创建文件而未接入 Gradle。
            require((ROOT / path).read_text().strip() in config, f"Rules not merged in {variant}: {path}")
        require("-keepnames class com.example.aicleanphonestorage.** extends android.app.Activity" in config,
                f"Activity name contract missing: {variant}")
        mappings = dict(re.findall(r"^(\S+) -> (\S+):$", (output / "mapping.txt").read_text(), re.M))
        for name in component_names + ["io.docview.push.worker.KeepAliveWorker"]:
            require(mappings.get(name) == name, f"Missing/renamed persistent component in {variant}: {name}")
        for model in ("io.docview.push.config.Config", "io.docview.push.config.NotificationConfig",
                      "io.docview.push.config.Content", "io.docview.push.config.ContentTranslation",
                      "net.corekit.metrics.revenue.RevenueConfigItem"):
            require(model in mappings, f"Gson model removed in {variant}: {model}")
        # Trustlook AAR 没有自带 consumer rules，项目中的 SDK 专用规则必须实际生效。
        with zipfile.ZipFile(ROOT / "app/libs/cloudscan_sdk_5.0.18.20250821.aar") as aar:
            import io
            with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as sdk:
                sdk_classes = [name[:-6].replace("/", ".") for name in sdk.namelist()
                               if name.startswith("com/trustlook/") and name.endswith(".class")]
        for name in sdk_classes:
            require(mappings.get(name) == name, f"Trustlook reflection class removed/renamed: {name}")
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
        print(f"PASS {variant}: {len(rule_paths)} rule sources, {len(component_names) + 1} components/Worker, 5 active DTOs, {len(sdk_classes)} Trustlook classes, {len(views)} XML Views")


if __name__ == "__main__":
    main()
