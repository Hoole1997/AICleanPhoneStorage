# GitHub CLI 构建 Google Play AAB

来源：StreetHtml 的 `.github/workflows/android-google-aab.yml` 和 app 签名/版本任务。
仓库：<https://github.com/Hoole1997/AICleanPhoneStorage>。

## 边界与签名

- app / notification / metrics 的 google 渠道仅在 GitHub Actions 创建编译、打包、安装任务；本机只使用 local 渠道。
- 本机 CLI 不调用 keytool、Gradle 或 adb，只配置 Secrets、触发/查看远程任务、下载产物。
- 首次云端任务自动生成 RSA 3072 / PKCS12 上传签名，文件提交至 `main` 的 `app/src/google/google-release.keystore`。
- 签名提交使用独立 worktree，只提交 keystore，不把旧分支源码覆盖到 main。全局并发组防止多个任务同时生成；main 已有签名时始终复用，密码错误也不重新生成。
- 密码独立保存在 Secret `ANDROID_SIGNING_STORE_PASSWORD`，不写入代码/日志。别名固定为 `google`，PKCS12 密钥密码与存储密码相同。
- 不使用 `ANDROID_SIGNING_BUNDLE`；不生成或上传本机 keystore。证书的 SHA-256 是密钥证书的指纹，与生成机器的硬件标识无关。
- 每次签名生成提交会由工作流推送 main；正常应用源码只在用户要求时由开发者提交/推送。

## 首次配置

本机要求 Python 3 和 GitHub CLI，先 `gh auth login`。

```bash
python3 scripts/github_aab.py configure-signing
python3 scripts/github_aab.py configure-packages
```

`configure-signing` 仅在缺少 Secret 时生成随机密码，不生成签名文件；已有 Secret 时不覆盖。main 已有签名但密码丢失时停止，需恢复原密码。

`configure-packages` 将环境变量 `GH_PACKAGES_USER` / `GH_PACKAGES_TOKEN` 或被 Git 忽略的 `local.properties` 中 `github.user` / `github.token` 配置为对应 Secrets，只上传两个指定键，不上传整份配置或输出令牌。凭据需要能读取 `toukaRemax/remax_sdk` 中 core 1.0.15 和 bill 1.0.51。

## 构建与下载

工作流需先提交到默认分支。main 的 push 自动构建；手动 CLI 构建使用已推送的指定 ref，不自动提交/推送源码，不发布 Google Play。

```bash
python3 scripts/github_aab.py build --ref main --watch --download
python3 scripts/github_aab.py status
python3 scripts/github_aab.py download 123456789
```

如刚推送已触发自动构建，可直接查看该次运行并下载，无需再次手动触发。CLI 手动请求通过唯一 build ID 精确匹配运行；下载目录为 `build/github-aab/<run-id>/`。若云端首次签名提交使 main 前进，本机先 `git pull --ff-only` 再发起下一次构建。

`--repo owner/repo` 可覆盖目标仓库，放在子命令之前。构建前检查工作区干净且 HEAD 与远程 ref 一致，避免遗漏本机未提交改动。

## 云端步骤

1. Checkout 实际触发提交，配置 JDK 25、SDK 37.0、Gradle Wrapper 9.5。
2. 校验 google 配置包名与 Firebase JSON、版本及私有 Maven 访问权限。
3. 从 main 读取/首次生成 keystore，首次生成必须成功提交 main 才继续打包；注入 `ANDROID_SIGNING_*`。
4. 执行 `bundleGoogleRelease`、Google Debug JVM 测试及 Google Release Lint（当前 AGP 不创建 Release 单元测试任务），保留本项目 AGP 9.3.2 的优化与混淆规则。
5. 验证 AAB 签名和证书，生成 `aiclean_google_release_<versionName>_<versionCode>.aab`。
6. 上传 AAB、`mapping.txt`、公开证书、含源码提交/签名提交的 `build-info.json` 和 `SHA256SUMS`，保留 30 天。失败报告保留 7 天，私钥不进入 artifact。

本机验证：`python3 -m unittest discover -s scripts -p 'test_*.py' -v`；测试模拟所有签名生成/远程写操作，不在本机生成签名或编译 Google 渠道。

参考：[GitHub CLI Secrets](https://cli.github.com/manual/gh_secret_set)、[workflow run](https://cli.github.com/manual/gh_workflow_run)、[Gradle Actions](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)。
