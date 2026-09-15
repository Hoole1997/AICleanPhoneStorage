# 常驻通知与首页 App 数量统一

## 问题原因

- 首页的数量回退：查询 `ACTION_MAIN + CATEGORY_LAUNCHER`，按包名去重。
- 原常驻通知：`getInstalledApplications(0).size`，包括没有桌面入口的可见安装包；另有独立的 60 秒字符串缓存。

因此即使在同一设备、同一时间，两处口径也不同；安装/卸载或语言切换后还可能读到不同缓存。

## 修复

- `LauncherAppCountSource` 沿用首页原有的桌面应用去重口径，只读包名并在受限 IO 执行，不读取图标、标签或应用存储明细。
- `InstalledAppCountRepository` 在应用级保存唯一 `StateFlow<Int?>`。并发刷新复用刚完成的读取，未知值为 null，不伪装为 0。
- 首页数量模式和常驻通知都通过 AppContainer 注入同一个仓库。首页还会订阅计数变化，不再停留在上次平台摘要内的旧数量。
- 常驻通知构建只读取共享数值、按当前语言格式化，不再自行查询 PackageManager，不再保存独立 60 秒缓存。
- `InstalledAppCountMonitor` 在初始化、回前台和系统安装/卸载/更新事件时刷新；合并事件，不设置轮询定时器。升级的临时移除阶段不发布数量波动。
- 读取失败时清空未知计数，取消不会发布迟到结果。TaskExecutor 的 IO 许可不嵌套获取。

首页在已授权且可读取应用占用时，原来的容量显示保持不变；本次统一的是数量口径。App 管理列表自身的第三方应用筛选不在本次修改范围。

常驻通知的买量/自然用户显示规则、第四入口隐藏规则均不改动。测试仅验证数量和共享数据，不测试通知视图。

## 验证

- local/google Debug 构建通过。
- app 182 项 JVM 单测通过；新增用例覆盖并发共享、连续刷新、失败与真实 0 的区分、取消、容量模式保持。
- app localDebug Lint：0 Error，179 个现有 Warning。
- 设备用例使用真实 PackageManager 结果核对首页口径，并通过独立买量 fixture 检查通知读取同一共享数字，不修改用户真实归因。

- 两项设备数据测试通过：`OK (2 tests)`。
- CPH2723（Android 15）实测：旧 `getInstalledApplications` 方式为 445；共享桌面应用计数为 171；首页 `HomeToolMetric.AppCount(value=171)`。通知数据模型读取同一共享值 171。
- 独立测试将共享值从 12 刷新到 13，通知在主线程读取也立即得到 13，没有独立字符串缓存，也没有主线程 PackageManager 查询。
- 本轮未改变用户归因，未安装/卸载其他应用，未运行通知视图测试。新 local Debug 包已覆盖安装。
