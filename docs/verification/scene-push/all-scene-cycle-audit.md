# 八种新场景循环、队列交接与 OnlyAlertOnce 核查

2026-10-22。此次检查未修改生产推送逻辑，补充全场景回归和实际系统通知记录。

## 结论

- 1101～1108 共用首条发布后的独立循环路径，原触发快照变化不会中断已开始的一轮。
- 前一轮完成配置的额外重复次数，并经过最后 4 秒展示窗口后，下一候选才首次发布。划除、关闭开关、回前台、权限撤销、服务结束等既有停止规则保留。
- 八种场景的首次、第一次重复、后续静音重复均未启用 `Notification.FLAG_ONLY_ALERT_ONCE`，生产场景代码没有 `setOnlyAlertOnce(true)`，也没有一次性 `PendingIntent.FLAG_ONE_SHOT`。
- 仓库内 `setOnlyAlertOnce(true)` 两处分别属于 `TriggerCtrl.resident()` 与 `CoreService.bootstrapNotification()`，均为常驻/前台服务通知，保持原样。

## 验证

1. 单元测试 139 项通过。新增虚拟时钟测试覆盖八种场景各 8 次额外重复：总计 8 次首次发布、64 次重复，下一场景首次发布时间依次为 0/36/72/108/144/180/216/252 秒。首条后将该场景原触发条件设为不满足并取消候选，仍可播完整轮；最后没有额外重发，历史只记录 8 次首次展示。
2. API 37 完整设备测试实际运行约 96 秒，依次验证八种场景，每种配置 2 次额外重复，全部通过。系统记录共 24 条，首次/重复的 OnlyAlertOnce 位全部为 false；重复间隔实测 4006～4014ms，下一场景首次发布距上一场景最后一次重复为 4006～4014ms。保留原点击 PendingIntent，静音阶段仍无 OnlyAlertOnce。
3. 设备测试确认 Show 只有 8 次、旧每日偏好数据未变、旧通知 ID 保留。最新 local APK 上旧通知布局、提醒策略和来源埋点回归 9 项通过。
4. local 编译、notification Lint、`git diff --check` 通过。此次没有布局修改，复用此前截图验收。

实录：[24 条系统通知记录](all-scene-cycles.json)、[全场景设备测试](all-scene-cycles-device.txt)、[旧推送回归](all-scene-cycles-legacy.txt)。

以上验证循环提交、通知标志和队列交接；实际悬浮横幅仍由系统及用户通知设置管理。未更改常驻通知、旧推送配置或 google 渠道，未向真实手机发送测试通知，未提交 Git。
