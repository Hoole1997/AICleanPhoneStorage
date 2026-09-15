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
- `allow1`：流程检查时已经授权，或排队之后在真正调用申请 API 前发现已经授权；只报 Result，不报 Start。

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

- local/google Debug 构建、app 186 项 JVM 测试通过。
- app localDebug Lint：0 Error，179 个现有 Warning。
- 可控权限边界测试覆盖两种位置、默认允许、排队后已经授权、实际调用才报 Start、三种请求结果、重复回调、未启动流程与设置边界。
- 不修改设备已有通知权限，不执行权限引导的布局/截图测试。

设备测试：`OK (5 tests)`，包括 3 项新埋点边界测试和 2 项原有引导行为回归。

手机现有 POST_NOTIFICATIONS 权限为 granted=true，未修改权限。正常启动后的客户端日志：

```text
20:38:12.624 Notific_Allow_Result {Notific_Allow_Position=SplashScreen, Result=allow1}
20:38:15.750 Notific_Allow_Result {Notific_Allow_Position=HomeScreen, Result=allow1}
```

该流程没有 Notific_Allow_Start。允许/拒绝/不再询问通过可控 SDK 边界回调测试验证，没有向真实埋点服务发送测试用假结果。新 local Debug 包已覆盖安装。日志保存于 `build/notification-permission-events/device.log`。
