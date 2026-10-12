# 测网速（Speed Test）

Figma `dVsTL6ggoDPXVPcEKg856G`：首页入口 [6562:1177](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6562-1177)，测速中 [6554:757](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6554-757)，结果 [6554:793](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6554-793)，失败弹框 [6565:1676](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6565-1676)。

## 流程

首页 SpeedTest 卡片（红点角标，受统一开关 `home_badge_switch` 控制）→ 直接打开 `SpeedTestActivity`（无首页授权/加载步骤，打开即计为当日已进入）→ 页面进入前台自动测速 → 结果页 Test Again 可重测。

- 测速中离开前台立即取消全部连接，回到前台重新完整测一次，不在后台消耗流量与电量。
- 失败弹框：无网络显示 “No internet connection.”，其他错误（服务端错误、超时等）显示通用失败文案。Retry 重测；Cancel 有上一次成功结果则回到结果，否则关闭页面。
- 部分阶段成功不作为结果展示，失败不冒充结果。

## 测速中页面（在稿件基础上补充）

稿件 6554:757 仅有环形 + “Loading ...”。按需求补充真实进度：环形进度（9 点方向顺时针，与稿件弧起点一致）、整体百分比、当前阶段（连接 / Ping / 下载 / 上传），下方实时填充与结果页相同的指标卡（下载、上传实时速度，Ping，下载/上传抖动）和节点卡（本机网络 + Cloudflare 节点）。整体进度权重：连接与延迟 10%，下载、上传各 45%；进度来自真实耗时与字节数，不由动画伪造。

## 测速口径（Cloudflare，用户 2026-10-12 选定）

端点 `https://speed.cloudflare.com`：`/cdn-cgi/trace`（节点机房代码、Ray ID、延迟探测）、`/__down?bytes=N`、`/__up`。Cloudflare 的 `/meta`、`/locations` 当前不返回城市与运营商，因此节点显示机房 IATA 代码（如 HKG），Test ID 为服务端 Ray ID。

| 项目 | 口径 |
| --- | --- |
| Ping | 复用连接请求 `/cdn-cgi/trace` 10 次（最长 4 秒），取 OkHttp “请求发出→响应头到达”间隔的中位数，不含建连 |
| 下载 | 4 路 HTTP/1.1 并发，各自独立 TCP 连接；上限 50MB 或 10 秒，先到为准；按实际收到字节计数 |
| 上传 | 3 路并发；上限 20MB 或 10 秒；按服务端已确认的请求计数，块大小自适应（约 0.5 秒/块，128KB–4MB） |
| 结果速度 | 跳过前 25%（最长 1.5 秒）慢启动段后的平均吞吐；展示为 KB/s（1KB = 1000 字节）整数 |
| 抖动 | 传输期间另一连接每 300ms 探测负载下延迟，取相邻样本差绝对值的平均 |

请求禁用压缩（`Accept-Encoding: identity`），计数为线路真实字节。左侧网络信息只用无需额外权限的公开 API：Wi-Fi 不读取名称（需要位置权限），蜂窝显示默认数据卡运营商名，机型为 `Build.MODEL`。广告：成功后进入结果页前请求插屏 `scan_complete_speedtest`（回调后揭示结果），返回首页请求插屏 `back_home_speedtest`，详见 [广告位清单](ads/slots.md)。

## 资源

`design/figma/speed-test/assets.json` 记录全部 11 个原版资源的节点、尺寸与 SHA-256；`tools/convert_speed_test_assets.py` 生成透明 WebP。位图填充（入口图标、Wi-Fi 插画、失败盾牌）按节点尺寸 contain 到 4x 母版；矢量图标由 `tools/render_svg_png.js` 4 倍栅格化；结果头部为原版渐变 + 遮罩组合成矢量，2x nodpi。上传图标按稿件对下载图标做垂直镜像，不另做资源。环形进度为原生绘制，颜色与尺寸取自稿件矢量。

## 埋点

首页入口 `speedtest`；页面 `speedtest`（测速中）/ `speedtest_result`（结果）。飞书协议表暂无测网速命名，如需特殊拼写在 `BusinessPageNames` 中映射。

## 验证

- 单元测试：`SpeedTestMathTest`（中位数/抖动/预热/预算）、`CloudflareSpeedTestTest`（MockWebServer：结果字段、字节预算、阶段时限、无网与服务端错误映射）、`SpeedTestViewModelTest`（前后台、失败恢复）。
- 设备：`SpeedTestRenderingTest`（英/德/俄、1.3 与 2 倍字号，基线与不截断）、`SpeedTestNetworkDeviceTest`（真机真实 Cloudflare 测速，约 16 秒、耗费至多约 70MB）。
