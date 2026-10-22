# 卸载挽留验证

实现说明：[动态入口、流程及反馈边界](../../uninstall-retention.md)。

## 结果

- localDebug 编译、APK 打包和 Lint 通过；Lint 0 errors、229 warnings。项目原有警告与竖屏提示保留。新增页面已用独立空窗口背景主题，Lint 仍按应用默认主题推断根布局 Overdraw；未通过禁用规则隐藏提示。
- 全部 367 项单元测试通过，0 失败、0 跳过。新增 RetentionTest 6 项覆盖配置解析、人群门槛、开屏后路由、通知替换挂起路由、广告/反馈恢复、反馈不上传及六个功能目标。
- Android 37 模拟器：RetentionDeviceTest 与 NewUserFlowDeviceTest 共 8 项通过，无跳过。动态快捷入口发布/移除、保留其他入口、启动路由标记、插屏失败后等待前台、系统卸载 Intent、新用户流程及动画回归均通过。
- 16 种语言 × 2 种字号 × 2 个页面，共 64 个原生 XML 渲染场景通过；覆盖 375×812 和 320×568 视口、文本自然高度、无截断、单选互斥和 500 字符输入边界。最后调整原因页按钮颜色后，重新运行完整布局用例通过。
- 核验 14 份原版 Figma 源资产及 SHA-256、57 份 WebP 密度资源。无整页截图作为运行时 UI。
- 已安装最新 localDebug 到连接的手机；没有编译、打包或安装 google 渠道，没有自动 Git 提交或推送。

## 截图

截图由 Android 原生 View 在模拟器渲染，示例选中项仅用于验证；生产原因页默认不选择。

- [挽留页顶部](retention-verification/en-1.0-OFFER-top.png)
- [挽留页底部及两种操作](retention-verification/en-1.0-OFFER-bottom.png)
- [原因页](retention-verification/en-1.0-REASON-top.png)
- [中文原因页](retention-verification/zh-Hans-1.0-REASON-top.png)
- [德语大字号卡片与按钮](retention-verification/de-2.0-OFFER-bottom.png)
- [阿拉伯语大字号输入与按钮](retention-verification/ar-2.0-REASON-bottom.png)

文档中的 Before you go 与功能说明在 Figma 中没有展示，本次按需求正文保留并允许滚动。常规字号卡片按 Figma 横向排布，大字号/窄屏使用图标在上、文字在下的布局。原因行触控高度至少 48dp，长文本自然展开。

## 测试边界

没有确认真实卸载，没有点击真实广告，没有上传选项或输入。系统卸载测试验证当前应用包名和系统 Intent；广告测试使用可控失败回调验证单次请求、关闭/失败后的前台续接。真实广告填充、OEM Launcher 的菜单外观和用户确认后的实际卸载行为未据此宣称已全面验证。

动态入口测试使用单独测试 ID 并在 finally 中清理，没有创建固定快捷方式或改动真实渠道归因；固定副本关闭逻辑由 Android disableShortcuts API 实现，未自动操作桌面固定确认。
