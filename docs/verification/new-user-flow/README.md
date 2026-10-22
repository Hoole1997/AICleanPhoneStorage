# 新用户流程验证 — 2026-10-21

仅执行 local 渠道构建、安装和测试；未构建或安装 google 渠道。

- `:app:testLocalDebugUnitTest`：361 项通过，0 失败；其中新增 `NewUserFlowTest` 6 项，覆盖严格 JSON 默认值、开屏后路由、字段失败/引擎重试、后台取消后续扫、写盘后请求一次广告、进程恢复及埋点去重。
- `:app:assembleLocalDebug :app:assembleLocalDebugAndroidTest :app:lintLocalDebug`：通过；Lint 0 errors、226 warnings。保留项目已有警告，以及竖屏策略、原稿省略号和根布局背景等提示，不以关闭 Lint 规避检查。
- Android 37 模拟器：`NewUserFlowDeviceTest` 4 项全部通过，无跳过。真实设备参数读取、Activity 重建、DataStore 跨实例恢复、16 语言布局、动画的焦点/后台/离屏/完成停止均通过。
- Android 36 真机：完成 DataStore 和 16 语言原生 View 渲染核验。首次真实 Activity 用例遇到手机息屏，页面处于 CREATED 且扫描正确取消；未修改手机锁屏设置，前台用例随后在模拟器完成。
- 18 份 Figma 原始图层的 SHA-256 与 86 份 WebP 密度资源核验通过。背景使用 Figma 单独导出的原始图层，避免 SVG 渲染器对模糊效果的差异。
- 大字体检查使用 2 倍字号和 320×568 dp 视口；全部可见文本自然展开，无 ellipsis/高度裁切，卡片保持间距，按钮高度不小于 48dp 且滚动可达。

## 截图

- [运行中的独立 Activity，真实数据](emulator/complete.png)
- [按设计视口渲染的完成态](new-user-verification/en-1.0-complete-top.png)
- [扫描态](new-user-verification/en-1.0-scanning-top.png)
- [德语 2 倍字号，小屏底部按钮](new-user-verification/de-2.0-complete-bottom.png)
- [引擎失败与 Retry](new-user-verification/en-2.0-failed-bottom.png)

`new-user-verification/` 的布局截图由 Android 原生 View 在真机绘制，固定样例仅存在于 androidTest；生产页面读取真实系统数据。系统状态栏保留空间，未伪造时间/信号图标。实际 Activity 截图另见 `emulator/`。

未点击真实广告，也未把接线测试视为线上填充验证。`new_user_start_clean` 的实际展示仍取决于广告后台配置、频控和填充。

## 2026-10-22 Boolean 参数纠正

用户确认 new_user_flow_config 本身是 Boolean。已删除 JSON 解析及对应旧测试，改用 SDK getBoolean，保留未配置默认 true。上文严格 JSON 验证属于历史实现。当前回归见 [Boolean 参数验证](boolean-config.md)。
