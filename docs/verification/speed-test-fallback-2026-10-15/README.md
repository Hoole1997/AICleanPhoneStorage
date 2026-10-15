# 测速备用路线验证

日期：2026-10-15。仅构建、安装普通 localDebug，真机 CPH2723 / Android 16。用户确认已关闭设备 VPN 后进行最终网络测试；未改动其余系统网络设置。Google 配置未改，未构建或安装 Google，未提交 Git。

本目录保留验证时工作区的历史证据，当时同时包含广告调试工具及 Bill 1.0.12。`checks.json` 分别记录网络测试与最终排版测试的 APK 哈希；后续按提交独立打包的产物以交付清单为准，不把此处工作区总测试数当成独立提交的测试数。

## 行为

优先 Cloudflare；同一可用系统网络上的连接、DNS、TLS、HTTP、协议、超时或有效数据不足错误，改用一次 LibreSpeed / Clouvider Los Angeles 公共备用。备用仍失败才向 UI 抛出最终错误，原七类错误文案保持。设备离线、强制门户、网络信息不可用、网络切换与用户取消不追加备用请求。

两条路线共用一次 60 秒总时限，下载请求合计最多 50MB、上传最多 20MB。主路线最多 30 秒和 80% 字节预算，为迟发故障保留备用完整测量额度。失败请求不退还已领取预算。切换时清空进度、指标和节点，备用重新测延迟、下载、上传，不拼接不同路线的数据。原有结果/失败埋点和扫描完成插屏不增加次数，页面离开前台仍取消。

协议与引擎分离：`SpeedTestEndpoint` 负责请求和合法响应，`HttpSpeedTest` 负责顺序切换、测量、预算和取消。建连异步等待可绕开系统 DNS 不可中断的问题；迟到响应关闭，每仓库至多 12 个待建连名额防止反复重测积累。备用下载按整 MiB 领取，上传必须取得空确认响应，200 错误 JSON/HTML 不计入成功上传。

## 自动验证

```sh
./gradlew :app:testLocalDebugUnitTest :app:assembleLocalDebug :app:assembleLocalDebugAndroidTest :app:lintLocalDebug --console=plain --max-workers=2
```

通过 344 项 app 单测，其中测速 49 项，包含 13 项自动切换测试；Lint 0 Error、220 Warning，测速文件没有新增标记。详情计数见 [checks.json](checks.json)。覆盖主路成功不访问备用、连接/下载/上传各阶段故障、双路失败、Online 下 DNS 故障、阻塞 DNS 截止前切换、网络状态变化、取消、进度清空、全轮时间/流量预算及备用上传错误正文拒绝。

## 真机网络

两项网络仪器测试通过，记录见 [network.txt](network.txt)；此后仅调整字号排版及其检查，没有改动已验证的网络逻辑：

| 测试 | 实际服务 / 节点 | 下载 | 上传 | Ping | 耗时 |
| --- | --- | --- | --- | --- | --- |
| 默认优先路线 | Cloudflare / FRA | 8399 KB/s | 820 KB/s | 232.0 ms | 19.6 秒 |
| 主路线注入 503 后的真实备用 | LibreSpeed / Los Angeles | 39 KB/s | 529 KB/s | 280.0 ms | 12.1 秒 |

备用用例仅在测试 OkHttp 拦截器中注入 Cloudflare 503；备用的连接、下载和上传均为真实网络，限制下载 8MiB、上传 2MB、每传输阶段 5 秒。不能把注入错误描述为本次 Cloudflare 真实故障，也不能将当前网络速度当作节点性能基准或大陆三网可用性结论。

界面使用 `SpeedTestRenderingTest` 分开验证 CF / 备用结果、无 Ray ID 行隐藏、英德俄语言及最大 2 倍字号，证据见 [rendering.txt](rendering.txt)。截图核验发现原离屏 `isShown` 判断会跳过未 attach 视图，已改为按可见父链遍历，并断言无省略和文本布局高度完整。节点信息在大字号/窄屏时纵排，节点及指标标签自然换行。其中 [backup.png](backup.png)、[backup-large-font.png](backup-large-font.png) 是最终布局的离屏渲染，用隔离的样例数值检查排版，不是真实测速结果截图。

一次合并测试在两个网络方法完成后、渲染期间被外部 force-stop；系统退出原因是 USER REQUESTED / FORCE STOP，未发现 Java 崩溃记录。最终将网络和渲染分开重跑，以上记录使用重跑结果。

## 公共节点选择

- 最终地址 `https://la.speedtest.clouvider.net/backend`，来自 [LibreSpeed 官方节点列表](https://librespeed.org/backend-servers/servers.php)。延迟/上传 `empty.php`，下载 `garbage.php?ckSize=N`，协议见 [官方后端](https://github.com/librespeed/speedtest/tree/master/backend)。未调用 IP 查询、遥测及分享接口。
- 东京 A573 节点在桌面能传输，但真机返回 403，因此未选用。USTC 候选真机返回 500，且其 [公开服务说明](https://test.ustc.edu.cn/) 要求浏览器完成 PoW，未尝试规避，未选用。
- 按用户要求查询阿里云、腾讯云，目前未找到可直接匿名复用的官方完整上下行/延迟接口。[阿里云 OSS probe](https://help.aliyun.com/zh/oss/developer-reference/probe-probe-state) 上传要求已授权 Bucket 的读写删除权限，控制台测速工具匿名访问转登录；[腾讯云 COSBrowser](https://cloud.tencent.com/document/product/436/116403) 使用自有 Bucket 或桶绑定测速链接。静态下载文件及存储服务的错误响应不能作为完整双向测速。

当前备用仍是国际节点；没有自有或获授权的国内端点，因此不承诺解决全部大陆线路限制。后续可注入国内同类协议节点，不改 UI、测量或广告流程。
