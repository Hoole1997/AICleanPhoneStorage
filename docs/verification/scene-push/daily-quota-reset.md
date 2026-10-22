# 旧推送每日额度跨日修复

2026-10-22：旧 `CheckCtrl` 对计数的读、加、手动重置均传入 `enableMidnightReset=false`，导致每日额度实际成为累计额度。

现在准入检查、今日统计统一通过日期感知读取；发送成功后的累加及手动重置也携带日期。按设备 `LocalDate.now()` 判断自然日，首次跨日访问即清零，无需午夜任务或进程持续存活。无日期的历史累计数首次读取清零并建立当天日期；同一天不会反复清零。`ResetCtrl` 累加只取一次日期，在锁内完成读改写，并一次异步写入日期与次数，避免跨午夜串日及并发丢失。

不修改旧每日限额配置值、触发间隔或新八场景的独立规则。

验证通过：

- local 应用 Kotlin 编译、notification local AndroidTest 编译、notification Lint（无 Error）。
- notification 单元测试 115 项，全部通过。
- API 37 独立测试 APK 的 `DailyNotificationCountDeviceTest` 6 项通过：当天达到上限不重置、五类旧来源跨日检查、多天未运行/旧数据迁移、发布跨午夜从 1 开始、手动重置记录日期、4 线程并发累加不丢失。
- 测试仅写 `io.docview.push.test` 的偏好数据，不改系统时钟、不发通知、不操作真实手机。无界面改动，不新增截图验收。
