# 启动页与正式 Logo

- 启动页 Figma：`dVsTL6ggoDPXVPcEKg856G`，节点 `5619:3118`，背景原图 `5619:3119`。
- 正式 Logo：直角 `5619:3150`，圆角 `5619:3156`。原始导出与来源说明在 `design/figma/app-logo/`。
- 独立 `StartupActivity` 为唯一 Launcher；AndroidX SplashScreen 1.2.0 衔接系统启动画面；系统 SplashScreen 图标直接使用与 Manifest 相同的 `@mipmap/ic_launcher`。
- 常驻通知 PendingIntent 指向启动页；notification 模块使用系统 Launcher 解析，因此普通通知自动进入启动页，无需修改模块逻辑。
- 旧版本仍指向 MainActivity 的通知也会先转启动页。启动页只传递白名单目标，结束后进入首页，再由首页执行既有权限和业务跳转。
- `StartupAdCoordinator` 在语言恢复完成、启动页 RESUMED 且取得焦点、绘制首帧后调用 `AdExt.loadSplash(positionName = "splash")`。联网时由 SDK 控制加载、展示及失败，`call` 回调后放行：广告请求不足 3 秒时等待剩余时间。按用户 2026-09-15 要求，仅启动页增加连续断网 3 秒的等待上限，其他广告加载、扫描、清理和压缩流程不变。
- `StartupNetworkMonitor` 使用默认网络回调，只有系统确认无可用互联网（含未验证网络或网络被阻止）时视为离线；查询失败为未知，不误判断网。只在启动页 RESUMED 时监听，离开即注销。联网恢复或页面退后台取消离线计时；返回后按新的网络状态重新计时。系统权限流程保持正常完成，不被网络超时跳过。
- 离线超时先失效请求编号，取消本页开屏请求协程并关闭其 SDK loading，再允许消费原定路由。迟到回调忽略。SDK 已报告广告加载成功时停止离线计时，已加载/展示的广告正常结束。应用级 SDK 初始化不受影响。
- 底部使用 Material 原生循环进度条，不填充 0–100、不显示虚构百分比；后台、广告覆盖或失去焦点时停止动画。关闭系统动画/省电/触控探索时保留静态加载段和 Loading 文案。
- `StartupViewModel` 保存请求编号和完成状态，成功/失败回调都放行且只消费一次。旋转不重复请求；等待期间的新通知更新目标；彻底退出后的迟到回调忽略。进程重建保留路由，但重新申请已经丢失的广告请求。广告回调发生在后台时，等页面 RESUMED 且取得焦点才执行首页交接。
- `StartupAdCoordinatorTest` 用假 SDK 验证回调、旋转、后台恢复、离线请求取消顺序和网络订阅释放；`StartupViewModelTest` 保留联网等待回调、准备顺序及去重验证；`StartupOfflineTest` 覆盖 2999/3000ms 边界、断网/重连、已有广告、后台取消和新通知目标。`StartupActivityFlowTest` 会请求真实开屏广告，需要显式传入 instrumentation 参数 `run_live_ads=true`，不纳入普通自动回归。
- 启动页到首页使用 240ms 原生窗口交叉淡入：首页透明度从 0 到 1，启动页在底层保持完整直到转场结束，避免露出窗口底色。仅影响这一跳转，不修改其他功能页动画；关闭系统动画、省电或触控探索时直接交接。
- 退出启动页后移除其 Activity，返回键不会重放启动页；只有恢复可见且取得窗口焦点后才转交首页。
- 背景为开发时转换的五档 WebP，最多 852×1846 像素，解码放在 IO。文字与进度条为原生组件，支持大字体、短屏、横屏和系统栏 inset。
- Logo 使用正式 Figma 导出生成密度资源。圆角导出的灰色画布角被按 Figma 的 106px 裁切恢复透明度。桌面自适应图标保留原图安全边距；SplashScreen、启动页和关于页面均使用正式 Logo。

转换脚本：`tools/convert_startup_assets.py`（背景）、`tools/convert_app_logo_assets.py`（正式 Logo）。使用 Pillow，无运行时图片转换。

验证用例：`StartupViewModelTest`（回调放行、准备顺序、恢复、目标消费），`StartupAdCoordinatorTest`（模拟广告回调、循环进度和重建），`StartupNavigationTest`（Launcher、白名单、普通/常驻/旧通知路由），`StartupRenderingTest`（Figma 尺寸、大小字体、横屏），`StartupActivityFlowTest`（显式开启真实广告测试后验证冷/热/旧通知及返回栈）。

本次接入通过 local/google Debug 构建、local Lint 和单元测试；API 32 模拟器的协调器、路由、布局共 6 项检查通过，已核验横竖屏及大字体截图。展示流程使用模拟回调，不将其作为真实广告填充验证。

2026-09-15 离线等待验证：local Debug 构建、Lint、173 项 app 单元测试通过；Android 17.2 模拟器中启动协调、路由、布局共 12 项回归通过，最后补充的超时/迟到广告竞态和页面重建 2 项重跑通过。设备测试注入持续网络状态和假广告回调，没有切换用户真机网络，也不将其当作真实广告填充测试。模拟器使用现有第三方 native 库的系统页大小兼容模式；已关闭测试环境的兼容提示，未修改 native 库或产品兼容配置。
