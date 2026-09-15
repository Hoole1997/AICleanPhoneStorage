#!/usr/bin/env python3
"""用项目编译的模型运行独立 R8 full-mode 回归，不连接设备/网络或初始化 SDK。

先执行 :notification:assembleLocalRelease :metrics:assembleLocalRelease。
JAVA_HOME 指向 JDK；依赖必须已在 GRADLE_USER_HOME 的缓存中解析。
产物只写 build/r8-model-probe。Gson 作为外部库，故测试不依赖它自带的 keep。
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"


def artifact(group, name, version):
    matches = list((CACHE / group / name / version).glob(f"*/{name}-{version}.jar"))
    if len(matches) != 1:
        raise SystemExit(f"Expected one cached {group}:{name}:{version}, found {len(matches)}")
    return matches[0]


def run(*args):
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gson", default="2.13.2", help="实际要验证的缓存 Gson 版本")
    args = parser.parse_args()
    java_home = Path(os.environ["JAVA_HOME"])
    versions = (ROOT / "gradle/libs.versions.toml").read_text()
    agp = re.search(r'^agp = "([^"]+)"', versions, re.M)[1]
    r8 = artifact("com.android.tools.build", "builder", agp)
    gson = artifact("com.google.code.gson", "gson", args.gson)
    # AGP 内置 Kotlin 编译器版本由其 POM 给出，避免依赖本机“最新”stdlib。
    pom = next((CACHE / "com.android.tools.build" / "gradle" / agp).glob("*/*.pom"))
    import xml.etree.ElementTree as ET
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    deps = ET.parse(pom).findall(".//m:dependency", ns)
    kotlin_version = next(d.find("m:version", ns).text for d in deps
                          if d.find("m:artifactId", ns).text == "kotlin-stdlib")
    kotlin = artifact("org.jetbrains.kotlin", "kotlin-stdlib", kotlin_version)
    out = ROOT / "build/r8-model-probe" / args.gson
    classes = out / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    # 只选真实模型及其纯 Kotlin 解析依赖，不把 Android/广告/通知服务带入 JVM 宿主。
    patterns = {
        "notification": re.compile(r"io/docview/push/(NotificationDestination(?:\$.*)?|config/(Config|ConfigKt|NotificationConfig|Content(?:\$.*)?|ContentKt(?:\$.*)?)|earthquake/(EarthquakeResponse|Metadata|EarthquakeFeature|EarthquakeProperties|EarthquakeGeometry|EarthquakeInfo))\.class$"),
        "metrics": re.compile(r"net/corekit/metrics/revenue/RevenueConfigItem\.class$"),
    }
    models = out / "models.jar"
    with zipfile.ZipFile(models, "w") as target:
        for module, pattern in patterns.items():
            source = ROOT / module / "build/intermediates/runtime_library_classes_jar/localRelease/bundleLibRuntimeToJarLocalRelease/classes.jar"
            with zipfile.ZipFile(source) as jar:
                selected = [name for name in jar.namelist() if pattern.fullmatch(name)]
                if not selected:
                    raise SystemExit(f"No compiled models in {source}")
                for name in selected:
                    target.writestr(name, jar.read(name))
    run(java_home / "bin/javac", "--release", "17", "-cp", os.pathsep.join(map(str, [models, gson, kotlin])),
        "-d", classes, ROOT / "tools/r8/ModelProbe.java")
    probe = out / "probe.jar"
    with zipfile.ZipFile(probe, "w") as jar:
        for path in classes.rglob("*.class"):
            jar.write(path, path.relative_to(classes))
    rules = out / "probe.pro"
    rules.write_text("-keep class ModelProbe { public static void main(java.lang.String[]); }\n"
                     "-dontwarn org.jetbrains.annotations.**\n")
    optimized = out / "optimized.jar"
    run(java_home / "bin/java", "-cp", r8, "com.android.tools.r8.R8", "--release", "--classfile",
        "--lib", java_home, "--lib", gson, "--lib", kotlin,
        "--pg-conf", ROOT / "notification/consumer-rules.pro",
        "--pg-conf", ROOT / "metrics/consumer-rules.pro", "--pg-conf", rules,
        "--pg-map-output", out / "mapping.txt", "--output", optimized, models, probe)
    run(java_home / "bin/java", "-cp", os.pathsep.join(map(str, [optimized, gson, kotlin])), "ModelProbe", ROOT)
    mapping = (out / "mapping.txt").read_text()
    renamed = re.findall(r"^((?:io\.docview\.push|net\.corekit\.metrics)\S+) -> (\S+):$", mapping, re.M)
    if not any(before != after for before, after in renamed):
        raise SystemExit("Probe did not exercise obfuscated models")
    print(f"Verified model obfuscation with Gson {args.gson}; artifacts: {out}")


if __name__ == "__main__":
    main()
