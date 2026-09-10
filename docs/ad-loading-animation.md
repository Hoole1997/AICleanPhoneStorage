# 广告加载 Lottie

本次按用户要求原创制作，使用 `animate` / `frontend-design` 技能，延续 `.impeccable.md` 中的蓝色、浅色和柔和动效。该加载动画不是 Figma 图标的近似替代；关闭按钮继续使用项目原有 WebP。

- 资源：`app/src/main/res/raw/ad_loading_tiles.json`，144 × 72 画布，1.5 秒循环，声明帧率 60，原始 JSON 7292 字节。
- 重建：`python3 tools/generate_ad_loading_lottie.py`。三个圆角方块只改变位移、缩放与透明度，循环端点及关键帧速度连续；没有图片、字体、遮罩、模糊或外部资源。
- 显示：`AdLoadingAnimationView`，96 × 48dp；弹框根布局使用 ConstraintLayout 的最大宽度 248dp，窄窗口继续服从父级测量约束。关闭按钮触控区维持 48dp，文案自然换行。
- 生命周期：窗口不可见、失去焦点、宿主暂停或 SDK 关闭弹框时停止动画并注销系统偏好监听；恢复后继续。关闭系统动画、省电或开启触控探索时显示静态帧。
- SDK：`onReady` 不等待 JSON 加载或动画播完；`findCloseView` 提供原关闭按钮，由 SDK 绑定关闭事件；renderer 不保存 Activity/View。广告请求和业务回调时机不变。
- 播放库：Lottie Android 6.7.1，参见 [官方版本](https://github.com/airbnb/lottie-android/releases/tag/v6.7.1) 和 [官方 AnimationView 源码](https://github.com/airbnb/lottie-android/blob/v6.7.1/lottie/src/main/java/com/airbnb/lottie/LottieAnimationView.java)。

`AdLoadingAnimationDeviceTest` 使用 SDK 的实际加载弹框核验尺寸、动画解析和生命周期，导出 Android 原生绘制的帧与大字体截图。系统动画设置测试仅在模拟器上执行，结束后恢复原值；不请求真实广告。

验证结果：local/google Debug 构建和 local Lint 通过。API 32 模拟器的两项新检查通过，确认实际 SDK 内容宽 248dp、200dp 窄约束下双倍字号无裁切、关闭按钮真实触摸可关闭、后台暂停/恢复及系统动画开关有效。另在 1080×1440 小屏窗口通过首页机器人后台及滚出视区回归；原 1080×2340 长屏未满足旧测试“滚到末项就看不到机器人”的前提。已导出 36 帧原生渲染预览，帧采样仅作为模拟器诊断，不据此声明真机恒定 60fps。
