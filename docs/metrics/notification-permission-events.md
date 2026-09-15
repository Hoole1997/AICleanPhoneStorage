# 通知权限埋点

按 2026-09-15 用户提供的协议接入，不改变系统权限授权规则。

## 事件与字段

| 事件 | 字段 | 值 |
| --- | --- | --- |
| Notific_Allow_Start | Notific_Allow_Position | SplashScreen / HomeScreen |
| Notific_Allow_Result | Notific_Allow_Position | 与本次申请相同的位置 |
| Notific_Allow_Result | Result | allow / denied / deined_forever / allow1 |

- `allow`：真实申请回调确认已授权；用户从本次通知设置申请中开启权限也使用此值。
- `denied`：普通拒绝；本次通知设置申请返回仍未授权也使用此值。
- `deined_forever`：在实际权限拒绝回调中，由 XXPermissions 确认“不再询问”。保留协议拼写，不改成 denied_forever。
- `allow1`：仅 Android 12/12L 及以下（API ≤ 32）无需运行时通知授权且检查已授权时上报；只报 Result，不报 Start。Android 13 及以上已授权检查、跨页面检查、排队后已授权均不补报结果；真实申请成功仍报 `allow`。

## Start 的触发边界

- 未授权，并且马上调用 XXPermissions 的 `request` 时才上报；位置由调用方明确传入。
- 请求还在排队、Activity 未 resumed、其他授权进行中、仅展示/关闭自定义引导，都不报 Start。
- 用户点引导 Allow 后需要打开系统设置时，由共享 PermissionCoordinator 的实际 launch 钩子上报，不能用早于系统调用的 before/入队回调代替。
- 如果设置流程在检查阶段发现已授权，不执行 launch，因此没有 Start。
- 热启动开屏本身不运行自动授权流程，不能仅因订阅 ViewModel 状态就产生默认允许事件。

## 生命周期与错误处理

PushPermissionTelemetry 只保存请求序号、位置、类型及去重标记，位于宿主 ViewModel 内，不持有 Activity。重复 onResume、重复权限回调及旋转恢复不会重复报结果。首页承接新一轮启动流程、用户明确重试时重置本轮标记，序号不回退。

设置页的请求 token 可随 SavedStateHandle 恢复；进程重建后无法恢复的运行时 SDK 回调会作废，不伪造拒绝结果。请求构建失败、无法完成请求或设置页不可用，没有协议对应的可靠授权结果时只放行 UI 流程，不填造 denied/forever。

埋点使用已有 BusinessTelemetry 有界启动队列，SDK 未就绪时保留顺序。Start 只有位置字段；Result 同时包含位置和首字母大写的 `Result` 字段。

## 接入位置

- StartupActivity：SplashScreen。
- MainActivity：HomeScreen。
- PushPermissionRequester：真正的运行时申请边界和拒绝分类。
- PushPermissionCoordinator：UI 协调、默认允许、设置申请接入。
- PermissionCoordinator：复用原有队列/生命周期守卫，增加可选的实际 launch 回调；其他权限使用原有注册重载，行为不变。

## 验证

- JVM 回归覆盖 API 32/33 边界、不同系统版本、两个页面位置、重复检查、宿主重建、流程重置与迟到回调。
- 保留真实申请的 allow / denied / deined_forever、请求去重和设置回跳测试。
- 设备边界测试按实际系统版本断言：API 33+ 已授权检查没有 Start/Result，API 32 及以下只有一次 allow1。
- 本次修正取代此前将 Android 13+ 已授权检查记录为 allow1 的口径。

本次验证：local Debug APK、google Debug Kotlin 编译、local AndroidTest APK 构建通过；187 项 app JVM 测试通过，local Debug Lint 无错误。设备测试代码已更新并编译，本次未进行真机授权复测。
