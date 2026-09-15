#!/usr/bin/env python3
"""Actions 中生成/复用签名、校验渠道与收集产物。只归档白名单文件，绝不归档私钥。"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def google_config():
    # 渠道文件中的版本/包名均为单行值，与当前项目配置约定一致。
    result = {}
    for line in (ROOT / "app/src/google/config.properties").read_text().splitlines():
        if not line.lstrip().startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def validate_channel():
    config = google_config()
    services = json.loads((ROOT / "app/src/google/google-services.json").read_text())
    packages = {c.get("client_info", {}).get("android_client_info", {}).get("package_name")
                for c in services.get("client", [])}
    if config["applicationId"] not in packages:
        raise RuntimeError("google/config.properties 的 applicationId 与 google-services.json 不匹配。")
    if not re.fullmatch(r"[A-Za-z0-9._-]+", config["versionName"]) or not config["versionCode"].isdigit():
        raise RuntimeError("Google 渠道版本配置无效。")
    print("Google 渠道 Firebase 包名与版本检查通过。")


def check_packages():
    user = os.environ.get("GH_PACKAGES_USER", "")
    token = os.environ.get("GH_PACKAGES_TOKEN", "")
    if not user or not token:
        raise RuntimeError("缺少 GitHub Packages 凭据，请运行 github_aab.py configure-packages。")
    auth = base64.b64encode(f"{user}:{token}".encode()).decode()
    # 对应当前实际依赖，不能沿用 StreetHtml 旧脚本里的 core 1.0.11。
    for module, version in (("core", "1.0.15"), ("bill", "1.0.51")):
        url = f"https://maven.pkg.github.com/toukaRemax/remax_sdk/com/github/toukaremax/{module}/{version}/{module}-{version}.pom"
        request = urllib.request.Request(url, headers={"Authorization": f"Basic {auth}"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                if response.status != 200:
                    raise RuntimeError("GitHub Packages 未返回成功状态。")
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"GitHub Packages {module} 访问失败：HTTP {error.code}。") from None
    print("GitHub Packages 依赖访问检查通过。")


KEYSTORE_PATH = "app/src/google/google-release.keystore"


def git(*args, cwd=ROOT):
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True).stdout


def publish_keystore(path):
    # 只提交签名文件到 main，不把旧 ref 的源码覆盖到 main；并发源码推送时有限重试。
    for attempt in range(3):
        git("fetch", "origin", "main")
        files = git("ls-tree", "--name-only", "origin/main", "--", KEYSTORE_PATH).decode().strip()
        if files:
            raise RuntimeError("生成期间 main 出现了其他签名，停止覆盖，请重新运行以复用 main 的签名。")
        with tempfile.TemporaryDirectory(prefix="google-signing-commit-") as temp:
            checkout = Path(temp) / "main"
            git("worktree", "add", "--detach", str(checkout), "origin/main")
            try:
                target = checkout / KEYSTORE_PATH
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)
                git("add", "--", KEYSTORE_PATH, cwd=checkout)
                git("-c", "user.name=github-actions[bot]", "-c",
                    "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                    "commit", "-m", "chore: generate google release keystore in GitHub Actions", cwd=checkout)
                push = subprocess.run(["git", "push", "origin", "HEAD:main"], cwd=checkout, capture_output=True)
                if push.returncode == 0:
                    return git("rev-parse", "HEAD", cwd=checkout).decode().strip()
                if attempt == 2:
                    raise RuntimeError("签名提交到 main 失败，停止构建；检查仓库写权限和分支保护。")
            finally:
                git("worktree", "remove", "--force", str(checkout))
    raise RuntimeError("无法保存云端签名。")


def ensure_signing():
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError("签名文件只允许在 GitHub Actions 中生成。")
    password = os.environ.get("ANDROID_SIGNING_STORE_PASSWORD", "")
    if not password or any(c in password for c in "\r\n"):
        raise RuntimeError("缺少有效签名密码，请运行 github_aab.py configure-signing。")
    alias = "google"
    path = ROOT / KEYSTORE_PATH
    path.parent.mkdir(parents=True, exist_ok=True)
    git("fetch", "origin", "main")
    files = git("ls-tree", "--name-only", "origin/main", "--", KEYSTORE_PATH).decode().strip()
    if files:
        # main 是唯一持久签名来源，旧分支再次构建也复用它，不重新生成。
        path.write_bytes(git("show", f"origin/main:{KEYSTORE_PATH}"))
        revision = git("rev-parse", "origin/main").decode().strip()
    else:
        if path.exists():
            raise RuntimeError("当前 ref 含 main 未记录的签名，拒绝用它覆盖 main。")
        result = subprocess.run(["keytool", "-genkeypair", "-storetype", "PKCS12", "-keystore", str(path),
                                 "-storepass:env", "ANDROID_SIGNING_STORE_PASSWORD",
                                 "-keypass:env", "ANDROID_SIGNING_STORE_PASSWORD",
                                 "-alias", alias, "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
                                 "-dname", "CN=AI Clean Upload, OU=Mobile, O=AI Clean, C=US"],
                                capture_output=True, check=False)
        if result.returncode:
            raise RuntimeError("云端 keytool 生成签名失败。")
        revision = publish_keystore(path)
    path.chmod(0o600)
    result = subprocess.run(["keytool", "-exportcert", "-keystore", str(path), "-alias", alias,
                             "-storepass:env", "ANDROID_SIGNING_STORE_PASSWORD"], capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError("无法读取 main 的签名，请检查原签名密码；不会重新生成或换签。")
    values = {
        "ANDROID_SIGNING_STORE_FILE": str(path),
        "ANDROID_SIGNING_KEY_ALIAS": alias,
        "ANDROID_SIGNING_KEY_PASSWORD": password,
        "ANDROID_SIGNING_CERT_SHA256": hashlib.sha256(result.stdout).hexdigest(),
        "ANDROID_SIGNING_COMMIT": revision,
    }
    with Path(os.environ["GITHUB_ENV"]).open("a") as output:
        for key, value in values.items():
            output.write(f"{key}={value}\n")
    print(f"云端签名已保存到 main 并验证：{revision}")


def collect(aab_name):
    if not re.fullmatch(r"[A-Za-z0-9._-]+\.aab", aab_name):
        raise RuntimeError("非法 AAB 文件名。")
    sources = list((ROOT / "app/build/outputs/bundle/googleRelease").glob("*.aab"))
    if len(sources) != 1:
        raise RuntimeError(f"应有一个 Google Release AAB，实际找到 {len(sources)} 个。")
    source = sources[0]
    verification = subprocess.run(["jarsigner", "-J-Duser.language=en", "-verify", str(source)],
                                  capture_output=True, text=True, check=False)
    if verification.returncode or "jar verified." not in verification.stdout:
        raise RuntimeError("AAB 签名验证失败，拒绝上传产物。")
    cert = subprocess.run(["keytool", "-printcert", "-jarfile", str(source), "-rfc"],
                          capture_output=True, text=True, check=True).stdout
    certificates = re.findall(r"-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----", cert, re.S)
    if not certificates or hashlib.sha256(base64.b64decode(certificates[0])).hexdigest() != os.environ["ANDROID_SIGNING_CERT_SHA256"]:
        raise RuntimeError("AAB 的签名证书与配置的上传证书不一致。")
    destination = ROOT / "build/google-aab"
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination / aab_name)
    mapping = ROOT / "app/build/outputs/mapping/googleRelease/mapping.txt"
    if not mapping.is_file():
        raise RuntimeError("缺少 R8 mapping.txt，请检查混淆构建是否完整。")
    shutil.copyfile(mapping, destination / "mapping.txt")
    (destination / "upload-certificate.pem").write_text("-----BEGIN CERTIFICATE-----\n" + certificates[0] + "\n-----END CERTIFICATE-----\n")
    config = google_config()
    info = {key: config[key] for key in ("applicationId", "versionName", "versionCode")}
    info.update(commit=os.environ["GITHUB_SHA"], run_id=os.environ["GITHUB_RUN_ID"],
                certificate_sha256=os.environ["ANDROID_SIGNING_CERT_SHA256"],
                signing_commit=os.environ["ANDROID_SIGNING_COMMIT"], aab=aab_name)
    (destination / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
    files = [destination / name for name in (aab_name, "mapping.txt", "upload-certificate.pem", "build-info.json")]
    # 流式计算，避免将整个 AAB 读入内存。
    with (destination / "SHA256SUMS").open("w") as output:
        for file in files:
            digest = hashlib.sha256()
            with file.open("rb") as source_file:
                for chunk in iter(lambda: source_file.read(1024 * 1024), b""):
                    digest.update(chunk)
            output.write(f"{digest.hexdigest()}  {file.name}\n")
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as output:
        output.write(f"### Google Release AAB\n\n- 文件：`{aab_name}`\n- 包名：`{config['applicationId']}`\n- 证书 SHA-256：`{info['certificate_sha256']}`\n")
    print(f"已验证并收集签名 AAB、mapping 和校验信息：{destination}")


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError("该脚本只允许在 GitHub Actions 执行，本机请使用 github_aab.py。")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "collect"))
    parser.add_argument("--aab-name")
    args = parser.parse_args()
    if args.action == "prepare":
        validate_channel()
        check_packages()
        ensure_signing()
    else:
        collect(args.aab_name or "")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
