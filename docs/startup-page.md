# 启动页与正式 Logo

- 启动页 Figma：`dVsTL6ggoDPXVPcEKg856G`，节点 `5619:3118`，背景原图 `5619:3119`。
- 正式 Logo：直角 `5619:3150`，圆角 `5619:3156`。原始导出与来源说明在 `design/figma/app-logo/`。
- 独立 `StartupActivity` 为唯一 Launcher；AndroidX SplashScreen 1.2.0 衔接系统启动画面；系统 SplashScreen 图标直接使用与 Manifest 相同的 `@mipmap/ic_launcher`。
- 常驻通知 PendingIntent 指向启动页；notification 模块使用系统 Launcher 解析，因此普通通知自动进入启动页，无需修改模块逻辑。
- 旧版本仍指向 MainActivity 的通知也会先转启动页。启动页只传递白名单目标，结束后进入首页，再由首页执行既有权限和业务跳转。
- `StartupAdCoordinator` 在语言恢复完成、启动页 RESUMED 且取得焦点、绘制首帧后调用 `AdExt.loadSplash(positionName = "startup_splash")`。SDK 控制广告加载、展示及失败；只有 `call` 回调后才能进入首页：进入开屏页不足 3 秒时等待剩余时间，超过 3 秒时回调立即放行；没有独立超时强行跳转。旋转或等待中的新通知不重置本次最短停留，已完成的新周期重新计时。
- 底部使用 Material 原生循环进度条，不填充 0–100、不显示虚构百分比；后台、广告覆盖或失去焦点时停止动画。关闭系统动画/省电/触控探索时保留静态加载段和 Loading 文案。
- `StartupViewModel` 保存请求编号和完成状态，成功/失败回调都放行且只消费一次。旋转不重复请求；等待期间的新通知更新目标；彻底退出后的迟到回调忽略。进程重建保留路由，但重新申请已经丢失的广告请求。广告回调发生在后台时，等页面 RESUMED 且取得焦点才执行首页交接。
- `StartupAdCoordinatorTest` 用假 SDK 验证回调、旋转及后台恢复；`StartupViewModelTest` 验证不会计时自动放行、准备顺序及去重。`StartupActivityFlowTest` 会请求真实开屏广告，需要显式传入 instrumentation 参数 `run_live_ads=true`，不纳入普通自动回归。
- 启动页到首页使用 240ms 原生窗口交叉淡入：首页透明度从 0 到 1，启动页在底层保持完整直到转场结束，避免露出窗口底色。仅影响这一跳转，不修改其他功能页动画；关闭系统动画、省电或触控探索时直接交接。
- 退出启动页后移除其 Activity，返回键不会重放启动页；只有恢复可见且取得窗口焦点后才转交首页。
- 背景为开发时转换的五档 WebP，最多 852×1846 像素，解码放在 IO。文字与进度条为原生组件，支持大字体、短屏、横屏和系统栏 inset。
- Logo 使用正式 Figma 导出生成密度资源。圆角导出的灰色画布角被按 Figma 的 106px 裁切恢复透明度。桌面自适应图标保留原图安全边距；SplashScreen、启动页和关于页面均使用正式 Logo。

转换脚本：`tools/convert_startup_assets.py`（背景）、`tools/convert_app_logo_assets.py`（正式 Logo）。使用 Pillow，无运行时图片转换。

验证用例：`StartupViewModelTest`（回调放行、准备顺序、恢复、目标消费），`StartupAdCoordinatorTest`（模拟广告回调、循环进度和重建），`StartupNavigationTest`（Launcher、白名单、普通/常驻/旧通知路由），`StartupRenderingTest`（Figma 尺寸、大小字体、横屏），`StartupActivityFlowTest`（显式开启真实广告测试后验证冷/热/旧通知及返回栈）。

本次接入通过 local/google Debug 构建、local Lint 和单元测试；API 32 模拟器的协调器、路由、布局共 6 项检查通过，已核验横竖屏及大字体截图。展示流程使用模拟回调，不将其作为真实广告填充验证。
