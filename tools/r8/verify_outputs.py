#!/usr/bin/env python3
"""校验指定渠道的真实 R8 产物；默认仅 local，google 产物由 GitHub Actions 构建。"""
import argparse
import io
import json
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def dex_defined_classes(apk):
    """从 APK 根 DEX 的 class_defs 读取定义，不把类型引用/加固资产误当成已保留类。

    原名保留的 native-only 类可能没有 mapping 条目，必须核对实际 DEX。
    """
    classes = set()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not re.fullmatch(r"classes(?:\d+)?\.dex", name):
                continue
            dex = archive.read(name)
            require(dex.startswith(b"dex\n"), f"Unsupported DEX header: {name}")
            def uint(offset):
                return struct.unpack_from("<I", dex, offset)[0]
            strings, types = uint(60), uint(68)
            count, definitions = uint(96), uint(100)
            for n in range(count):
                type_index = uint(definitions + n * 32)
                string_index = uint(types + type_index * 4)
                offset = uint(strings + string_index * 4)
                # string_data_item 以 ULEB128 UTF-16 长度开头，类描述符随后以 NUL 结束。
                while dex[offset] & 0x80:
                    offset += 1
                offset += 1
                descriptor = dex[offset:dex.index(b"\0", offset)].decode("utf-8", errors="replace")
                if descriptor.startswith("L") and descriptor.endswith(";"):
                    classes.add(descriptor[1:-1].replace("/", "."))
    require(classes, f"No DEX class definitions in {apk}")
    return classes


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
        # local 是否使用加固 SDK 由渠道配置决定；google 始终使用，仍只读取 CI 产物。
        channel = variant.removesuffix("Release")
        channel_config = (ROOT / f"app/src/{channel}/config.properties").read_text()
        risk_enabled = channel == "google" or bool(re.search(r"^xmRiskEnabled\s*=\s*true\s*$", channel_config, re.M))
        variant_rule_paths = rule_paths + (["app/src/xmRiskSdk/keepRules/vendor-risk.keep"] if risk_enabled else [])
        for path in variant_rule_paths:
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
            with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as sdk:
                sdk_classes = [name[:-6].replace("/", ".") for name in sdk.namelist()
                               if name.startswith("com/trustlook/") and name.endswith(".class")]
        for name in sdk_classes:
            require(mappings.get(name) == name, f"Trustlook reflection class removed/renamed: {name}")
        apk_folder = ROOT / f"app/build/outputs/apk/{channel}/release"
        metadata = json.loads((apk_folder / "output-metadata.json").read_text())
        require(len(metadata["elements"]) == 1, f"Expected universal release APK: {variant}")
        apk = apk_folder / metadata["elements"][0]["outputFile"]
        with zipfile.ZipFile(apk) as archive:
            require("assets/alarm_wakeup_config.json" in archive.namelist(), f"Alarm default config missing: {variant}")
        defined_classes = dex_defined_classes(apk)
        vendor_count = 0
        if risk_enabled:
            # native/加固内部 DEX 不可静态追踪：核验这些入口确实在最终 mapping 中原名保留。
            for artifact in ("collect-1.3.2.aar", "xm-0.0.5.aar"):
                with zipfile.ZipFile(ROOT / "app/libs" / artifact) as aar:
                    with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as sdk:
                        vendor_classes = [name[:-6].replace("/", ".") for name in sdk.namelist()
                                          if name.endswith(".class") and name.startswith((
                                              "com/ai/clean/phone/storage/", "com/jiagu/sdk/", "com/xmsupport/base/components/"))]
                for name in vendor_classes:
                    require(name in defined_classes and mappings.get(name, name) == name,
                            f"Vendor native/reflection class removed/renamed in {variant}: {name}")
                vendor_count += len(vendor_classes)
        require("com.example.aicleanphonestorage.feature.speedtest.ui.SpeedTestFailureDialog" in mappings,
                f"Speed test Fragment missing: {variant}")
        seeds = (output / "seeds.txt").read_text()
        require("SpeedTestFailureDialog()" in seeds, f"Speed test Fragment restoration constructor missing: {variant}")
        for debug_class in ("AdDebugActivity", "AdDebugPreviewActivity", "addebug.AdDebugViewModel"):
            name = "com.example.aicleanphonestorage.feature.settings." + debug_class
            require(name not in mappings and name not in defined_classes, f"Debug-only ad tool leaked into {variant}: {name}")
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
        print(f"PASS {variant}: {len(variant_rule_paths)} rule sources, {len(component_names) + 1} components/Worker, 5 active DTOs, {len(sdk_classes)} Trustlook classes, {vendor_count} risk SDK classes, debug tools absent, Alarm asset, Fragment constructor, {len(views)} XML Views")


if __name__ == "__main__":
    main()
