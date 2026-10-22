# 场景通知独立循环悬浮验收

2026-10-22，用户要求新场景补齐旧通知的循环悬浮方式，并明确复用开关/次数、每个 ID 独立、不得影响旧推送。

后续用户要求在此基础上将尚未展示的触发事件串行排队，最新生产流程与验收见 [触发事件队列](trigger-queue.md)。独立 ID/循环模块保留，生产首次发布须先取得 FIFO 展示位。

## 实现

- `SceneRepeatLoop`：主线程拥有的有限协程任务，每个 ID 一份会话，4 秒间隔。每次更新读取现有循环开关/次数。新一轮替换同 ID 旧轮，其他 ID 不受影响。
- `SceneRepeatCoordinator`：复用 `NotificationHost.repeatNotificationsEnabled`、`ConfigCtrl.getHoverDurationStrategySwitch()`、`getHoverDurationLoopCount()`。绑定 CoreService 会话，不共享旧 Handler/Runnable/currentNotificationId。
- 初次展示后按配置进行额外重复。例如次数 2：初次、4 秒重复、8 秒重复，然后结束。复用同一文案、点击快照与 ID；不重复推进文案轮播、记录频控时间、增加旧每日额度或上报 Show。
- 保留旧声音节奏：初次与第一次重复可响铃，第二次重复起静音。新场景静音使用自己的 HIGH 渠道，继续无振动，不修改旧渠道。
- 独立 immutable 划除 PendingIntent 以 ID + 会话 token 区分；服务内非导出广播接收，过滤器包含对应 URI scheme。旧卡片迟到的划除不会误停新一轮。
- 每轮复核开关、前后台、权限、事件有效性、已展示通知及 token，已消失/被替换的通知不重建。回前台、关闭场景、服务停止/超时/销毁时取消相关任务；不新增 Alarm、WorkManager 或唤醒锁。

## 验证

- local 应用与测试 APK 编译通过；notification Lint 无 Error；`git diff --check` 通过。
- notification 单元测试 123 项通过，其中新增 7 项独立循环测试：4 秒节奏、次数上限、同 ID 替换与旧 token、其他 ID 隔离、远程开关与次数变化、单项失效、异步构建取消、服务关闭。
- API 37 notification 设备测试 14 项通过（8 项场景测试 + 6 项每日额度测试）。真实发布 A/B 两张卡片，观察第一轮重复、发送 A 的划除回调并暂留卡片验证接收器确实停止 A，观察 B 第二轮进入静音，确认次数用尽后不再更新；旧 ID 仍在、旧计数未变、Show 只有初次两次。
- API 37 旧通知布局、提醒策略和来源埋点设备回归 9 项全部通过。
- 没有改动界面 XML，沿用已验收布局/动效；本次重点验证系统实际发布与取消行为。未改系统时间、未向真实手机发送合成通知、未构建 google 渠道、未提交或推送 Git。

测试日志：`repeat-device-api37.txt`、`repeat-legacy-regression-api37.txt`。系统冷却、通知渠道设置、勿扰及 OEM 策略仍决定每轮是否实际出现悬浮横幅，不能据重复 notify 成功承诺始终悬浮。
