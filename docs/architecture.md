# Android 清理 APP 基础架构

## 当前结论

采用 **单 app 模块 + Kotlin + XML/ViewBinding + 轻量 MVVM + Coroutines/StateFlow + 手动依赖注入**。

```text
app/                    Application、Activity、AppContainer（唯一依赖组装入口）
core/coroutines/        可注入调度器、共享的受限并发任务执行器
core/diagnostics/       Debug StrictMode / Release 空实现（按 source set 隔离）
feature/home/
  ui/                   HomeViewModel、HomeUiState、纯渲染函数
  data/                 Repository 契约、摘要模型、明确的空实现
```

依赖方向：`Activity → ViewModel → Repository`；数据通过 `Flow → StateFlow → renderHome` 返回。UI 不访问平台存储，Repository 不依赖页面。数据实现后续依赖 `core`，由 `AppContainer` 注入。

继续使用项目已有的 AppCompat/Material Views，减少当前迁移成本。架构本身不绑定 XML；以后可把渲染层替换为 Compose。当前没有通用 BaseActivity/BaseViewModel、事件总线、Hilt、路由框架、UseCase 空壳或多模块拆分。只有多页面复用的复杂规则出现时才提取 UseCase；只有独立构建/复用/多人协作需要时才拆模块。后续多页面可以使用单 Activity + Navigation/Fragment，Fragment 的 Binding 必须在 onDestroyView 清空。

## Figma 如何影响设计

- [初始首页 5548:225](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=5548-225)：主卡显示 Storage Used、已用/总容量与百分比。
- [扫描完成 5548:33](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=5548-33)：同一主卡改为 Scan Complete、垃圾字节数，存储信息与工具区继续复用。
- `HomeOverview.storage` 独立于 `scan`。首次进入也可以有容量摘要；没有容量数据用 null，不伪装为 0。
- `ScanSummary.NotScanned` 与 `Completed(junkBytes, completedAtEpochMillis)` 明确分开。完成且 0 字节仍是已扫描。存储/文件字节数使用 Long，不把格式化后的 MB/GB 字符串存入数据层。
- 加载摘要的 `Loading/Ready/Failure` 与“扫描阶段”是不同维度；未来接入扫描时再增加运行中、取消、失败等任务状态，不把读取摘要等同于扫描。
- 设计稿中的 40.3GB、199MB、637B/S、工具区 12.5GB 是设计样例，未当作设备数据。通知数、网络流量、文件大小将使用各自正确的单位。

基础架构验证页现已替换为完整首页原生 UI，详见 [首页 UI 说明](home-ui.md)。Debug 通过独立样例支持两种 Figma 状态预览；Release 的 `EmptyHomeOverviewRepository` 仍返回未知容量与未扫描状态。没有扫描/清理、权限申请、测速或后台服务，后续业务替换容器中的数据实现。

## 已落地的性能边界

| 关注点 | 当前机制 | 能力边界 |
| --- | --- | --- |
| ANR | TaskExecutor 用 withContext 切换线程；I/O 默认最多 2 个，计算最多 1 个；等待许可不阻塞线程 | suspend 本身不代表后台线程。所有耗时数据实现必须主动使用执行器，不能在主线程调用阻塞 API |
| 取消与资源 | 执行器不创建独立 Scope/Job，异常和取消透传，withPermit 自动释放许可 | 非协作式阻塞不会被神奇中断；需 use/finally、CancellationSignal 或 runInterruptible |
| OOM | 首页只缓存常量大小摘要；不持有 Bitmap、全量文件清单、Activity/Context | 限并发不是内存上限，也不是等待队列上限；图片和文件处理需另设预算 |
| 功耗 | 启动不扫描；依赖懒创建；没有常驻线程/轮询/定时任务/唤醒锁 | 真实任务频率和电量/温度策略需结合业务实现与实测 |
| 生命周期 | repeatOnLifecycle(STARTED) + WhileSubscribed(0, replayExpiration=0)；无观察者时立即停止上游并清掉旧 UI 缓存 | 只约束这条摘要订阅，不能替代扫描 Job 的显式取消策略 |
| 冷启动 | Application 只装配诊断入口；容器无磁盘和数据库操作 | 首次接入数据库/SDK时必须继续审查初始化路径 |
| 诊断 | Debug 检查主线程磁盘/网络、Activity 泄漏及未关闭资源；Release 不安装 StrictMode；启用 R8 优化 | StrictMode 不是完整的 ANR/泄漏/OOM 监控工具 |

### 生命周期必须区分三件事

1. **页面观察**：页面进入 STOPPED 后取消摘要观察；回到 STARTED 时重读。超时为 0，优先节电，因此旋转时也可能重建轻量摘要订阅。真实实现需避免昂贵重读，可读本地持久摘要。`observeOverview()` 必须无扫描等副作用。
2. **用户发起的工作**：`viewModelScope` 跨配置变更存活，仅在 ViewModel 清除时自动取消。退到后台不会自动取消它。后续扫描默认按前台任务设计，显式控制单个 Job、防重入与取消；不要把开始扫描放进 repeatOnLifecycle，否则旋转/回前台会重扫。
3. **进程死亡**：ViewModel 和内存 Flow 不能恢复扫描结果。后续在 Repository 落库保存小摘要/任务记录，重启重读并校验权限、有效期；SavedStateHandle 只保存轻量页面参数/记录 ID，不保存文件列表或位图。当前空实现进程重启恢复“未扫描”，不会谎称恢复正在执行的任务。

