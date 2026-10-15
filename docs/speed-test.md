# 测网速（Speed Test）

Figma `dVsTL6ggoDPXVPcEKg856G`：首页入口 [6562:1177](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6562-1177)，测速中 [6554:757](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6554-757)，结果 [6554:793](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6554-793)，失败弹框 [6565:1676](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6565-1676)。

## 流程

首页 SpeedTest 卡片（红点角标，受统一开关 `home_badge_switch` 控制）→ 直接打开 `SpeedTestActivity`（无首页授权/加载步骤，打开即计为当日已进入）→ 页面进入前台自动测速 → 结果页 Test Again 可重测。

- 测速中离开前台立即取消全部连接，回到前台重新完整测一次，不在后台消耗流量与电量。
- 优先 Cloudflare；同一可用网络下主路线的连接、协议、服务端、超时或弱网异常，自动改用备用完整测一次。切换时清空主路线的指标、节点和进度，只有备用也失败才展示失败弹框；设备已离线、强制门户、网络信息不可读或网络已切换时直接提示对应系统网络问题，不发起无效备用请求。
- 失败弹框（用户 2026-10-12 指定文案）：Retry 重测；Cancel 按最新埋点文档关闭弹窗并返回首页。

| 异常类型 | 判定 | 文案 |
| --- | --- | --- |
| 无网络（默认） | 开始时无可用网络、出错时已离线，以及无法归类的连接错误（DNS 失败、连接被拒/重置等） | No internet connection. |
| 网络过弱 | 网络可用，但某个传输阶段在时限内传输量低于 64KB，或无法得出速度 | Network is too weak to complete the test. |
| 测速超时 | 建连/读写 `SocketTimeoutException`，或整次测速超过 60 秒总时限 | Speed test timed out. Please try again. |
| 网络切换 | 测速期间系统默认网络切换（`registerDefaultNetworkCallback`，仅测速期间注册），或出错时网络句柄已变化 | Network changed. Please try again. |
| 网络受限 | 系统检测到强制门户（需登录 Wi-Fi）、HTTP 401/403/407/451、TLS 握手/证书校验失败 | Network access is restricted. |
| 网络信息失败 | 无法读取系统网络状态（ConnectivityManager 不可用或拒绝访问） | Unable to access network information. |
| 服务器异常 | HTTP 5xx、429 及其他非预期响应、协议错误 | Speed test failed. Please try again. |

判定顺序：先看出错时的系统网络状态（离线 → 切换 → 强制门户 → 不可读），再看异常类型；规则集中在 `SpeedTestFailureClassifier`。
- 部分阶段成功不作为结果展示，失败不冒充结果。

## 测速中页面（在稿件基础上补充）

稿件 6554:757 仅有环形 + “Loading ...”。按需求补充真实进度：环形进度（9 点方向顺时针，与稿件弧起点一致）、整体百分比、当前阶段（连接 / Ping / 下载 / 上传），下方实时填充与结果页相同的指标卡（下载、上传实时速度，Ping，下载/上传抖动）和节点卡（本机网络 + 当前实际测速服务及节点）。整体进度权重：连接与延迟 10%，下载、上传各 45%；进度来自真实耗时与字节数，不由动画伪造。

节点信息在字体倍率 ≥1.5 或宽度 <360dp 时改为纵排；标题、节点和指标标签自然换行，避免长备用节点名被省略。离屏测试按可见父链检查实际文本布局，不能用未 attach 时恒为 false 的 `isShown` 跳过断言。

## 测速口径（Cloudflare，用户 2026-10-12 选定）

端点 `https://speed.cloudflare.com`：`/cdn-cgi/trace`（节点机房代码、Ray ID、延迟探测）、`/__down?bytes=N`、`/__up`。Cloudflare 的 `/meta`、`/locations` 当前不返回城市与运营商，因此节点显示机房 IATA 代码（如 HKG），Test ID 为服务端 Ray ID。

