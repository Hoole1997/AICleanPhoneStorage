# Network Traffic 初始实现方案（历史记录）

当前实现与后续用户调整以 [network-traffic.md](network-traffic.md) 为准（首页先授权/Loading、页面内无弹窗、入口进度与2–4秒随机展示窗口同步）。

本轮先提交已有首页：`47ae9fe`（feat: 还原首页双状态 UI 与原版 WebP 资源），再分析设计；尚未添加功能代码、权限或广告 SDK。

## 设计解读与建议口径

- [通用 Loading：5583:3756](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=5583-3756)：遮罩、圆角白色容器、关闭按钮、旋转指示、标题/说明、进度条/百分比、底部广告位。参考主体为 315×281；Android 端限定宽度、自适应高度，支持字号和屏幕 Insets。
- [Network Traffic：5583:3610](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=5583-3610)：返回栏、三个时间筛选、Mobile/Wi-Fi 总量、Apps 列表、应用流量及占比、行尾按钮。
- 页面属于历史累计流量统计，不是实时网速或主动测速，不需要网络下载、VPN、轮询、后台服务或 WorkManager。

两处设计语义需要在实施前定稿，目前建议如下，尚未修改设计或实现行为：

| 项目 | 原稿 | 建议 |
| --- | --- | --- |
| 时间筛选 | This Month / This Month / 24 Hours | This Month / Last Month / 24 Hours |
| 行尾按钮 | Stop | Manage：打开该应用的系统详情页，由用户在系统页面管理 |
| Apps 流量 | 未标明口径 | 当前时间范围的移动 + Wi-Fi，上传 + 下载，按总字节数降序 |
| 应用进度条 | 未说明分母 | 当前应用 / 当前用户可查询的移动与 Wi-Fi 总量；全为 0 时不除零 |
| Loading 说明 | 文件路径 | 当前阶段，例如 Reading mobile usage / Reading Wi-Fi usage / Loading app details |
| 统计范围 | 未标明用户范围 | 当前 Android 用户/配置文件；不宣称覆盖所有用户和工作资料 |

