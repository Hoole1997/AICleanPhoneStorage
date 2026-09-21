# 本次埋点验证

- `:app:testLocalDebugUnitTest`：213 tests，0 failures/errors/skips。
- `:app:lintLocalDebug`：0 errors，201 warnings。
- API 32：VideoCleanerDeviceTest、SimilarCleanerDeviceTest、MalwareAdDeviceTest 无失败；1 个要求所有文件授权的测试跳过。
- CleanupTelemetryDeviceTest：1 test，通过。覆盖月份选择的全页总量、冻结确认大小、重复/迟到回调、新操作取消。
- 为保留截图，重新安装相同 localDebug APK，直接 instrumentation 重跑 3 个界面用例：3 tests，通过。

截图取自本次运行，已实际查看：视频列表、相同照片确认、恶意软件 200% 字号结果。图片、计数和广告均为测试 fixture。相同照片截图顶部出现系统推送浮层，不遮挡确认弹窗；恶意软件大字号结果底部可滚动，另附滚动截图。

client-events.txt 是本次 UI 测试的 BusinessMetrics 日志摘录，验证 `vedio` / `duplicatephoto` 参数、勾选 MB 及 Clean 点击。测试用独立索引可直接启动页面，所以 `perm_granted=false` 是模拟器真实权限状态，并非生产入口绕过授权。客户端日志只表示调用 SDK，不代表服务端收数。

未修改广告位/配置/加载逻辑；未在本机构建 google；未提交 Git 或推送。
