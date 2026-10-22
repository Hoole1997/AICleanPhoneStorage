# 增强通知动效与一分钟充电防抖

2026-10-22，按用户核对后的明确要求实现，不受原需求对动效幅度的限制。

## 改动

- 新场景及旧日龄普通通知的折叠/展开按钮都采用 XML AnimatedVectorDrawable：1100ms 背景呼吸（X 最小 90%、Y 最小 86%）、两层错开 700ms 的 1400ms 扩散淡出水波纹、1400ms 单向扫光。
- 波纹裁切在圆角背景内；文字、点击 PendingIntent 和 View 布局边界不随动画缩放。原图图标及其已有呼吸保留，不重绘图标。
- 统一 `NotificationMotion` 只判断视图动效及静态替代层，构建时尊重减少动效/省电/灭屏；RemoteViews reapply 显式切换两层，隐藏后的原生 ProgressBar 停止。没有增加 App 帧轮询或为了动画反复 notify。
- 保留新旧通知发布、点击、ID、频控、循环和 FIFO 调度；旧日龄按钮只更新视图背景。
- 充电开始连接防抖改为 60 秒，与确认延迟和远程每场景间隔分别维护。59.999 秒内抑制，满 60 秒允许新候选；持续抖动从最近一次连接计算。
- `tools/generate_notification_motion.py` 统一生成 XML；原资产转换脚本调用此生成器，避免下次转换回退为旧效果。

## 验证

- localDebug：notification 150、app 373 项单元测试通过，合计 523 项，0 失败。包括新增一分钟精确边界与现有八场景循环/FIFO/旧配额回归。
- notification/app Lint 0 Error；已有 Warning 保留。app Kotlin 编译通过；未构建或安装 google 渠道。
- Android 37 独立模拟器 4 项设备测试通过，见 `device-tests.txt`：旧折叠/展开实际像素变化、可点击、背景边界匹配；静态 reapply 后停止；新按钮像素变化及隐藏停止；原图和 HTML 在 1x/2x 字号下无正文裁切。
- 独立水波纹夹具仅启用生产波纹目标、关闭呼吸/扫光，验证实际屏幕像素随扩散/淡出变化，避免只证明扫光在动。
- 系统通知 1 项测试通过，见 `system-notification-test.txt`：八个新 ID 与旧通知共存、独立取消、旧计数未变化，埋点来源及富文本纯文本快照正确。
- 实际屏幕截图保存在 `preview/`；系统通知录屏为 `system-notifications.mp4`。录屏用于效果核验，不表示已测量真实设备帧率或功耗。
