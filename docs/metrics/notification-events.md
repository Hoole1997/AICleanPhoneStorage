# 通知埋点上下文修复

日期：2026-09-15。依据 [推送模块需求 / 4. 埋点](https://pic6ktmsyi.feishu.cn/wiki/M5XMwD5FQinTCukK06LcKIQQnUh#FbegdnW5AoG8zBxIUfUcVksGnmf)，通过 Chrome 核对。

文档中 title/text 分别为通知标题/通知展示文本，Notific_Click 的 from_background 表示 App 是否在后台（true/false）。按本次缺陷要求，三个事件均补齐这些字段。

## 取值与时机

| 事件 | from_background | title / text |
| --- | --- | --- |
| Notific_Show | 成功发布通知时，应用是否存在可见 Activity | 此次发布通知的文案快照 |
| Notific_Click | 通知进入启动页时、resume 之前采集的状态 | 被点击通知 PendingIntent 中的文案快照 |
| Notific_Enter | 沿用对应 Click 的状态，不在首页重新判断 | 沿用对应 Click 的同一文案快照 |

`from_background` 在事件参数中使用字符串 `"true"` / `"false"`。Firebase 官方只支持 String/long/double，不能直接用 Bundle Boolean，否则可能丢失参数。参考 [FirebaseAnalytics.logEvent](https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics)。

## 修复点

- 常驻展示事件原先注释掉了 title/text，现在从实际通知模型发送。
- 本地/客户端 FCM 的展示事件保留原始文案字段，并补充展示时前后台状态。
- 常驻通知从已有可见项生成文案摘要，包含真实资源文字和可见角标；自然用户第四个入口仍隐藏，摘要也不包含它。不修改布局或显示规则。
- 常驻卡片根区域和各快捷入口 PendingIntent 都携带有界文案；普通推送兼容既有 landing_notification_title/content。
- StartupActivity → StartupViewModel/SavedStateHandle → MainActivity 白名单链路传递 NotificationClickContext。进入事件发送后移除 Intent 中的埋点字段，避免重复消费。
- NotificationVisibility 单独记录 Activity 可见计数和本次 resume 前的状态；前台服务本身不算可见页面。这样冷启动，以及 onStart 已发生但 onNewIntent/onResume 尚未结束的回前台过程，不会统一误记为前台点击。
- 常驻 Show 进入已有有界 BusinessTelemetry 队列；SDK 未初始化时不直接丢弃。FGS 和删除恢复在成功发布后上报，不再在仅构建 Notification 时提前上报。

## 数据边界

仅处理本 App 自身通知，不读取其他应用通知正文。启动链路只允许来源、后台状态、标题和正文等明确字段，不复制任意 URI、完整 extras、RemoteViews 或 Bitmap。

文案快照上限为 title 120、text 500 个 UTF-16 单元，截断时避免拆开 emoji 代理对。Firebase 适配层对这三个事件的 title/text 再限制为 100 个 Unicode 字符，避免超长参数被 SDK 拒收；其他 reporter 保留原有界快照，通知显示内容不受此限制。

旧通知或由系统展示的 FCM 通知，若启动 Intent 没有携带文案，客户端不能还原不存在的数据；兼容读取已知 title/text/body 和 gcm.n.title/body 字段，缺失时保留空字符串，不伪造内容。新包重新发布的自家常驻/普通通知携带完整的有界快照。

## 验证

- local/google Debug 编译通过。
- JVM 测试：app 174、notification 30、metrics 2，共 206 例通过。
- 三模块 localDebug Lint：0 Error；现有 Warning 为 app 180、notification 48、metrics 9。
- NotificationLaunchTelemetryDeviceTest 覆盖三种来源 × 前后台、启动页白名单、Click/Enter 一致性、一次消费、长度限制及真实 Activity 前后台采样。
- 本次只验证埋点与生命周期行为，不运行通知视图测试。

设备测试结果：`OK (4 tests)`。

用户真实操作“Home 回桌面 → 点击常驻通知 → 到达首页”后，客户端发送日志确认：

| 时间（设备本地时间） | 事件 | from_background | title | text |
| --- | --- | --- | --- | --- |
| 19:40:59.374 | Notific_Show | true | AI Clean &Phone Storage | 清理 / 应用 / 照片 |
| 19:41:06.222 | Notific_Click | true | AI Clean &Phone Storage | 清理 / 应用 / 照片 |
| 19:41:09.353 | Notific_Enter | true | AI Clean &Phone Storage | 清理 / 应用 / 照片 |
| 19:41:09.384 | Notific_Show | false | AI Clean &Phone Storage | 清理 / 应用 / 照片 |

这验证了 Click/Enter 不受进入首页后的前台状态覆盖，Show 则使用展示时的状态。日志产物为 `build/notification-telemetry/device.log`。本次核验到客户端交给 SDK 的事件参数，未声称已核对云端入库记录。新 local Debug 包已覆盖安装。
