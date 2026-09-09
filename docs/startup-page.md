# 启动页与正式 Logo

- 启动页 Figma：`dVsTL6ggoDPXVPcEKg856G`，节点 `5619:3118`，背景原图 `5619:3119`。
- 正式 Logo：直角 `5619:3150`，圆角 `5619:3156`。原始导出与来源说明在 `design/figma/app-logo/`。
- 独立 `StartupActivity` 为唯一 Launcher；AndroidX SplashScreen 1.2.0 衔接系统启动画面。
- 常驻通知 PendingIntent 指向启动页；notification 模块使用系统 Launcher 解析，因此普通通知自动进入启动页，无需修改模块逻辑。
- 旧版本仍指向 MainActivity 的通知也会先转启动页。启动页只传递白名单目标，结束后进入首页，再由首页执行既有权限和业务跳转。
- 当前没有广告 SDK 或广告加载逻辑。无广告阶段约 3 秒过渡，等待准备的硬上限为 15 秒。返回或后台停止动画与等待任务，重新进入时依据单调时钟处理超时；旋转和连续通知不会延长当前周期。
- 退出启动页后移除其 Activity，返回键不会重放启动页；只有恢复可见且取得窗口焦点后才转交首页。
- 背景为开发时转换的五档 WebP，最多 852×1846 像素，解码放在 IO。文字与进度条为原生组件，支持大字体、短屏、横屏和系统栏 inset。
- Logo 使用正式 Figma 导出生成密度资源。圆角导出的灰色画布角被按 Figma 的 106px 裁切恢复透明度。桌面自适应图标保留原图安全边距；SplashScreen、启动页和关于页面均使用正式 Logo。

转换脚本：`tools/convert_startup_assets.py`（背景）、`tools/convert_app_logo_assets.py`（正式 Logo）。使用 Pillow，无运行时图片转换。

验证用例：`StartupViewModelTest`（时限、后台取消、恢复、目标消费），`StartupNavigationTest`（Launcher、白名单、普通/常驻/旧通知路由），`StartupRenderingTest`（Figma 尺寸、大小字体、横屏），`StartupActivityFlowTest`（需解锁设备，验证冷/热/旧通知及返回栈）。