Android 14 起，第三方应用通过 killBackgroundProcesses 只能结束自身进程；这也不是强制停止其他应用的公共接口。因此不能做“点 Stop 就停止其他 App”的假功能。建议使用 ACTION_APPLICATION_DETAILS_SETTINGS，包已卸载或系统不支持时展示可恢复提示，不宣称已停止。[ActivityManager](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String))、[Settings](https://developer.android.com/reference/android/provider/Settings#ACTION_APPLICATION_DETAILS_SETTINGS)

## 最小架构改动

沿用现有单模块、ViewBinding、ViewModel/StateFlow、Repository 与 AppContainer，不引入通用 BaseActivity、事件总线或庞大路由框架。

```text
core/ui/loading/
  TaskLoadingDialogFragment       纯展示通用弹窗
  LoadingUiState                  标题、说明、进度、能否取消、广告位状态
  AdPlaceholderView               可替换的静态广告槽位

feature/networktraffic/
  ui/
    NetworkTrafficActivity        窗口、权限/系统页面跳转、生命周期
    NetworkTrafficViewModel       筛选、查询任务、错误/取消/结果状态
    NetworkTrafficRenderer        页面渲染、Dialog 显隐、列表提交
    TrafficAppsAdapter            DiffUtil + 按需图标绑定
  data/
    NetworkTrafficRepository      一个功能边界，组合系统数据源与小型缓存
    AndroidTrafficDataSource      系统流量统计查询与资源关闭
    AppMetadataDataSource         UID → 包名/名称，按需取得应用图标
    UsageAccessChecker            使用情况访问权检查
    TrafficPeriodResolver         时间区间计算，可注入时钟和时区
    TrafficSnapshot               轻量应用汇总，不携带 Bitmap/Drawable
```

优先独立的 NetworkTrafficActivity：当前首页直接由 MainActivity 渲染，没有 Fragment 导航框架。这样无需先迁移整个首页。首页只将 Network 动作接到 Intent，新 Activity 内由同一个 ViewModel 持有 Loading 到结果的全过程，避免首页查询后通过 Intent 传大列表，或在目的页重复查询。列表在数据就绪后展示。

## 用户流程

1. 点击 Network Traffic，创建模块页面并检查使用情况访问权；重复点击只能启动一次。
2. 已授权：立即展示通用 Loading，开始一次查询。未授权：显示权限说明与“去设置”入口，不能一直停留在 Scanning。
3. 从系统设置返回后重新检查实际授权状态，不相信 Activity resultCode；拒绝可返回，不能自动反复拉起授权页。
4. 查询完成：同一状态更新关闭 Loading、展示结果；空数据与权限不足/系统错误分别处理。
5. 初次 Loading 的关闭键/返回键：取消请求并回到首页；不允许关闭后查询完成又自动打开结果页面。
6. 切换时间范围：只保留最新请求；可以复用 Loading。取消筛选刷新时保留上一份完整结果和它对应的筛选标签，避免新标签配旧数据。
7. 点击 Manage 跳转系统应用详情；返回后检查权限/安装状态，历史流量不因为用户停止应用就清零。

## 通用 Loading 的边界

- 用 DialogFragment 管理窗口/旋转恢复；不做静态 Dialog 单例，不保存 Activity 引用。固定 tag 防止重复弹出，FragmentManager 已保存状态时延后到可安全展示的生命周期，不用 commitAllowingStateLoss 掩盖问题。
- 参数化 title、message、progress（未知或已知）、cancellable、adSlot。弹窗不直接访问 Repository，不拥有扫描任务；关闭结果通过 Fragment Result 等生命周期安全机制反馈给宿主，携带 requestId，避免旧弹窗取消新任务。
- 支持未知进度和真实计数进度。系统查询期间无法知道已完成字节数，不用定时器伪造 0–99%；拿到需要解析的 UID 总数后，可以按真实处理数量显示百分比。完成态才是 100%。
- 旋转图形使用原版资源或合适的原生指示器；可见时运转，STOPPED 时停止。进度合并更新约 200ms，终态即时更新，不每个 bucket 都触发 UI 重绘。
- 广告区域先封装静态槽位，保留稿件布局；不接广告 SDK、不请求网络、不触发安装/点击、不为了广告人为延迟任务。真实广告以后只替换槽位内容与生命周期实现。
- 高度按文本自然测量，关闭按钮触摸区域至少 48dp；百分比独立宽度，说明最多合理行数，不把路径强行塞进固定高度。

## 数据、权限和兼容性

系统数据使用 NetworkStatsManager 的时间范围汇总接口，在后台线程查询。使用情况访问权通过系统设置由用户授予；没有授权不能将其他应用统计显示为 0。[NetworkStatsManager](https://developer.android.com/reference/android/app/usage/NetworkStatsManager)、[授权设置页](https://developer.android.com/reference/android/provider/Settings#ACTION_USAGE_ACCESS_SETTINGS)

计划实现：

- 明确时间窗口：本月 = 本地时区月初至当前；上月 = 上月月初至本月月初；24 Hours = 当前时间回溯真实 24 小时。每次请求固定一个 now，不用“30 天”代替自然月。API 24/25 使用 core library desugaring 或兼容时间 API。
- 每个网络类型获取汇总，按 UID 合并不同状态/计费/漫游维度的 rxBytes + txBytes；不为每个应用单独发起一轮历史查询。统计用 Long，比例用 Double，异常负值/不可用结果单独处理。
- 以当前用户范围保持顶部总量和列表口径一致。能够取得但无法解析包名的 UID、共享 UID、卸载应用和系统条目明确标识，不重复把同一 UID 的用量记到多个应用。可见应用列表的和不保证覆盖全部系统统计，不能杜撰差额到具体应用。
- 历史统计有系统记录粒度和更新延迟，页面说明为系统统计；不能承诺运营商账单级准确度，也不展示毫秒级“实时流量”。
- API 29+ 移动网络查询使用 null subscriberId 取得全部移动网络汇总，Wi-Fi 也按汇总查询，不读取 IMSI。API 24–28 单独实现/验证兼容路径：如确实需要 subscriberId，仅在旧版本按需申请 READ_PHONE_STATE（maxSdkVersion=28）并临时用于查询，不落盘/不日志输出。用户拒绝、无 SIM 或不支持时 Mobile 标记不可用，Wi-Fi 仍可展示，不降低 minSdk 或伪造 0。[subscriberId 限制](https://developer.android.com/reference/android/telephony/TelephonyManager#getSubscriberId())
- Android 11+ 包可见性有过滤：优先通过限定的 queries 声明可启动应用，无法解析的 UID 保留明确的不可识别状态。不能默认用 QUERY_ALL_PACKAGES 绕过边界，也不能承诺列出所有隐藏/其他用户应用。[包可见性](https://developer.android.com/training/package-visibility/declaring)
- 真实应用名称/图标来自 PackageManager；Figma 中的七个 App 图标只是设计样例，不当作真实安装应用。返回、关闭、移动网络、Wi-Fi 等固定 UI 图标仍按项目规则导出原版 WebP。

## ANR / OOM / 功耗 / 生命周期

- ANR：统计服务、PackageManager 元数据与图标读取离开主线程；继续使用注入的 TaskExecutor。该模块采用一个在途统计请求，快速切换只保留最新待执行请求，避免 Binder 查询堆积。
- 取消：NetworkStatsManager 的阻塞调用不保证能被协程立即中断。UI 取消立即生效；底层返回后检查取消/requestId，丢弃旧结果并在 finally 关闭 NetworkStats。超时不是强杀线程，不同时堆积新的阻塞请求，不吞 CancellationException。
- OOM：NetworkStats bucket 边读边聚合成 UID 摘要，不复制所有原始记录；列表只存 UID/包名/名称/Long 数值。应用图标按可见项在后台加载成约 42dp 的位图，用按字节计费的小 LruCache（起点上限 2MiB，实测后调整），列表复用时取消旧图标请求并校验绑定 ID。不能在 onBindViewHolder 同步调用 PackageManager.getApplicationIcon。
- 功耗：历史用量查询只在进入、用户筛选/刷新、权限变更等事件触发；可短时复用同区间快照，不加秒级轮询、广播常驻监听、唤醒锁或后台保活。缓存包含时间窗口、查询时刻、授权状态，不能用旧权限下的数据冒充新结果。
- 生命周期：旋转保留 ViewModel 中的查询，不重复执行；非配置变更的退后台取消未完成工作，回来显示可重试状态。查询完成时仅当前有效请求可以更新 UI，后台不弹窗/导航。页面销毁时取消图标任务、释放 dialog/view 引用；广告槽无独立常驻任务。
- 进程回收：SavedStateHandle 只存时间筛选/请求参数，不存应用列表或图标。重建重新检查权限和读取，不能恢复成“还在扫描 34%”的假状态。首版不需要数据库，缓存仅为小型内存快照。

## 实施顺序与验收

1. 定稿筛选和 Stop 的产品语义；封装通用 Loading/广告占位及取消契约。
2. 实现 Network Traffic 原生布局、状态与导航，Figma 原版固定图标转 WebP。
3. 接入使用情况访问授权、时间范围、移动/Wi-Fi 汇总、UID/应用映射、排序和按需图标。
4. 接入取消、防重复点击/过期响应、系统详情入口及边界状态。
5. 测试授权拒绝/返回/撤销、无 SIM、零流量/不可用、共享/未知 UID、时间边界/夏令时、快速筛选、旋转、后台/进程回收、关闭 Loading 后不得跳页；验证应用名过长时金额/按钮不会被挤掉，常规/大字号不裁切。
6. 运行编译、Lint、单元/设备测试与两份 Figma 截图核验。用慢系统查询和大量 UID/图标验证主线程耗时、峰值内存、取消后行为；不给未经实测的 ANR/OOM/耗电保证。
