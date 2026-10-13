# 风控冷启动、广告限制与埋点验证

日期：2026-10-13。仅构建和安装 `localRiskDebug`，实际包名 `com.hotpotgames.happysave.global`，服务 `http://test-happysave.movieapi.novhop.com/`。Google 配置、Firebase JSON、consent 页面与顺序均未修改；未构建或安装 Google 渠道。

## 改动

- 冷启动由原先 `clearCache → getResult → init` 改为 `getResult → refresh`，使用带 Application Context 的官方强制刷新入口；一进程一轮，SDK retryCount=0。不手工操作 SDK 缓存，不定时检测，native 调用仍在有界 worker。
- 有效空 ID 响应兼容数字 200、字符串 `200`/`success`，检查 `deviceId`/`device_id` 两个字段。未知协议、非法/冲突 ID、网络/解析/SDK/PI 失败仍整体放行；`decision=reject` 不替代 SDK 远端异常标记。
- 检测最终达到 APPLIED，最新整组平台限制同步成功后，按命中维度每进程上报一次 `Ad_Block_VPN/DNS/IP/Simulator/ADB/SIM/No_GP/Sys_9_Low/Play`，scope=`admob` 或 `all`。遥测独立于 15 秒启动门禁；失败、超时和部分同步成功不报，重试/配置变化不重复。
- 原有 5 秒指纹等待、15 秒总期限、迟到结果丢弃、失败撤销本桥接 PROCESS 限制及启动页 consent 流程保持。

## 编译与单测

```sh
./gradlew :app:assembleLocalRiskDebug :app:lintLocalRiskDebug :app:testLocalDebugUnitTest --console=plain --max-workers=2
./gradlew -PlocalRiskInstrumentation=true :app:assembleLocalRiskDebugAndroidTest --console=plain --max-workers=2
```

两次构建成功，localRiskDebug Lint 无 Error、210 项 Warning（本次风控源码未被标记），app 306 项单测无失败，其中风控 44 项。单测覆盖成功码和两个 ID 字段的边界、PI 失败兜底、15 秒截止及迟到回调、多风险 OR、MAX 重试、部分同步失败零埋点、去重和遥测异常。计数见 [unit-tests.json](unit-tests.json)。

## 真机冷启动

设备 CPH2723，在线。安装新版 APK 保留旧 SDK 缓存后，连续两次 force-stop 冷启动，未手动删除 SDK 缓存：

| 冷启动 | 启动前 cached_at_millis | 本轮 cached_at_millis | 检测终态耗时 |
| --- | --- | --- | --- |
| 1 | 1791870829463 | 1791871871493 | 约 7.9 秒 |
| 2 | 1791871871493 | 1791871921222 | 约 7.2 秒 |

两轮均重新出现 Play Integrity 开始/失败事件，结果缓存更新，consent 开始并完成，最终 resumed Activity 为 MainActivity。脱敏证据为 [cold1.json](cold1.json)、[cold2.json](cold2.json)，未保存指纹、Token、设备标识或原始响应。

联调仍遇到 Play Integrity `-16`（Cloud Project Number 无效），服务端有效响应为 `code=success`、`device_id=null`、`decision=reject`。PI 失败优先使本轮 FAILED_OPEN；协议兼容不绕过该失败，也不将空 ID 报成正常 deviceId。SDK/服务配置问题尚待提供方处理。

## 真实 Bill / AdRisk 门禁验证

显式 `-PlocalRiskInstrumentation=true` 才生成 riskDebug 测试 APK，默认 debug 仪器配置及 Google variant 禁用规则不变。

```sh
adb -s <device> install -r app/build/outputs/apk/androidTest/local/riskDebug/app-local-riskDebug-androidTest.apk
adb -s <device> shell am instrument -w -e class com.example.aicleanphonestorage.app.risk.LocalRiskRestrictionDeviceTest com.hotpotgames.happysave.global.test/androidx.test.runner.AndroidJUnitRunner
```

3 项真机仪器测试通过，见 [instrumentation.txt](instrumentation.txt)：

- 独立测试配置/快照经真实 PolicyBridge 写入 AdRisk PROCESS 状态，验证 `-1` 放行、`0` 只限制独立 ADMOB、`1` 限制 ADMOB/GAM/TOPON/PANGLE/MAX，多风险 OR 及撤销。
- 读取 Bill support_risk_1.0.11 已安装的真实插件及平台候选 gate，验证 SDK 和 Bill 候选门禁结果一致。不替换插件，不请求、展示或点击真实广告；反射仅用于测试 APK 的锁定版本门禁，正式 APK 不包含。
- 9 项 Ad_Block 经过真实 Telemetry 转换，确认正确事件名、scope 和去重；测试事件进入独立收件箱，不上报数数。失败及缩短为 100ms 的测试截止均释放真实 SDK 平台限制，广告等待结束且零 Ad_Block；生产 15 秒边界另由单测覆盖。

正向限制验证使用明确隔离的测试快照，不能冒充线上 SDK 检测成功。当前 local 联调未启用 Firebase，默认 9 项策略全为 -1；线上 PI 仍失败，真实服务返回 deviceId 及线上命中后的广告展示限制尚未验收。
