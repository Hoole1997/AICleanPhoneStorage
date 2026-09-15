# 文件清理模块

四个首页入口共用 `feature/filecleaner`。保持单模块 Kotlin + XML/ViewBinding + ViewModel/StateFlow，不增加后台常驻服务。

## 入口与权限

- 首页先申请所需访问权，再扫描并显示通用 Loading，最后打开结果页面。沿用 Network Traffic 的 2–4 秒随机展示窗口，文件计数和百分比共用时间轴；扫描未完成时不会提前导航。广告仍为占位。
- Photo Compress / Screenshots：MediaStore 照片查询，支持 Android 14+ 部分照片授权并标明范围。已有所有文件访问权时也使用 MediaStore 查询照片。
- Large Files / Unused Files：Android 11+ 可直接打开本应用的所有文件访问设置；也可通过 SAF 选择可写目录。旧版本使用目录授权。不会访问其他应用的私有目录。
- 权限拒绝不会进入结果页或启动清理。目录授权持久化；索引和设备授权记录排除在系统备份之外。

## 分层与共享逻辑

- `CleanupAccess`：决定媒体、共享存储、目录三种来源的授权策略。
- `FileScanSources`：可取消的只读元数据扫描；不跟随符号链接，目录查询连接 CancellationSignal。
- `FileScanRepository` / `ScanIndex`：200 条批量写 SQLite；Paging 每页 60 条、初始 120 条、最多驻留 240 条元数据。目录遍历队列和选中项都放在磁盘索引，不放入 Bundle 或全量 StateFlow。
- `CleanupPolicy`：文件分类和候选规则。Large Files 默认至少 10 MB；类型/大小/时间筛选只查询索引。
- `CleanupViewModel`：筛选、选择、确认、操作和恢复状态。选择通过 Diff payload 更新，图片不因勾选重新加载。
- `FileOperationEngine`：冻结当前筛选中选中的文件集合，逐项验证并执行；确认后再发生的筛选/选择变化不会改变操作集合。
- `CleanupThumbnailLoader`：采样解码、EXIF 方向、按字节限制缓存；页面不可见时取消请求并释放缓存。

## 业务规则

Unused Files 按 2026-09-15 用户确认使用三类代理规则：包名已安装的 APK、共享 Android/data 与 Android/obb 中可确认包已卸载的残留、Download 下超过 30 天未修改的文档/压缩包。时间未知、正好 30 天、旧媒体、Download 以外的旧文档不纳入下载候选；不声称知道文件最后打开时间。三个功能独立筛选，同一文件可跨入口出现。Screenshots 根据截图目录或文件名识别，部分厂商自定义命名可能无法识别。

闲置概览展示三分类，进入详情沿用 Paging 和共享选择；返回刷新总量。新扫描默认全选，旧版无分类候选不进入展示和删除快照。unused_scan_result 上报 installed_apk_size、residue_size、download_size，单位为十进制 MB；分组点击和选择分别上报 unused_group_click、unused_check，group 为 installed_apk/residue/download。埋点文档的旧 90 天表述由用户最新 30 天规则覆盖。

Android 11+ 的包查询有可见性过滤，且共享 Android/data/obb 受系统访问限制；不将 NameNotFound 当作卸载，也不新增 QUERY_ALL_PACKAGES 或绕过目录限制。旧系统仅在用户已授权且可访问时处理残留。SAF 的下载/残留路径只认系统 ExternalStorageProvider 的卷内路径，其他不透明提供者不猜目录归属。SAF APK 串行流式复制至临时文件供 PackageManager 解析，单个上限 128 MiB，结束或取消删除临时副本。

删除闲置项前重新判断包状态和文件资格。残留内容按选中快照先文件后目录逐项处理，目录仅为空时删除；扫描后新增或取消选择的内容不会被递归连带删除。

Photo Compress 当前支持 JPEG 和静态 PNG，提供 JPEG 质量 60/75/85。按约 2 MP 和最长边 2048px 的预算采样，低内存设备进一步降低预算；透明区域转为白色。界面节省量是按质量档位计算的粗略估计，实际以编码结果为准；输出不小于原图时跳过。

压缩先在缓存中编码，再写入 `Pictures/AIClean/Compressed/`，重新读取副本并校验 SHA-256 后发布。不会覆盖原图。完成后用户可以保留原图，或再次确认删除原图；删除前重新验证副本的 SHA-256，副本丢失/被修改时拒绝删除原图。副本保留期间会额外占用存储，释放空间要在删除原图之后发生。进程被系统终止时不自动恢复破坏性操作；尚未发布的 MediaStore 输出由系统回收，已发布的副本保留。

删除前核对文件范围、大小和修改时间；无法访问或已变化的条目计为失败。Android 11+ MediaStore 删除使用系统确认，每批最多 200 个 URI；Android 10 处理 RecoverableSecurityException；直接文件和 SAF 目录在本应用确认后删除。删除不可恢复，默认没有预选文件。

## 生命周期与性能边界

磁盘/Provider/编码工作使用已有 TaskExecutor 的有界工作线程。压缩逐张进行，不缓存原尺寸图片；页面退到后台会取消当前扫描/操作。旋转保留 ViewModel；进程重建只恢复小型扫描 ID、筛选、系统确认标记和结果，不自动重启删除。临时扫描索引保留三天，之后在下次扫描时清理。

