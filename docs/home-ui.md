# 首页 UI 还原与维护说明

## 范围与状态

使用 Kotlin + XML/ViewBinding + Material 原生组件；首页是一个 RecyclerView，GridLayoutManager 负责通栏摘要/统计/标题与双列工具区。没有嵌套 RecyclerView/ScrollView，Network Traffic 已接入真实统计，详见 [模块说明](network-traffic.md)；通知、应用管理与文件清理入口现已接入，首页数字来源见 [入口统计](home-metrics.md)。

Debug 默认展示 Figma 初始状态。**长按顶部应用标题**可切换“未扫描”和“扫描完成”；也可通过启动 Intent 的 `home_preview=initial|scanned` 选择状态。长按菜单与示例数值位于 `src/debug`，Release 使用同名空实现，不接受预览参数。真实数据源仍为空，正式包显示未知值，避免把 199MB 等设计示例伪装成设备结果。

Network Traffic 通过首页入口完成权限和Loading后进入结果页；Smart Cleaning 和工具卡均通过 HomeUiActions 接入对应业务协调器。Debug 默认显示真实数据；设计预览仅在长按标题或显式 Intent 选择时启用，并可切回 Live data；旋转/Activity 重建保存小型模式标记，滚动位置由 RecyclerView 保存。

## Figma 对应

- 文件：`dVsTL6ggoDPXVPcEKg856G`
- 初始首页：[5548:225](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=5548-225)
- 完成首页：[5548:33](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=5548-33)
- 内容按 375dp 页面、16dp 外边距、12dp 圆角、20dp 区块间距、12dp 卡片间距实现。
- 主卡两种文案/数字/单位复用同一个布局；Download、Available、Used 三列统计和七张工具卡完整呈现。
- 稿件进度条实际为 54/140，标注却是 31%。Debug 预览按原稿的条长呈现；正式数据映射使用真实已用容量占比。
- 设计稿状态栏是 iOS 风格，Android 端使用真实系统栏，不绘制假时间、Wi-Fi 或电池图标。触摸目标至少 48dp，设置图标仍保持 24dp。
- 只提供了浅色设计，所以首页使用明确的浅色主题与深色系统栏图标，不擅自生成深色设计。允许后续新增独立的深色稿。

## 资源与字体

`design/figma/home/source/` 保留 Figma 原节点透明 PNG；`reference/` 保留两张参考截图；`assets.json` 记录节点、尺寸、裁切区域、密度、原件 SHA-256 和输出路径。资源不依赖临时 Figma URL。

16 个原版资源转换成 WebP，工具/操作/统计图标与机器人生成 mdpi–xxxhdpi 各密度；渐变背景用小型 nodpi 图片由 ImageView 采样缩放，避免系统将低频渐变提前解码成高分辨率大图。机器人只裁掉完全透明的外部留白，内层机器人、AI 徽标、星星、光圈、阴影均为原稿导出，没有重绘或近似图标。

共 72 个 WebP 变体，约 883 KiB；应用运行时只选择相应密度，不会同时解码所有密度。以 3x 资源估算，首页全部图片 RGBA 像素约 2.4MiB（只计像素，不等于实测 PSS/GPU 或缓存开销）。插画已烘焙，无运行时 blur/shader/无限动画。

为避免 OEM/用户系统字体替换与无效字体别名破坏视觉，内置 [Google Fonts 的 Roboto](https://github.com/google/fonts/tree/main/ofl/roboto)，生成 400/500/600/700/900 静态字重，覆盖 API 24。子集保留 Latin/Latin Extended 与常用标点，其他字符由系统字体回退。字体约 227KiB；完整来源字体与 OFL 许可归档，APK 内附许可证。

```sh
python3 -m venv .venv-assets
.venv-assets/bin/pip install -r tools/requirements-home-assets.txt
.venv-assets/bin/python tools/convert_home_assets.py
.venv-assets/bin/python tools/prepare_home_fonts.py
```

这些脚本仅在开发时运行，不进入 Android 运行路径。新增图标应更新 NODES/manifest 并导出原版，不自行画相似图。

## 生命周期、布局与性能

- HomeRenderer 管理 UI；HomeListAdapter 用 DiffUtil 只更新有差异的行；HomeGridSpacing 集中处理间距。ViewModel 与 Repository 不依赖页面或预览。
- 摘要仍由 repeatOnLifecycle(STARTED) 收集；预览没有定时器、协程任务、测速或常驻线程。
- 图片/字体均为本地小型资源，没有网络加载、全局 Bitmap 缓存或运行时格式转换。正常首页没有循环动画；定值 ProgressBar 更新不播放进度动画，列表关闭整卡刷新动画。
- 小于 320dp 的可用内容宽度，或 fontScale > 1.2，工具改为单列，主卡插画移到文本下方，统计信息纵向排列。宽屏内容最大 600dp，长文案自然换行，不靠缩小字号掩盖溢出。
- 系统栏/刘海 Insets 与页面背景分层处理；每个可点击工具的目标是整张卡，箭头/装饰图不独立获取无障碍焦点。
- UI state 只缓存小摘要；Activity 销毁时断开 adapter，ViewModel/应用容器不持有 View、Binding 或 Bitmap。

## 验证方式

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

HomeLifecycleTest 验证首次展示、重建、STOPPED 后返回、完成预览保存、点击不改变业务状态及末尾工具可到达。HomeRenderingTest 在 Android 平台上离屏渲染 375dp 两态、360dp 常规/1.2x 字体、320dp 窄屏、2x 字体及 640dp 宽屏，不改真机全局显示设置。输出在测试应用的 `cache/home-rendering/`；Gradle 测试结束可能卸载测试应用，需要保留截图时可手动安装并运行 instrumentation 后用 `adb exec-out run-as` 导出。

这些测试是功能/布局验证，不能证明真实扫描时的 ANR/OOM/耗电表现。后续接入业务仍需测量冷启动、峰值内存、帧时间、取消延迟和耗电，并补充低版本设备及真实进程回收测试。

## 本轮验证记录

- 13 个单元测试；5 个 Android 15 真机测试（其中渲染测试覆盖七种配置）。
- 顶部数字使用自然高度和 baseline 约束；渲染测试自动断言文字布局高度不超过 TextView、数字与单位基线一致。
- 已人工核对两态完整截图、320dp 窄屏、2x 字号和 640dp 宽屏，截图位于 app/build/reports/home-ui/。
- Gradle Debug/Release 构建、Lint 与回归检查；模拟器由于宿主虚拟化不可用未完成启动，API 24/37 尚未进行设备验证。

## 工具卡标题对齐优化

七张工具卡统一使用完整宽度的标题，箭头移到副标题行右侧，保留 14sp 标题、不省略文案、不按单个标题缩字。所有卡片共享自然测量高度、图标间距与标题/副标题约束。

普通 360dp/375dp 屏幕保持双列。页面创建时按实际 Roboto 字体与系统字号测量最长标题，若双列内容宽度不足，整组工具改为单列；这项判断独立于主卡/统计条的上下排布规则。测量只执行一次，不做逐帧测量或监听循环。

渲染测试检查七张卡片：完整单行标题、无省略号、卡片等高、标题和副标题的相对基线相同、箭头不与标题重叠。截图新增 phone_360.png、phone_360_font_120.png。
