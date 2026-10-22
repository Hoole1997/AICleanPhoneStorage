# v1.0.5 开机恢复与 Grouping

需求：[飞书第 4、5 部分](https://pic6ktmsyi.feishu.cn/wiki/M6lAwdAtyijk3jkj1PRchPOonDb)。

## 开机恢复

- `notification` Manifest 已声明 `RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE` 和 `FOREGROUND_SERVICE_SPECIAL_USE`。新增非导出的 `BootCompletedReceiver` 静态监听 `BOOT_COMPLETED`，不监听 `LOCKED_BOOT_COMPLETED`，不新增权限弹窗。
- Activity 真正启动后在 IO 写入 `notification_boot.opened`。仅 Application/Provider 初始化不写入。接收开机广播后检查已打开、通知权限、宿主服务开关、系统后台限制和服务运行状态。
- 同一系统 `BOOT_COUNT` 最多尝试一次，持久化尝试编号后通过既有 `Provider.METHOD_START_KEEP_ALIVE_SERVICE` 请求 `CoreService`，来源为 `boot_completed`。Receiver 的 `goAsync` 覆盖实际 Provider 调用，8 秒协程时限、9 秒独立广播截止；不等远程配置、不轮询、不重试。
- 复用既有常驻通知 ID `10001`、频道、布局和点击链路，Service 继续使用原 bootstrap→正式常驻通知流程。开机动作不调用普通推送发布、页面导航或扫描/清理。
- Application 冷启动的数据刷新只刷新常驻通知，不因此额外申请后台 FGS。开机尝试后的其他启动请求等真正回到前台再恢复，避免失败后由初始化/屏幕回调隐式反复拉起。用户下次打开仍沿用已有前台启动和授权回调恢复。
- 旧 Alarm 接收器继续只恢复调度；后续旧推送/新场景按原有事件、周期和配置工作，不把开机当成一条推送触发事件。电池/网络的初始快照只建立基线。
- Android 是否允许 FGS 仍由平台决定，Provider 只是现有内部调用入口。[Android 后台启动限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)与[服务类型规则](https://developer.android.com/develop/background-work/services/fgs/service-types)。

## Grouping

- 参数键严格为 `Grouping`，通过既有 `ConfigRemoteManager.getString("Grouping", "")` 读取。空值不处理，非空原样构成事件 `Grouping_<value>`，不 trim、不截断，不新增 Firebase/数数分发实现。
- `AdSdkInitializer` 唯一远程更新回调发出激活版本；统一上报接口初始化就绪后，启动页在 STARTED 生命周期订阅并读取。SDK 已初始化也会发当前版本。离线/未成功获取不猜分组，不为上报阻塞启动页；晚于页面退出的配置在下次启动页读取。
- 应用级 `GroupingReporter` 用 Mutex 串行化并发页面/配置回调，调用统一 `ReportDataManager.reportData`。接口未就绪或抛错不消费分组；同步交接成功后写入持久记录。
- `startup_grouping` 保存各值的 SHA-256 摘要键，不保存远程原文。A→B→A 仅上报 A、B 各一次，重建页面/进程不重复。仅记录客户端已交给统一接口，不表示服务端已确认收到；SDK 负责后续传输。
- 两项持久记录排除云备份及设备迁移，重装/清数据后重新建立。磁盘与配置读取离开主线程，不在后台或开机时上报 Grouping。

## 验证

本机只运行 local 渠道任务；结果见 `docs/verification/boot-and-grouping.md`。无页面布局变化；已核验重启后的系统通知截图、实际服务和通知记录，不以普通 Activity 启动代替开机广播。
