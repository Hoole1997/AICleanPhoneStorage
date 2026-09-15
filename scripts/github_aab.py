#!/usr/bin/env python3
"""GitHub CLI 打包入口。本机仅管理 Secrets 和远程任务，签名文件只在 Actions 生成，绝不调用 Gradle/adb。"""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = "android-google-aab.yml"
SIGNING_SECRET = "ANDROID_SIGNING_STORE_PASSWORD"
DEFAULT_REPO = "Hoole1997/AICleanPhoneStorage"


def command(args, *, data=None, env=None):
    # 密码/令牌经 stdin 或子进程环境传递，禁止出现在命令行与异常信息中。
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            cwd=ROOT, env=env, check=False)
    if result.returncode:
        raise RuntimeError(f"{Path(args[0]).name} {args[1]} 失败（退出码 {result.returncode}），请检查配置/权限。")
    return result.stdout


def gh(repo, *args, data=None):
    return command(["gh", *args, "--repo", repo], data=data)


def configure_signing(repo):
    # 本机只生成密码，不生成/上传密钥；已有密码不能在重复配置时轮换。
    existing = json.loads(gh(repo, "secret", "list", "--json", "name"))
    if any(item["name"] == SIGNING_SECRET for item in existing):
        print("GitHub 已配置签名密码，无需覆盖。")
        return
    metadata = json.loads(command(["gh", "repo", "view", repo, "--json", "isEmpty"]))
    if not metadata["isEmpty"]:
        tree = json.loads(command(["gh", "api", f"repos/{repo}/git/trees/main?recursive=1"]))
        if any(item["path"] == "app/src/google/google-release.keystore" for item in tree["tree"]):
            raise RuntimeError("main 已有签名文件但密码 Secret 缺失，请恢复原密码，不能自动换签。")
    gh(repo, "secret", "set", SIGNING_SECRET, data=secrets.token_urlsafe(32).encode())
    print("已配置随机签名密码；keystore 将由 GitHub Actions 首次生成并提交到 main。")


def configure_packages(repo):
    # 沿用项目 settings.gradle.kts 的凭据来源；只上传指定键，不上传整个 local.properties。
    values = {}
    source = ROOT / "local.properties"
    if source.is_file():
        for line in source.read_text().splitlines():
            if not line.lstrip().startswith(("#", "!")) and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    user = os.environ.get("GH_PACKAGES_USER") or values.get("github.user")
    token = os.environ.get("GH_PACKAGES_TOKEN") or values.get("github.token")
    if not user or not token:
        raise RuntimeError("缺少 GH_PACKAGES_USER/GH_PACKAGES_TOKEN 或 local.properties 私有依赖凭据。")
    gh(repo, "secret", "set", "GH_PACKAGES_USER", data=user.encode())
    gh(repo, "secret", "set", "GH_PACKAGES_TOKEN", data=token.encode())
    print("GitHub Packages 凭据已配置（未输出凭据）。")


def dispatch(repo, ref, watch, download):
    if command(["git", "status", "--porcelain"]).strip():
        raise RuntimeError("工作区有未提交改动。请先按需提交并推送；此脚本不会自动提交或推送。")
    local_sha = command(["git", "rev-parse", "HEAD"]).decode().strip()
    remote_sha = command(["gh", "api", f"repos/{repo}/commits/{ref}", "--jq", ".sha"]).decode().strip()
    if local_sha != remote_sha:
        raise RuntimeError("本机 HEAD 与目标远程 ref 不一致，请先核对并推送要构建的提交。")
    build_id = uuid.uuid4().hex
    gh(repo, "workflow", "run", WORKFLOW, "--ref", ref, "--raw-field", f"build_id={build_id}")
    # 用本次请求 ID 查找运行，避免并发 dispatch 时误下载别人的最近一次产物。
    for _ in range(30):
        runs = json.loads(gh(repo, "run", "list", "--workflow", WORKFLOW, "--event", "workflow_dispatch",
                            "--limit", "50", "--json", "databaseId,displayTitle,url,headSha"))
        match = next((r for r in runs if build_id in r["displayTitle"] and r["headSha"] == local_sha), None)
        if match:
            break
        time.sleep(2)
    else:
        raise RuntimeError(f"已发送请求 {build_id}，暂未查到运行；请用 status 查看，不要重复触发。")
    run_id = str(match["databaseId"])
    print(match["url"], flush=True)
    if watch or download:
        subprocess.run(["gh", "run", "watch", run_id, "--repo", repo, "--exit-status"], check=True)
    if download:
        download_run(repo, run_id)


def download_run(repo, run_id):
    if not run_id.isdigit():
        raise RuntimeError("run ID 必须是数字。")
    destination = ROOT / "build/github-aab" / run_id
    gh(repo, "run", "download", run_id, "--pattern", "google-release-*", "--dir", str(destination))
    print(f"已下载至 {destination}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=DEFAULT_REPO)
    sub = parser.add_subparsers(dest="action", required=True)
    for name in ("configure-signing", "configure-packages", "status"):
        sub.add_parser(name)
    build = sub.add_parser("build")
    build.add_argument("--ref", default="main")
    build.add_argument("--watch", action="store_true")
    build.add_argument("--download", action="store_true")
    download = sub.add_parser("download")
    download.add_argument("run_id")
    args = parser.parse_args()
    if args.action == "configure-signing": configure_signing(args.repo)
    elif args.action == "configure-packages": configure_packages(args.repo)
    elif args.action == "build": dispatch(args.repo, args.ref, args.watch, args.download)
    elif args.action == "download": download_run(args.repo, args.run_id)
    elif args.action == "status":
        subprocess.run(["gh", "run", "list", "--repo", args.repo, "--workflow", WORKFLOW], check=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