2026-10-15 新增备用：`https://la.speedtest.clouvider.net/backend`，节点 `Los Angeles`。节点来自 [LibreSpeed 官方公共节点列表](https://librespeed.org/backend-servers/servers.php)，服务由 Clouvider 提供。它是独立国际路线，不等于大陆节点；当前网络实测可达不能代替中国大陆不同运营商的可用性验证。如需自有国内节点，可替换注入的 `LibreSpeedEndpoint`，不改 ViewModel、测量引擎或广告流程。

按用户要求核查了国内阿里云、腾讯云：目前未找到无需凭证即可同时完成上下行和延迟测试的官方公共 App 接口。[阿里云 OSS probe](https://help.aliyun.com/zh/oss/developer-reference/probe-probe-state) 的上传测速需授权 Bucket 的 GetObject/PutObject/DeleteObject 权限，控制台测速工具匿名访问转登录；[腾讯云 COSBrowser 测速](https://cloud.tencent.com/document/product/436/116403) 需已建 Bucket 或桶绑定的测速链接。公开文件/镜像下载不能替代上传确认，不将存储服务的 XML 错误体作为有效测速数据。

协议按 [LibreSpeed 官方后端](https://github.com/librespeed/speedtest/tree/master/backend)：`empty.php` 用于连接/Ping/上传确认，`garbage.php?ckSize=N` 每块为 1MiB。仅请求整 MiB，剩余不足 1MiB 不向上取整；不调用 IP 查询、遥测或分享接口，不伪造 Cloudflare Ray ID。没有 Test ID 时隐藏该行。连接响应有界读取，并拒绝 HTML 登录页、异常 trace 和非空的备用探测响应。

| 项目 | 口径 |
| --- | --- |
| Ping | 复用连接请求 `/cdn-cgi/trace` 10 次（最长 4 秒），取 OkHttp “请求发出→响应头到达”间隔的中位数，不含建连 |
| 下载 | 4 路 HTTP/1.1 并发，各自独立 TCP 连接；两路合计上限 50MB，每路线下载阶段最多约 10 秒；按实际收到字节计数 |
| 上传 | 3 路并发；两路合计上限 20MB，每路线上传阶段最多约 10 秒；按服务端已确认的请求计数，块大小自适应（约 0.5 秒/块，128KB–4MB） |
| 结果速度 | 跳过前 25%（最长 1.5 秒）慢启动段后的平均吞吐；展示为 KB/s（1KB = 1000 字节）整数 |
| 抖动 | 传输期间另一连接每 300ms 探测负载下延迟，取相邻样本差绝对值的平均 |

请求禁用压缩（`Accept-Encoding: identity`），计数为线路真实字节。左侧网络信息只用无需额外权限的公开 API：Wi-Fi 不读取名称（需要位置权限），蜂窝显示默认数据卡运营商名，机型为 `Build.MODEL`。广告：成功后进入结果页前请求插屏 `scan_complete_speedtest`（回调后揭示结果），返回首页请求插屏 `back_home_speedtest`，详见 [广告位清单](ads/slots.md)。

两路共用一次 60 秒总期限；Cloudflare 最多占用 30 秒和 80% 下载/上传预算，给迟发故障后的备用保留至少约 10MB/4MB。失败或被取消的请求也消耗其已领取字节预算，不返还后重发；主路线提前失败时，备用使用全部剩余额度。每次切换都重新测 Ping、下载、上传，不能拼接不同路线的结果。端点协议集中于 `SpeedTestEndpoint`，调度/预算/取消集中于 `HttpSpeedTest`；只发射整轮最终成功或最终失败，原有统计及插屏流程不增加次数。

建连通过异步 Call 等待，避免系统 DNS 阻塞拖住主路/整轮截止；超时后取消宿主等待并关闭迟到响应。不可中断的底层 DNS 可晚结束，每个仓库最多保留 12 个待建连名额，防止重复重测无界积累。正文读写仍在有限 IO 调度器执行，取消时关闭 Call；备用上传严格验证空确认响应，HTTP 200 的错误 JSON/HTML 不得算成功。

## 资源

`design/figma/speed-test/assets.json` 记录全部 11 个原版资源的节点、尺寸与 SHA-256；`tools/convert_speed_test_assets.py` 生成透明 WebP。位图填充（入口图标、Wi-Fi 插画、失败盾牌）按节点尺寸 contain 到 4x 母版；矢量图标由 `tools/render_svg_png.js` 4 倍栅格化；结果头部为原版渐变 + 遮罩组合成矢量，2x nodpi。上传图标按稿件对下载图标做垂直镜像，不另做资源。环形进度为原生绘制，颜色与尺寸取自稿件矢量。

## 埋点

首页入口 `speedtest`，携带点击时实际显示的 `home_badge=yes/no`；页面 `speedtestScan`（测速中）/ `speedtest_result`（结果）。测速完成、扫描返回、结果曝光、Test Again 及失败弹窗埋点见 [2026-10-12 新增埋点](metrics/telemetry-2026-10-12.md)。

## 验证

- 单元测试：`SpeedTestMathTest`（中位数/抖动/预热/预算）、`CloudflareSpeedTestTest`（单路线测量基线）、`SpeedTestEndpointTest`（CF/备用协议与有界响应校验）、`HttpSpeedTestFallbackTest`（故障切换、全轮预算、取消与无混合结果）、`SpeedTestViewModelTest`（前后台、失败恢复）。
- 设备：`SpeedTestRenderingTest`（英/德/俄、1.3 与 2 倍字号，CF/备用节点、无 Ray ID 状态、基线与不截断）、`SpeedTestNetworkDeviceTest`（真实优先路线，及测试拦截器注入主路线 503 后的真实备用上传/下载；后者限制为最多约 10.4MB，不能将注入故障描述为 CF 真实故障）。
