# Application 与 CoreService 双层屏幕事件监听

2026-09-15：用户要求两处各注册一份，并同时启动，以提高后台可用性。

## 实现

| 职责 | 位置 | 行为 |
| --- | --- | --- |
| Application 注册 | TimingCtrl / ScreenEventRegistration | 使用 Application Context；失败不记录假句柄，下次初始化可补注册 |
| Service 注册 | CoreService / ScreenEventRegistration | 成功进入前台并等待 Runtime 准备好后，用 Service Context 注册独立接收器 |
| 注册与注销 | ScreenEventRegistration | 每个 owner 自己保存、关闭句柄；全局对象不保存 Service Context |
| 统一业务入口 | TimingCtrl.onScreenEvent | 两处均可分发事件，但只处理状态门控接受的事件 |
| 去重与过期事件 | ScreenEventGate | 按真实熄屏/亮屏/解锁状态转换去重；用 PowerManager/KeyguardManager 当前状态拒绝过期广播，不用固定时间窗口 |
| 服务启动 | KeepAliveServiceManager | 应用可见且通知已授权时启动；服务运行时不重复启动，权限撤销时停止 |
| 正常系统恢复 | CoreService.START_STICKY | 冷进程先履行 startForeground 契约，再等待初始化完成并建立 Service 监听 |

两份接收器仅订阅 SCREEN_OFF、SCREEN_ON、USER_PRESENT 三个系统受保护广播，使用 RECEIVER_EXPORTED 兼容独立 UID 的 OEM SystemUI。

`CleanNotificationHost.backgroundServiceEnabled = true`；`periodicPushEnabled` 独立且默认 false。启用事件监听不会开启原来的定时推送或 WorkManager 保活。初始化时取消旧的唯一周期任务，历史 Worker 即使先被调度，也会检查开关直接完成。

移除了 ServiceMgr 中通过 Provider 延迟重试启动服务的路径。后台没有运行中的 Service 时，保留 Application 监听和常驻通知，等待合法的前台启动时机；不循环强拉服务、不申请电池豁免。

常驻通知复用 ID 10001，真实设备显示 FOREGROUND_SERVICE / ONGOING_EVENT 标志。自然用户隐藏第四个入口的现有业务逻辑完全不变。

## 生命周期与去重验证

- local/google Debug 构建通过；通知模块 26 项 JVM 测试通过，0 失败。
- 新增 ScreenEventGateTest 覆盖双份事件、迟到广播、连续真实锁屏周期、遗漏 OFF 后恢复、另一 owner 加入时不重置去重状态。
- CoreServiceDeviceTest 读取设备实际广播注册表：重复 start 三次没有新增接收器；释放 Application 监听仅减少一份；重建 Application 后恢复；停止 Service 仅减少一份，Application 监听仍在。实机结果 `OK (1 test)`。
- app / notification 的 localDebug Lint 均为 0 Error，仍有现有 Warning（分别为 180 / 48）。

## 真实后台与恢复测试

设备：CPH2723，Android 15 / API 35，已授权通知。

- 19:06:29.162：注册 owner=APPLICATION。
- 19:06:29.200：注册 owner=SERVICE。
- 系统服务表：`CoreService isForeground=true foregroundId=10001`。
- 用户 Home → 锁屏 → 解锁后，19:07:14.182 系统 USER_PRESENT 记录显示广播已投递到应用接收器，服务仍处于前台状态。此次业务去重依据单元测试验证，未把已被日志缓冲覆盖的业务日志当作实测计数。
- 随后仅对本应用调试进程执行 SIGKILL（不是 force-stop，也没有清数据）。系统在 2 秒一次采样中观察到 PID 31389 → 2078，CoreService 已恢复前台。
- 恢复日志：19:09:08.464 重新注册 APPLICATION；19:09:08.492 重新注册 SERVICE。没有主动打开页面或用 Provider 重试帮助恢复。

该恢复结果是一次设备实测，不代表固定 2 秒恢复时限。两份接收器属于同一应用进程；系统强制停止、用户主动停止服务以及 OEM 限制仍可能阻止运行或恢复。

## 常驻通知截图检查

自然用户的第四个入口必须保持 GONE。原截图测试错误地读取隐藏父容器内 TextView 的 Layout，修正为：先断言第四入口隐藏，再只检查实际参与布局的可见文字。不使用 isShown 跳过未附着窗口的整个 RemoteViews，也不修改生产 UI。

自然用户隐藏第四项的业务逻辑保持不变；按用户后续要求，不再继续通知视图测试。

## 参考

- [Android 广播注册与导出标志](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
- [前台服务类型与 specialUse 声明](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android 14 缓存进程广播投递行为](https://developer.android.com/about/versions/14/behavior-changes-all)
