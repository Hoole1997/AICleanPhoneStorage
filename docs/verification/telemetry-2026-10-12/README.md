# 新增埋点验证

## 构建与回归

- 仅 local：`:app:testLocalDebugUnitTest`、`:app:lintLocalDebug`、`:app:assembleLocalDebug`、`:app:assembleLocalDebugAndroidTest` 通过。
- 单元测试 260 项，0 failures/errors/skips。覆盖全部原因及 unknown、参数白名单、取消、迟到/重复广告回调、成功事件后的异常、旧失败弹窗及重复动作、实际结果曝光与完成事件分离。
- Lint 0 errors，208 warnings；本次测速及新增设备测试文件未出现 Lint 告警。
- 新增真实弹窗重建测试后仅重新构建测试 APK，并重新执行相关 Lint；生产代码未再次修改。
- OPPO CPH2723，Android 16，系统字号 1.0；安装 localDebug 并保留应用原有数据。
- `NewTelemetryDeviceTest`：2 项通过。实际窗口中的 RecyclerView 验证 Smart Clean、Malware 整卡/按钮、SpeedTest，异步提交尚未重绑时角标快照为 yes，重绑后为 no；真实 DialogFragment 重建保留原因和代次，快速重复 Retry 仅回传一次。
- `SpeedTestRenderingTest`：2 项通过。英/德/俄、多字号，等待扫描完成插屏时仍保持扫描画面，实际结果与失败弹框渲染已检查。

## 真机实际业务路径

1. 首页打开 SpeedTest：`entry=speedtest`、`home_badge=no`、`perm_granted=none`，扫描页面 `speedtestScan`。
2. 真实 Cloudflare 完整测速成功：一次 `speed_scan_result {}`；插屏流程返回后一次 `speed_result_show {}`，页面切为 `speedtest_result`。本次扫描页停留 17.065 秒，仅作为这次测试记录。
3. 点击 Test Again 后立即系统返回：各一次 `TestAgain_click {}`、`speed_scan_back {}`。中止轮次没有被当作完成结果。
4. 暂时关闭 Wi-Fi 触发真实连接失败；设备网络层返回 `network_restricted`。弹窗展示传 reason，Retry 传原 reason + retry；新一轮失败再曝光一次，Cancel 传原 reason + cancel 并回到首页，没有额外扫描返回事件。
5. 带红点的 Network Traffic 点击：`home_badge=yes`、`perm_granted=false`；取消权限说明，没有授予新权限。
6. Wi-Fi 已恢复为原来的开启状态，移动数据保持原来的关闭状态；手机留在应用首页。

全部五个新事件在真实业务日志中出现。实际设备只制造了一个失败类型；其他原因及 unknown 的枚举/时序由注入的单元测试覆盖，没有向上报 SDK 发送虚构测速结果。日志摘录见 [client-events.txt](client-events.txt)，自动测试输出见 [device-tests.txt](device-tests.txt) 与 [rendering-tests.txt](rendering-tests.txt)。系统环形日志后来覆盖早期记录，摘录从当时已采集的 adb 工具输出保留。

## 截图与边界

- [live-testing.png](live-testing.png)、[live-result.png](live-result.png)、[live-failure.png](live-failure.png) 是这次真实测速及失败状态。
- `*-fixture.png` 来自设备离屏渲染测试，只用于布局核验，与实际结果隔离。正常字号结果/失败界面已查看；200% 字号的既有下载抖动标签存在裁切，未在本次埋点任务中调整布局。
- 客户端 `BusinessMetrics` 日志表示已调用上报 SDK；没有访问埋点平台验证服务端收数。
- 未在本机构建或安装 google 渠道，未提交 Git 或推送。
