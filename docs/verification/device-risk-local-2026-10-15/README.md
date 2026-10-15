# 普通 local 风控在线验证

日期：2026-10-15。设备 CPH2723 / Android 16（API 36）。本次构建、更新安装的是普通 `localDebug` 1.0.4（versionCode 4），包名 `com.leafmotivation.quizguessoncolor`。保留已有应用数据、Firebase JSON 及原 consent 流程，没有关闭手机 VPN、修改系统网络或清空 SDK 缓存。未改动 Google 配置，未构建或安装 Google，未提交 Git。

## 接线与地址

原来仅设置 `xmRiskEnabled=true` 仍会编译 `riskDisabled` 工厂，而且 HTTP 地址会被原有 HTTPS 校验拒绝。现在普通 local 的开关同时选择真实适配器、AAR 依赖及 keepRules；关闭时恢复无 SDK 工厂和依赖。Google 接线保持原样，独立 `localRiskDebug` 保留其 Hotpot 包名与地址。

用户最初配置 `http://aimouse.xoxneaxrch.com/`。为排查而临时精确放行该 HTTP 域名后，SDK 返回 HTTP 401；根地址 HEAD 同时观察到 301 跳转至同域名 HTTPS。使用 `https://aimouse.xoxneaxrch.com/` 后，SDK 可收到有效响应。最终保留 HTTPS，已移除临时 HTTP 例外，恢复原有 HTTPS 校验；最终 APK 没有 networkSecurityConfig 覆盖，`usesCleartextTraffic=false`。不能仅凭对根地址的 HEAD 探测断言 SDK 请求 401 的具体签名原因。

## 真机结果

- HTTP 排查轮：`XM_PlayIntegrity_Result → XM_Upload_PlayIntegrity_Fail`，错误为 `HTTP 401`；约 6.757 秒进入 `FAILED_OPEN / SDK_ERROR`，consent 开始、完成，约 5.252 秒已观察到 MainActivity。SDK 没有有效结果缓存。见 [http.json](http.json)。
- HTTPS：收到 `XM_Upload_PlayIntegrity_Result`、`XM_Get_Detection_Result`，回包 `code="success"`、`data.device_id=null`、`data.device_risk.decision="reject"`。这是有效风控拒绝，尚未取得可用 deviceId；宿主按既有空 ID 兼容规则进入 `APPLIED`，不能将其描述成网络失败或检测超时。
- SDK 同时检测到 ADB、VPN 和 DNS 异常；这些本地信号不能代替服务端拒绝原因。需后台核对本次检测记录，确认允许通过的测试环境后才能验收非空 deviceId。
- HTTPS 冷启动均保留已有 SDK 缓存并重新检测，缓存时间戳有更新。具体时点见 [https1.json](https1.json)、[https-final.json](https-final.json)。证据中的耗时从本机启动测试命令开始，首页按约一秒间隔查询 resumed Activity，属于观测值。
- consent 正常开始并完成，最终 resumed Activity 为 MainActivity，未观察到崩溃或卡在启动页。截图见 [home.png](home.png)。

两轮 HTTPS 冷启动均进入 `APPLIED`，未观察到 PI Fail：

| 冷启动 | 启动前缓存时间戳 | 本轮缓存时间戳 | 检测终态 | consent 开始 / 完成 | 首次观察到首页 |
| --- | --- | --- | --- | --- | --- |
| HTTPS 1 | 1792045779765 | 1792045792568 | 6.626 秒 | 0.928 / 0.930 秒 | 5.140 秒 |
| 最终 APK | 1792045889819 | 1792045906892 | 6.657 秒 | 0.945 / 0.954 秒 | 5.238 秒 |

本次没有观察到 `Ad_Block_*` 事件。`APPLIED` 表示检测结果及初次配置同步完成，不能据此声称所有广告平台已拦截。未修改线上风控策略，未主动点击广告或另行触发广告展示测试，也没有伪造正常 deviceId。

## 构建与边界

```sh
./gradlew :app:assembleLocalDebug :app:testLocalDebugUnitTest :app:lintLocalDebug --console=plain --max-workers=2
```

构建通过，320 项 app 单测无失败，其中风控 44 项；Lint 0 Error、220 Warning，本次风控文件未被标记。计数、最终包名及网络属性见 [checks.json](checks.json)。单测覆盖失败/15 秒截止放行、迟到结果丢弃、PI 失败优先、成功码和空 ID 兼容、限制聚合与埋点边界。实际手机本轮验证 HTTP 失败放行和 HTTPS 有效拒绝，没有人为制造原生 SDK 永久阻塞；不把单测当成真机超时实测。

冷启动证据只保存结果类别、布尔信号、缓存时间戳和必要事件名，不保存指纹、Token、完整设备标识或原始服务端正文。SDK debug 保持关闭。Google release 的 R8/打包未在本机验证。
