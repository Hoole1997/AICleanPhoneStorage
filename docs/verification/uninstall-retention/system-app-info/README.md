# 卸载原因页进入系统应用信息页

2026-10-22。用户明确要求点击原因页“卸载”后进入当前 App 的系统应用信息页面，而不是直接调起卸载确认。

## 实现

- 目标为 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`，data 为 `package:当前 App 包名`。不传反馈文本或额外包名、不使用 ACTION_DELETE/ACTION_UNINSTALL_PACKAGE，也不添加 EXTRA_RETURN_RESULT。
- 原自卸载协调器更名为 `RetentionSettingsCoordinator`，职责只负责系统页导航、防连点、生命周期与外部页面热启动保护。系统页返回后清除 pending 并释放 guard，留在原因页；不把返回值解释为卸载结果。
- 保留原因页点击埋点和反馈快照。系统页中的卸载/停用/清数据均由用户自行操作；应用管理、恶意软件扫描等其他模块的卸载行为未更改。
- 无可用系统处理页或被系统拒绝时显示可重试提示；提示文案已同步项目全部 16 种语言，说明无法打开应用信息页。

## 验证

- `:app:assembleLocalDebug :app:assembleLocalDebugAndroidTest :app:testLocalDebugUnitTest :app:lintLocalDebug` 通过；375 项单元测试，0 失败/跳过，Lint 0 Error。
- Android 37 模拟器 2 项测试通过：校验 action、当前包 URI、无 extras；实际启动系统设置，确认页面属于 com.android.settings，且显示当前 App 名称，再通过系统返回。结果见 device-tests.txt。
- 系统设置的 Compose 页面在窗口事件之后加载内容；设备测试按实际可访问节点有界等待。app-info-entering.png 是测试抓到的进入动画阶段，app-info.png 是同一构建完全显示后的系统页面截图。
- 没有点击系统卸载/停用/清数据按钮，没有卸载任何 App；未操作实体设备。没有提交/推送，用户 local 配置和之前未提交改动均保留。
