# 旧通知展开按钮文字居中

2026-10-23，仅调整 `view_notification_general_action.xml` 的文字层高度。

动效 drawable 的 46dp 固有高度可撑大 FrameLayout；原文字层仍以 32dp 最小高度自然测量且贴顶，导致文字自身 `gravity=center` 无法相对整个背景居中。文字层改为 `match_parent`，由 FrameLayout 自然测量后再铺满最终高度，保留原有字号、最小高度、padding、背景动效和点击事件。长文案继续自然撑高，不固定文字高度。

## 验证

- `:notification:assembleLocalDebugAndroidTest`、`:notification:lintLocalDebug` 通过，仅构建 local。
- API 37 只读模拟器、独立 `io.docview.push.test` APK；未安装/修改用户手机中的应用。
- `LegacyExpandedActionLayoutTest`：修改前复现失败（2x 密度下文字中心 32px、背景中心 46px，偏上 7dp）；修改后通过。
- 96 组：两种展开布局 × 英/德/阿拉伯/印地语 × 1.0/1.3/2.0 字号 × 240/320dp 宽度 × 动效/静态背景。检查文字垂直中心、文字裁切、完整按钮高度的点击区域。
- 既有 `SceneNotificationDeviceTest#legacyMotionSupportsBothLayoutsAndStaticReapply` 通过，保留真实动效及静态切换。
- 图片为原生 RemoteViews 渲染截图，已目视检查；本次未在 OEM SystemUI 上复测，不推断其他通知布局的显示结果。

## 截图

- [修改前](before.png) / [同布局修改后](after.png)
- [Android 13+ 展开布局](modern-after.png)
- [窄屏、2 倍字号、德语多行按钮](large-font-after.png)
