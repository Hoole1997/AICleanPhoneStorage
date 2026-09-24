# 视频清理（2026-09-18）

需求：[飞书 1. Video Cleaner](https://pic6ktmsyi.feishu.cn/wiki/T4vOwfFKviXZBYkwCgFcH6xmnAc)。
设计：[列表](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb?node-id=5986-538)、[已选状态](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb?node-id=5986-767)、[首页](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb?node-id=6194-1503)。

## 行为

- 首页 Videos 紧跟 Screenshots；首页容量来自最近完成的真实扫描索引，未扫描保持未知。
- 使用公共权限弹框和状态机；Android 11+ 复用所有文件访问设置页，旧系统运行时申请及永久拒绝判定仍使用 XXPermissions 28.3。未授权停留入口；已授权直接进入原有扫描 loading。
- 按 2026-09-18 最新要求，Android 11+ 入口必须拥有 MANAGE_EXTERNAL_STORAGE；复用垃圾清理的所有文件权限说明、系统设置和授权返回流程。仅视频读取/选定媒体权限不再满足入口条件。Android 10 及以下保留 XXPermissions 存储读取申请，Android 8–9 另需写入权限。扫描仍使用 MediaStore.Video。
- 仅扫描 MediaStore.Video；过滤 pending、trashed、零大小以及打不开或无法识别视频轨道的文件。不访问应用私有目录；容器轨道检查不等同于完整播放解码验证。
- 按本地修改月份倒序分组，组内按修改时间倒序。正常字号三列；fontScale ≥ 1.5 时两列，避免小屏容量文案与勾选/播放按钮重叠。
- 新扫描默认全选有效候选；单项、分组和全局选择同步。折叠不改变选择，操作快照包括已选但当前未加载/被折叠的条目。顶部 Cancel 表示取消全选，与 Figma 已选状态一致。
- 点击中央播放按钮通过 ACTION_VIEW 临时只读授权交给设备播放器；无可用播放器/权限失效时沿用错误提示。
- 复用应用内删除确认、空态和完成页。Android 11+ 视频操作在再次确认所有文件访问后直接使用 ContentResolver.delete；没有该权限或 OEM Provider 拒绝直接删除时，才退回系统删除确认。其他模块的系统确认规则不变。完成计数使用真正成功删除的视频数，单位为 Video/Videos。系统取消且没有已删除项时返回列表保留选择。
- Continue 与结果页返回键均结束视频清理流程并返回首页，关闭原视频列表页；返回首页沿用 back_home_video。广告位 native_scanning、native_feature_video、native_result_video 接入现有协调器；填充仍由现有 SDK/远程配置决定。

## 结构与资源

- `VideoMediaScanner`：受限 I/O 执行器内顺序读元数据/容器轨道；Cursor、AssetFileDescriptor 和 MediaExtractor 随项释放。查询接入 CancellationSignal，逐项检查协程取消。
- `VideoIndex`：复用 SQLite files 和选择/操作快照；月份标题与文件一起分页。折叠状态单独按扫描 ID 存储。刷新使用临时索引并在事务内合并；未变化文件保留选择，新出现/变化项不自动选中。
- `VideoFilesAdapter` / `VideoThumbnailLoader`：单 RecyclerView，分页驻留上限 240 行，缩略图目标 320px，LRU 上限 8MB，共享 I/O 并发上限 2。停止/回收时取消请求并释放可见图片引用；不读取原尺寸视频帧。
- 共用 `CleanupEntryCoordinator`、`CleanupViewModel`、`CleanupOperationCoordinator` 和 `FileOperationEngine`；不另写删除流程或通用 Activity 基类。
- Figma 原版资源和来源：`design/figma/video-cleaner/`。转换脚本：`tools/convert_video_assets.py`，依赖 Pillow/CairoSVG。首页图标使用原始透明 PNG，而不是含白底的节点导出；播放/折叠来自原版 SVG。发布资源为 mdpi–xxxhdpi WebP。
- 新文案覆盖现有 15 个非英语语言目录；数量与单位分开显示的完成页沿用原有复数规则。

## 验证

本机仅运行 local 渠道，没有编译、打包或安装 google 渠道，没有提交或推送。

```
./gradlew :app:lintLocalDebug :app:testLocalDebugUnitTest :app:assembleLocalDebug :app:assembleLocalDebugAndroidTest
```

- local 编译/打包及 Lint 通过；Lint 保留现有告警（没有通过新增 baseline 忽略错误）。
- 190 项 JVM 单元测试通过；新增本地月份边界、候选来源、完成计数和入口顺序测试。
- Pixel_10a_Offline / API 37 模拟器：10 项视频索引、真实媒体及公共权限 UI 测试通过（未授权组 9 项、已授权直接删除 1 项，分两次进程运行）。包括 160 条分页、跨页全选、月份折叠、选择快照、刷新隔离、重建、确认取消、正常/双倍字号布局。
- 真实媒体测试仅创建并处理本测试的 MP4/损坏容器：检查视频轨道过滤、系统缩略图尺寸、引擎返回系统 Consent、取消保留文件、文件未消失时不虚报成功、实际删除后统计成功。没有通过测试删除用户视频。
- [视频页系统截图](verification/video-cleaner/video-tests/video_hardware.png)、[所有文件权限说明](verification/video-cleaner/all-files-permission.png)、[双倍字号所有文件权限说明](verification/video-cleaner/all-files-permission-large.png)、[双倍字号月份标题](verification/video-cleaner/video-tests/video_header_2.0.png)、[双倍字号视频格](verification/video-cleaner/video-tests/video_tile_2.0.png)。列表截图使用独立测试索引，故显示默认视频占位；不是生产示例数据。

首次广泛权限回归受到模拟器系统 16KB 兼容提示、热启动/首页首次弹框干扰。公共权限 UI 测试现使用已有 debug 首页预览与前后台交接保护隔离无关入口（finally/After 释放），保留真实权限协调器和对话框交互；未修改生产广告/评分/通知规则。关闭模拟器兼容提示后，原 8 项设备测试通过；本次权限策略调整后按授权状态分两次启动，共 10 项测试通过。兼容提示列出的现有第三方 native 库（例如 liballiance.so 等）不属于此次视频实现，未改动相关 SDK 或兼容模式配置。

当前未实测各 OEM 字体、Android 8–14 全版本组合、大型媒体库长时间性能或功耗；并发/缓存上限是实现约束，不是无 ANR/OOM 的实测保证。

Android 媒体访问与删除依据：[Android 官方共享媒体文档](https://developer.android.com/training/data-storage/shared/media)。

2026-09-18 权限调整验证：模拟器未授权时入口返回 ALL_FILES；授权后保持 MediaStore 视频扫描，实际测试视频删除直接返回 Finished，没有生成 Consent。未授权时的系统 Consent/取消/失败统计回归继续通过。Android 修改所有文件 AppOp 会终止应用，因此权限状态在测试进程启动前设置，并在结束后恢复默认。

权限说明最终文案：视频入口即使申请 ALL_FILES，也显示 “Allow access to your videos”、指定 Video Cleaner 说明，以及 Not now / Allow。PermissionPurpose 只控制说明和图标，PermissionKind 仍为 ALL_FILES，点击 Allow 继续打开系统所有文件访问设置。其他入口默认文案不变。已通过 local 编译、Lint 及公共弹框测量回归，复核普通/双倍字号截图。

选中/取消选中按钮闪烁修复：CleanupActionRenderer 不再用临时 editing 状态决定 isEnabled（避免写入开始变灰、写入结束变蓝），统一由有效选择和操作状态决定外观；editing 仅拦截点击。取消最后一项仍正常置灰，执行操作时仍禁用，prepare 继续等待总量查询并验证选择。local 编译与 Lint、共享按钮各 CleanupFeature 状态回归及视频页设备回归共 4 项通过，复核页面截图。

2026-09-21 二次进入修复：首页退出广告与扫描完成共用 Activity 级插屏队列。扫描已 Ready 但队列占用时保留结果，在队列释放且首页 RESUMED 时续接原扫描的广告及跳转；不重新扫描、不轮询、不增加广告请求。取消后的结果不会重放，重复 SDK 回调仍只导航一次。
