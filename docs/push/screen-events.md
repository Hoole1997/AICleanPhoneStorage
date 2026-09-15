# 后台屏幕事件监听排查

## 首次排查时的职责（双层监听之前）

- `NotificationRuntime` 在应用进程初始化时调用 `TimingCtrl.initialize`。
- `TimingCtrl` 使用 Application Context 动态注册 `SCREEN_OFF`、`SCREEN_ON`、`USER_PRESENT`，由单例持有接收器。切后台只更新前后台状态并检查 BACKGROUND 通知，不注销屏幕接收器。
- `SCREEN_OFF` 停止重复通知；`SCREEN_ON` 确保常驻入口存在；`USER_PRESENT` 才检查 UNLOCK 普通通知。
- `NotificationHost.backgroundServiceEnabled` 默认 false，当前 CleanNotificationHost 没有覆盖它。因此 ServiceMgr 只确保普通常驻通知存在，不启动 CoreService；WorkManager 保活入口也关闭。
- 这里监听的是系统事件，没有注册 TIME_TICK/TIME_SET，也没有为了观察屏幕状态而持续轮询系统时间。

## 2026-09-15 实机复现

设备：CPH2723，Android 15 / API 35；当前包为可调试测试安装。通过 adb 检查确认进程存活、三个屏幕 action 已注册，CoreService 未运行；采样时 isFrozen=false，不能将本次问题归因于进程冻结。

用户执行“打开 App → Home 桌面 → 锁屏 → 解锁”，修复前应用日志（设备本地时间）：

| 时间 | 证据 |
| --- | --- |
| 18:47:51.401 | 应用切到后台 |
| 18:47:51.405 | BACKGROUND 被新用户冷却拦截，剩余 351.262 秒 |
| 18:47:52.308 | 收到 SCREEN_OFF，app_in_foreground=false |
| 18:47:53.521 | 收到 SCREEN_ON，app_in_foreground=false |
| 18:47:54.512 | 系统产生 USER_PRESENT，发送方为 com.android.systemui，UID 10258；通知模块没有对应接收日志 |

这证明当次后台熄屏/亮屏监听有效，缺失的是解锁事件，不是所有后台事件都失效。

## 原因与修复

原注册使用 `ContextCompat.RECEIVER_NOT_EXPORTED`。该设备的解锁广播来自独立 UID 的 SystemUI，不是发送 SCREEN_ON/OFF 的 system UID 1000；NOT_EXPORTED 会排除这类发送者。

把这个仅处理系统屏幕事件的接收器改为 `RECEIVER_EXPORTED`，并注释说明注册范围。三个 action 均为 Android 声明的 protected-broadcast，普通应用不能伪造；应用自定义广播不能混入此过滤器。没有修改冷却时间、通知频率，也没有开启旧后台服务/WorkManager/重复通知。

参考：[Android 广播注册与导出标志](https://developer.android.com/develop/background-work/background-tasks/broadcasts)、[AOSP 系统受保护广播声明](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)。

## 验证

- `:app:assembleLocalDebug`、`:notification:testLocalDebugUnitTest`、`:notification:lintLocalDebug` 通过；21 项单测，0 失败；Lint 0 Error、48 个现有 Warning。
- 修复后的 local Debug APK 已保留数据覆盖安装到同一设备，并启动后由用户真实锁屏/解锁复测。
- 18:50:25.237 收到 SCREEN_OFF，app_in_foreground=false。
- 18:50:26.341 收到 SCREEN_ON，app_in_foreground=false。
- **18:50:27.853 收到 USER_PRESENT，keyguard_locked=false，app_in_foreground=false。**
- 18:50:27.854 进入 `trigger=UNLOCK` 检查；18:50:27.856 因 `new_user_cooldown` 拦截（剩余 194.812 秒）。说明后台解锁链路已恢复，未展示普通通知属于现有冷却策略。
- 额外观察到返回前台解锁时同样收到事件，并按 `app_in_foreground` 拦截，前台不发普通通知的策略保持有效。
- 本次无页面布局修改，以真实后台广播与策略日志验证行为；未把构建通过替代实际事件复测。

## 运行边界

常驻通知可见不代表前台服务在运行，也不保证进程一直执行。动态接收器的注册跟随应用进程；Android 14+ 可延迟缓存进程的部分广播，进程被终止后该注册不再存在。本次修复针对 SystemUI 发送方被过滤的问题，不能据此声称长期后台监听不受系统限制。参考 [Android 14 广播投递变更](https://developer.android.com/about/versions/14/behavior-changes-all)。

## 后续双层监听

用户随后授权 Application 和 CoreService 同时启动并各自注册，当前实现与验证记录见 [双层监听说明](dual-screen-listeners.md)。自然用户隐藏第四个常驻入口的业务逻辑保持不变。
