# 场景推送验收记录

日期：2026-10-22。仅 local 渠道；未编译、打包或安装 google 渠道，未发布远程配置。

后续用户要求已进一步将文案改为单一 `text`（不兼容 `title/desc`），并按视频重做 XML 动效；最新测试与录屏见 [整句富文本与动效修正](motion/README.md)。以下保留初版验收记录，图片目录已更新为最新渲染。

## 构建与静态检查

- `:app:assembleLocalDebug`、两模块 local AndroidTest APK 编译通过。
- `:app:testLocalDebugUnitTest`：367 项通过。
- `:notification:testLocalDebugUnitTest`：115 项通过，合计 482 项，失败/错误为 0。
- `:app:lintLocalDebug` 与 `:notification:lintLocalDebug` 通过，无 Error；仍有 Warning（包括已有项、包可见性提示、KTX 建议和布局提示），不将“通过”解释为零警告。
- `git diff --check` 通过。
- 8 个场景原版源图及 4 份布局素材的 SHA-256、60 份五密度 WebP 非空检查通过。图标位于 24dp 折叠槽位及 56dp 展开槽位；呼吸帧仅缩放原图。原版卸载图与新布局引用原图 SHA-256 相同。
- 旧 `MsgBuilder.kt` 与五份 `layout_notification_general*.xml` 无本次差异；仅新场景使用 `scene_notification_*.xml`。

## 设备测试

| 环境 | 测试 | 结果 |
| --- | --- | --- |
| API 37，Pixel_10a_Offline | 独立 notification 测试 APK，5 项 | 通过，见 `api37/notification-tests.txt` |
| API 37 | SceneRouting、GeneralNotificationLayout、NotificationAlert、NotificationTriggerSource、NotificationLaunchTelemetry，共 19 项 | 通过，见 `api37/app-regression-tests.txt` |
| API 32，Whisp_Locale_API32 | 独立 notification 测试 APK，5 项 | 通过，见 `api32/notification-tests.txt` |
| API 32 | 路由/旧布局/旧声音策略 8 项；另以系统 2.0 字号运行设置页与路由 3 项（其中路由 2 项重复） | 通过，见 `api32/app-regression-tests.txt`、`api32/settings-routing-tests.txt` |

独立测试 APK 包名为 `io.docview.push.test`，使用夹具来源与通用系统小图标；截图中的包名、图标不是生产 App 名称。模拟器测试后撤销该次通知。没有向真实手机发送合成通知，也没有执行真实文件清理或卸载。

覆盖内容：

- 八个新通知 ID 与旧 ID 同时保留，取消一个场景不影响其他场景，旧偏好快照不变。
- 新渠道 HIGH、有 heads-up RemoteViews、无全屏 Intent、无旧删除重发 PendingIntent。
- 远程富文本使用原生 Span，加粗红色为新稿 `#FB4949`；脚本/标签不进入可见文案。
- XML 图标动画不被 ProgressBar 主题 tint 染成单色；按钮不同动画帧的像素确实变化；双箭头使用 XML AnimationDrawable。
- 1.0/2.0 字号下八种图标、折叠/展开布局、动画/静态模式和同 ID reapply。标题高度按真实字体度量核验；系统固定高度内允许省略，展开态提供更完整内容。
- 开关变化与服务会话关闭释放动态广播；场景历史、轮播游标和用户开关独立保存。
- 八种来源经过启动参数归一化、首页消费和功能转交；恶意 URI/超大字段不会穿透白名单。
- 旧 Show/Click/Enter 来源及前后台快照、启动页立即上报与去重回归。
- 单元测试覆盖电池状态边沿、充满/未充满、充电抖动/重连、低电量两档、网络稳定延迟/VPN/漫游/断线后蜂窝回调、下载聚合/去重/时间窗口/100MB 边界、配置失效回退、发送失败不推进轮播、冷却隔离和异步校验期间配置变化。

## 截图

每个平台 `scene-verification/` 保存 32 张静态内容截图、4 张卸载场景动画首帧截图，以及 1 张 SystemUI 实际通知面板截图。离屏 RemoteViews 图片只表示内容区，头部与外框由系统提供；面板截图展示实际组合结果。已核对原版图标、红色粗体、合并文案、按钮、箭头及大字排版。

- [API 37 系统通知面板](api37/scene-verification/system-notifications.png)
- [API 32 系统通知面板](api32/scene-verification/system-notifications.png)
- [动画展开内容](api37/scene-verification/1107_1.0_expanded_animated.png)
- [两倍字号动画展开内容](api37/scene-verification/1107_2.0_expanded_animated.png)
- [两倍字号设置页](api32/settings-2.0.png)

## 实测范围

上述设备测试验证真实 Android RemoteViews/通知发布、布局和生命周期，规则延迟使用确定性测试验证。没有进行所有 OEM 的真实插拔充电、真实应用安装/卸载、长时间媒体写入与系统杀进程恢复测试；没有测量功耗、内存峰值或 ANR 指标，因此不据此宣称性能达标或后台必达。实际悬浮横幅仍受系统、用户渠道设置、勿扰、权限和系统限频控制。

配置、规则及平台边界见 [实现与远程参数说明](../../scene-push.md)。
