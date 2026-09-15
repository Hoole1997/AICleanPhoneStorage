# 飞书业务埋点覆盖报告

依据：[AI Clean & Phone Storage 埋点方案](https://pic6ktmsyi.feishu.cn/wiki/GvgKwIzPeiWUmVk0BQFc31dNnVh?sheet=0pRVXK)，2026-09-11 通过 Chrome 导出读取全部 9 张工作表。逐字段快照见 [event-spec.json](event-spec.json)。没有修改飞书文档。

## 覆盖结论

共 40 个事件：**23 项现有场景字段完整、13 项部分接入、4 项尚无可验证触发/结果数据**。36 个事件已有实际业务调用，不能据此称为全表完整实现。没有为了凑齐事件而新增业务页面、伪造分组或用 0 替代未知数据。

本次按已说明的“先补现有业务并列出缺失场景”范围执行；补完下列业务/数据缺口后才能满足全表所有事件、参数和枚举。

## 公共实现

- 统一通过 BusinessTelemetry → `ReportDataManager.reportData()` 上报；仅两个文档要求的事件添加 user_type，使用 `ChannelUserController.getCurrentChannel()`，PAID→paid，NATURAL→organic，不能用构建渠道代替归因。
- 启动前有界缓存 256 个小事件；metrics 初始化后单消费者按顺序转发。权限在触发时异步采集，队列保留顺序；读取和上报最多占用两个 I/O 执行线程，不阻塞主线程。未配置上报器和队列溢出有日志。
- `page_show` / `page_leave` 基于实际 Activity 可见生命周期配对，stay_duration 为秒（数值），不把后台停留加到下一次访问。ViewModel 去重配置重建。枚举中没有的设置页、启动页、广告页、通知完成页不新增自定义 page 值。
- 首页状态数据与虚拟垃圾量同源，dirty 时 junk_size 为数值 MB；cleaned 时不传。预览模式不发送首页演示数据。真实文件事件始终使用实际索引/操作摘要，不引用首页虚拟量。
- 选择保存后读取新的 SQL 汇总；质量调整不冒充勾选。筛选变化不重发扫描结果；扫描事件只在真正完成扫描时发出。点击事件不由广告回调重放；完成页专项结果在首次可见时上报，重建不重报。
- 应用卸载只上报大小区间，不上传包名。所有自定义文件事件不含文件名、URI、路径或索引 ID。
- 常驻入口使用独立 PendingIntent 标识，保留所展示角标状态，启动页消费后移除标记。普通推送不误算常驻点击；通知模块内部源码未修改。

## 全表逐项对应

| 工作表 | 事件 | 状态 | 当前落点与说明 |
| --- | --- | --- | --- |
| 01-启动与首页 | `app_launch` | 已接入 | [PageTelemetry.kt](../../app/src/main/java/com/example/aicleanphonestorage/core/analytics/PageTelemetry.kt)：现有触发时机和参数已接入。 |
| 01-启动与首页 | `page_show` | 部分接入 | [PageTelemetry.kt](../../app/src/main/java/com/example/aicleanphonestorage/core/analytics/PageTelemetry.kt)：按实际 Activity 曝光；没有 unused_detail 页面。权限引导位于入口页，拒绝时由 feature_entry_click=false 覆盖，不虚构功能页曝光。 |
| 01-启动与首页 | `page_leave` | 部分接入 | [PageTelemetry.kt](../../app/src/main/java/com/example/aicleanphonestorage/core/analytics/PageTelemetry.kt)：与实际曝光页面配对；unused_detail 页面缺失。后台不累计停留，进程被杀无法保证离开事件。 |
| 01-启动与首页 | `home_state_show` | 已接入 | [MainActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/app/MainActivity.kt)：现有触发时机和参数已接入。 |
| 01-启动与首页 | `clean_now_click` | 已接入 | [MainActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/app/MainActivity.kt)：现有触发时机和参数已接入。 |
| 01-启动与首页 | `feature_entry_click` | 已接入 | [MainActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/app/MainActivity.kt)：现有触发时机和参数已接入。 |
| 01-启动与首页 | `rate_show` | 未接入 | 没有好评弹窗。 |
| 01-启动与首页 | `rate_click` | 未接入 | 没有星级选择和评分结果流程。 |
| 02-垃圾清理 | `junk_scan_result` | 部分接入 | [FileScanRepository.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/FileScanRepository.kt)：total_size 为当前七类候选合计；仅 APK/临时文件有匹配统计，缺 empty_folder_size、ad_file_size，不填假 0。 |
| 02-垃圾清理 | `junk_group_click` | 部分接入 | [JunkCleaningActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/ui/JunkCleaningActivity.kt)：INSTALLERS→apk、TEMPORARY→temp；其余五类不冒充文档枚举。 |
| 02-垃圾清理 | `junk_detail_check` | 部分接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：APK/临时文件三级页可记录；其他分类缺少文档枚举。 |
| 02-垃圾清理 | `junk_clean_click` | 部分接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有 Smart Clean 路径已接；空扫描按钮仍隐藏，got_it 路径缺失。 |
| 02-垃圾清理 | `junk_result_show` | 已接入 | [CleanupTelemetry.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/analytics/CleanupTelemetry.kt)：明确 emptyScan 时 type=already_clean；成功删除时 type=cleaned，deleted_size 为实际 MB。失败/取消不按删除数 0 误判为空扫描。 |
| 03-流量使用 | `traffic_page_show` | 已接入 | [NetworkTrafficActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/NetworkTrafficActivity.kt)：现有触发时机和参数已接入。 |
| 03-流量使用 | `traffic_manager_click` | 已接入 | [NetworkTrafficActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/NetworkTrafficActivity.kt)：现有触发时机和参数已接入。 |
| 04-通知清理 | `notify_page_show` | 已接入 | [NotificationCleanerActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/notifications/ui/NotificationCleanerActivity.kt)：现有触发时机和参数已接入。 |
| 04-通知清理 | `notify_clean_click` | 已接入 | [NotificationCleanerActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/notifications/ui/NotificationCleanerActivity.kt)：现有触发时机和参数已接入。 |
| 04-通知清理 | `notify_clean_result` | 未接入 | 当前 Done 汇总已保存规则；没有实际清除来源数，不能用 selected_count 冒充 cleared_count。 |
| 05-截图清理 | `shot_scan_result` | 已接入 | [FileScanRepository.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/FileScanRepository.kt)：现有触发时机和参数已接入。 |
| 05-截图清理 | `shot_check` | 已接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有触发时机和参数已接入。 |
| 05-截图清理 | `shot_clean_click` | 已接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有触发时机和参数已接入。 |
| 05-截图清理 | `shot_result_show` | 已接入 | [CompletionActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionActivity.kt)：现有触发时机和参数已接入。 |
| 06-照片压缩 | `photo_scan_result` | 已接入 | [FileScanRepository.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/FileScanRepository.kt)：现有触发时机和参数已接入。 |
| 06-照片压缩 | `photo_check` | 已接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有触发时机和参数已接入。 |
| 06-照片压缩 | `photo_compress_click` | 已接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有触发时机和参数已接入。 |
| 06-照片压缩 | `photo_result_show` | 已接入 | [CompletionActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionActivity.kt)：现有触发时机和参数已接入。 |
| 07-大文件清理 | `large_scan_result` | 已接入 | [FileScanRepository.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/FileScanRepository.kt)：现有触发时机和参数已接入。 |
| 07-大文件清理 | `large_filter_change` | 部分接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有筛选值全部正确映射；UI 尚无 1gb、6m、1y 选项。 |
| 07-大文件清理 | `large_file_check` | 已接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有触发时机和参数已接入。 |
| 07-大文件清理 | `large_clean_click` | 已接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有触发时机和参数已接入。 |
| 07-大文件清理 | `large_result_show` | 已接入 | [CompletionActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionActivity.kt)：现有触发时机和参数已接入。 |
| 08-应用管理 | `apps_page_show` | 部分接入 | [AppManagerActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerActivity.kt)：使用页面实际可管理应用数量，受系统可见性限制；尚无通知栏 App 数量角标可比对。 |
| 08-应用管理 | `apps_uninstall_click` | 部分接入 | [AppManagerActions.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerActions.kt)：可用大小转为区间；大小未知则省略 size_band，不伪造为 lt50mb。 |
| 08-应用管理 | `apps_uninstall_jump` | 已接入 | [AppManagerActions.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerActions.kt)：现有触发时机和参数已接入。 |
| 09-未使用文件与通知栏 | `unused_scan_result` | 部分接入 | [FileScanRepository.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/FileScanRepository.kt)：记录扫描完成事实；现有扫描未建立 installed_apk/residue/download 三项统计，暂不传这三个字段。 |
| 09-未使用文件与通知栏 | `unused_group_click` | 未接入 | 未使用文件目前是平铺列表，没有三分组入口。 |
| 09-未使用文件与通知栏 | `unused_check` | 部分接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有勾选 action/selected_size 已接，缺分组数据时不传 group。 |
| 09-未使用文件与通知栏 | `unused_clean_click` | 已接入 | [CleanupViewModel.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt)：现有触发时机和参数已接入。 |
| 09-未使用文件与通知栏 | `unused_result_show` | 已接入 | [CompletionActivity.kt](../../app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionActivity.kt)：现有触发时机和参数已接入。 |
| 09-未使用文件与通知栏 | `notifbar_entry_click` | 部分接入 | [ResidentClickTelemetry.kt](../../app/src/main/java/com/example/aicleanphonestorage/feature/push/ResidentClickTelemetry.kt)：现有 Clean/Photos 可记录，缺 App/Accelerate 入口与 app_badge_count；Photos 当前跳压缩，尚未改为文档的截图。 |

## 需要补充的业务/口径

1. 好评弹窗及星级操作，才能触发 rate_show/rate_click。
2. 通知清理实际取消完成的统计与来源去重，才能可靠上报 notify_clean_result.cleared_count；当前配置来源数不等于真正清除的来源数。
3. 垃圾分组需要统一定义。目前七类为安装包、临时文件、旧日志、空文件、重复、相似、低质量照片，不能直接套用文档的四类。
4. 未使用文件需要文档的三分组及相应真实统计、三级页；不能从当前“未修改时间候选”推断 90 天未访问。
5. 通知栏 App/Accelerate、App 数量角标、Photos→截图跳转，以及 A/B 入口布局仍需业务调整。
6. 2026-09-15 更新：空扫描完成页已有明确 emptyScan 标记，本次补齐 junk_result_show.type=already_clean；大文件筛选项不在本次修复范围。

未知大小时省略 size_band；区间边界按 <50MB、50–200MB（含200）、>200–500MB（含500）、>500MB 处理。

## 联调

日志 Tag：`BusinessMetrics`，例如 `adb logcat -s BusinessMetrics`。日志中的事件名/字段表示已调用 SDK，不代表服务端确认接收。SDK 自有广告、收益、Adjust 与推送埋点继续由原模块处理，没有重复补一份。

localDebug/googleDebug Kotlin 编译、localDebug APK 与测试 APK 构建通过；148 项单元测试通过；localDebug Lint 0 错误、137 条警告。API 32 模拟器 13 项回归通过，包括选择保存后的大小、去重筛选、常驻上下文单次消费、清理确认/广告回调时机、启动通知导航和首页状态。原有时机测试首次被真实热启动广告插入干扰，已增加仅测试使用的交接 guard 隔离后通过，未关闭产品热启动规则。

静态核对已覆盖全部 9 张表、40 个事件名，没有自行发明事件名；客户端方法调用与本地回归不等于服务端收数验收。此次不改 UI 资源，不新增视觉截图。