错误在功能边界处理：当前仅把 IOException、SecurityException 映射为可展示状态，提供失败重试。Flow 的取消继续向上传播；不 catch Throwable，不吞掉取消，不尝试 catch OOM 后继续。编程错误保留崩溃信息。日志不输出用户文件路径和敏感信息。

## 后续数据实现约定

- **线程与背压**：通过构造函数注入同一个 TaskExecutor。每次顶层 I/O/计算操作取得一次许可，不嵌套调用受限入口（否则可能死锁）。按批次生产任务，绝不 `files.map { async { ... } }` 启动海量协程。需要流水线时使用有界 Channel，队列容量以实测决定。
- **资源释放**：Cursor/InputStream/OutputStream 使用 `use`；callbackFlow 在 awaitClose 注销回调；CPU 循环定期 ensureActive。MediaStore 等支持 CancellationSignal 的 API 要接上协程取消。不要用全局锁串住主线程；超时不能中断不支持取消的系统调用。
- **文件与图片**：分页查询元数据，明细进 Room/Paging，不把全量文件列表塞进 StateFlow、Bundle 或 Intent；打开/删除前重新校验 URI 与访问权限。按展示尺寸采样解码图片，禁止 readBytes 读取整个大文件，压缩流式执行。未来图片加载优先使用成熟库和有限内存缓存，不先造全局缓存。
- **内存预算**：并发 2/1 是保守起点而非测量结论；图片压缩的内存取决于解码尺寸及中间 Bitmap。高分辨率图即使串行也可能 OOM，必须按尺寸与设备预算降采样。缓存接入后才实现对应 trim/后台释放策略，不能用 System.gc 或 largeHeap 代替设计。
- **进度与网络速率**：扫描进度合并更新（建议起点 200–500ms，终态立即发出），不每发现一个文件就刷新整个首页。网络速率仅页面可见时低频采样，取消订阅即停止，不为展示网速保活进程。
- **后台任务**：确有跨页面/进程存活的业务需求时再引入 WorkManager，并使用唯一任务、适当约束、有限重试与退避。WorkManager 不保证立即执行；长任务须结合目标 Android 版本的前台服务类型、启动和时长限制。用户主动清理不能在自动重试中重复删除。
- **安全操作**：扫描阶段只读；删除/压缩覆盖等写操作必须在后续交互中提供明确选择与确认，操作前重新校验，不让陈旧摘要直接驱动删除。

Android 清理能力受系统沙盒和分区存储限制，不能承诺清除其他应用私有缓存或任意结束其他应用。后续按 MediaStore/SAF 与系统授权设计；本轮 Manifest 未添加存储、通知监听、全文件访问或电池优化豁免权限。

## 运行与验证

保持原项目 AGP 9.3.2、Gradle 9.5.0、compile/target SDK 37、min SDK 24；AGP 9 内置 Kotlin，不重复添加 kotlin-android 插件。新增依赖版本集中在 gradle/libs.versions.toml。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
# 连接专门的测试设备后执行（会安装 app 与测试包）：
./gradlew :app:connectedDebugAndroidTest
```

单元测试重点：摘要边界/0 垃圾状态、无人订阅不读取、取消订阅释放上游、重新订阅、ViewModel 清除、错误恢复、执行器并发上限及等待/运行取消后许可释放。设备测试覆盖启动、Activity 重建、前后台往返。

真正接入业务后再建立性能基线：Debug StrictMode/内存分析、Release Perfetto 与 Macrobenchmark、低内存设备、超大媒体库、旋转/退后台/权限撤销/进程回收、连续扫描取消。记录冷启动、帧耗时、峰值 PSS、取消延迟及任务耗电；架构和编译通过不能替代这些测量。

## 本轮验证结果（2026-09-07）

- Debug APK 与启用 R8 的 Release APK 构建成功；Release 为未签名构建产物，不代表已发布。
- 11 个单元测试通过，覆盖并发、取消、异常许可释放、摘要边界、订阅生命周期和失败重试。
- Android 15 已连接设备上 2 个仪器测试通过，覆盖首次启动、Activity 重建、STOPPED 后恢复。
- Lint：0 errors、2 warnings；两项均为原项目 Gradle/AGP 版本更新提示，本轮保持原工具链。
- 尚未验证 API 24/37 设备、真实进程回收恢复及扫描/压缩性能，当前没有这些业务实现或性能基线。

## 官方参考

- [Android 架构建议（Views）](https://developer.android.com/topic/architecture/views/recommendations-views)
- [协程最佳实践：main-safe、调度器注入与取消](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [保持应用响应、避免 ANR](https://developer.android.com/topic/performance/anrs/keep-your-app-responsive)
- [应用内存管理](https://developer.android.com/topic/performance/memory/manage-app-memory)
- [后台任务选择](https://developer.android.com/develop/background-work/background-tasks)