这不是性能跑分报告：已验证分页、取消、页面重建和文件保护行为；超大图库、慢速云端 SAF、不同 OEM 权限页、低端机峰值内存与耗电仍应在发布前专项测试。

## 设计与验证

Figma 节点：Loading 5603:1057；Photo 5603:684/842；Large 5603:1301/1429/1907；Unused 5603:2162/2609；Screenshots 5603:2276/2620。

勾选、展开、箭头均从原 Figma 节点导出；`design/figma/file-cleaner/assets.json` 记录源节点、PNG 校验值和五档密度 WebP。缩略图来自实际文件；非图片复用原 Figma 文件夹图标。确认框为原生 DialogFragment，文字高度自然测量。

验证命令：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --max-workers=1
adb shell am instrument -w -e class com.example.aicleanphonestorage.CleanupFeatureDeviceTest com.example.aicleanphonestorage.test/androidx.test.runner.AndroidJUnitRunner
```

设备测试只创建/处理测试专属文件，不修改系统权限、不删除用户文件。覆盖分页及选择快照、变化/越界文件拒绝删除、压缩副本验证且原图不变、副本丢失保护、扫描目录排除及取消、四页选择/确认/旋转及截图。系统权限确认与不同厂商设置路由仍需手动验收。

平台依据：[Android 共享媒体访问](https://developer.android.com/training/data-storage/shared/media)、[所有文件访问](https://developer.android.com/training/data-storage/manage-all-files)。所有文件访问权限的商店申报须与文件管理/清理用途对应。

2026-09-07 验证记录：Debug 构建、41 项单元测试、Lint（0 错误）通过；真机五项文件操作/扫描测试通过，四页 UI/旋转测试在前轮通过，最后回归因设备锁屏未执行。UI 测试明确要求设备已解锁。

## 连续扫描 Loading

文件扫描现在统一使用 `ContinuousEntryProgress`：从第一帧 0% 开始，整个请求只建立一次时间轴，阶段切换不切换 indeterminate 模式、不回退。Smart Cleaning 的文件枚举与照片分析共用总体进度；Unused Files 及其他文件入口使用单阶段进度。

文件总数未知时，百分比是阶段工作量的展示估计，并非精确的文件完成比例；无需为了统计总数再扫描一遍。前 90% 留给工作阶段，结果真正准备完成才解锁最后 10%。随机展示窗口会延后进度及计数；慢扫描完成后用 300ms 收尾并短暂显示 100%，不提前进入结果页。

## 压缩品质弹框

照片列表的压缩条件使用 `CompressionQualityDialog`，与统一权限弹框保持白色圆角卡片、蓝色选中态和原版 WebP 图标。三档品质仍为 60/75/85，用户文案以体积与细节的取舍解释，不承诺固定压缩比例。选择即应用，点击当前选项或取消仅关闭。

通过 Fragment Result 回传文件 ID 和品质；Activity 重建后仍可接收，初始化回显不会触发保存。弹框不解码缩略图、不持有 Activity 回调、不启动扫描或压缩。结果使用原有 IO 写入、索引刷新和列表 Diff 流程，只更新对应照片；文字自然测量，小屏/大字体可滚动。

按用户反馈，品质弹框采用紧凑布局：宽 288dp，普通字号约 300dp 高，18sp 标题、56dp 最小选项高度和右侧勾选标记；去除大图标和长说明。保留大字体下的自然测量与滚动。

## 截图总量与清理按钮

截图页标题下显示整份扫描结果的“截图总数 · 总大小”，不随分页或勾选改变；清理后随剩余索引更新。未完成统计时隐藏副标题，避免闪现假的零结果。未勾选时按钮为灰色且不可点击；有选择时显示“清理（已选大小）”，仅在选择写入完成且没有正在执行的操作时可点击。勾选写入期间保持按钮外观稳定，只暂时拦截点击；按钮文案仅在容量变化时更新，不重设背景与颜色。容量固定 MB、一位小数，计数/文案随应用语言格式化。

照片压缩按钮与截图按钮共用 `CleanupActionRenderer`：无选择时灰色禁用，有选择时显示本地化的 `Compress (N)`，N 为索引中的完整已选数量。数量不变时不重设文案，选择写入期间只拦截点击，避免灰/蓝切换；点击后冻结已选项，直接进入既有广告/压缩流程，不再显示压缩前确认弹框；对应多语言文案和翻译覆盖项已删除。副本验证、取消和单独删除原图的确认流程保留。

## 压缩大小口径

压缩页顶部显示已选原图的实际大小，卡片徽标显示品质选项；已删除以 `size × (100-quality) / 100` 估算节省量的逻辑和文案。质量参数只是 JPEG 编码参数，不能直接换算为体积减少比例。

完成页从操作快照读取已选原图、成功压缩的原图和已验证副本字节数。全成功时展示已选原图、压缩后副本及实际减少量；部分成功时额外列出成功压缩的原图大小。失败/跳过项只计入本次已选输入，不计入成功副本或减少量。所有页面使用相同的 MB 单位与一位小数格式，原始统计保留 Long 字节数，Intent 仅传摘要，页面重建不丢失口径。原图保留期间，副本变小不等于已经释放磁盘空间。
