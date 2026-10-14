# AlarmManager 推送触发

依据：[v1.0.4 需求，AlarmManager 循环唤醒章节](https://pic6ktmsyi.feishu.cn/wiki/TZHww3Sifiys0WkFJ8ycJFfJnkb)，核对日期 2026-10-14。

Firebase Remote Config Key 为 `alarm_wakeup_config`，JSON Object 经 Remax 加密下发。客户端通过宿主的 `ConfigRemoteManager.getString` 解密，再注入 notification 模块；不直接解析 Firebase 原始密文。线上模板见 `alarm-wakeup-config.json`；实际打包的本地默认参数位于 `notification/src/main/assets/alarm_wakeup_config.json`。按用户 2026-10-14 最新要求，默认总开关开启、买量开启、自然/未确认归因关闭、循环间隔 15 分钟。

| 字段 | 说明 |
| --- | --- |
| `paid_tier` | 已确认买量归因使用的调度策略 |
| `standard_tier` | 自然用户和未确认归因使用的调度策略 |
| 根级 `alarm_wakeup_enabled` | 总开关：数字 `1` 开启、数字 `0` 关闭；旧线上 JSON 未提供时兼容为开启 |
| tier 内 `alarm_wakeup_enabled` | 数字 `1` 开启，数字 `0` 关闭；两个 tier 都必须提供 |
| `service_heartbeat_interval` | 独立 Alarm 间隔，单位分钟；缺省 15，限制在 15–1440 |

正文背景描述出现 30 分钟，详细调度规则和配置样例为 15 分钟，客户端采用后者。校验段落的 `interval_minutes` 与样例字段名不一致，采用 JSON 样例的 `service_heartbeat_interval`。此字段仅属于 `alarm_wakeup_config`，不改变旧 `pushConfigJson` 的 Service/WorkManager 配置。

配置优先级为：有效当前线上 JSON → 有效线上缓存 → assets 本地默认 JSON。未下发参数、Remax 未就绪、读取/解密失败或格式错误均回退，不再因为线上参数不可用而默认关闭。只有有效线上值会写入缓存；有效的线上或缓存总开关/tier 显式关闭必须保留，不被本地开启默认覆盖。总开关关闭会关闭全部 tier；总开关开启仍需对应 tier 开启。根级总开关、tier 开关必须使用数字 0/1，两个 tier 必须完整，interval 规则保持独立。

首次实际前台启动后才启用恢复。只注册一个显式不可变广播 PendingIntent；使用 `ELAPSED_REALTIME_WAKEUP` 单次非精确 Alarm，执行后从当前时间安排下一次，不补发漏掉的周期。前台/冷启动复核不会重排仍有效的当前 Alarm；间隔修改从下一次注册生效；关闭立即取消。系统重启和自身升级广播只恢复调度，不发布通知。注册凭据和配置缓存排除备份/设备迁移。

触发沿用普通通知的归因、通知权限、Channel、前台状态、免打扰、新用户冷却、频次及日龄文案池检查。自然 tier 可开启后台调度，普通日池推送仍遵守现有已确认买量用户资格。Alarm 不申请精确闹钟或电池豁免，不启动 Activity、Service/Provider，不扫描文件或执行测速。

`CheckCtrl.NotificationType.ALARM_MANAGER.string` 为 `alarmmanager`。`Notific_Pull`、`Notific_Show_Fail`、`Notific_Show`、`Notific_Click`、`Notific_Enter` 增加 `trigger_source=alarmmanager`，`Notific_Type` 保持普通通知的 `1`。点击来源经启动页白名单和 SavedStateHandle 保留，与展示文案/点击前后台状态共用快照。Alarm 使用独立的通知点击 PendingIntent identity，发布前被拦截不会改写另一触发源的旧通知。

Receiver 的异步窗口覆盖实际发布，8 秒取消检查并另设 9 秒广播截止；失败/超时回收同 token 凭据后恢复下一轮。所有状态操作串行，重复或迟到广播不消费新任务。耗时初始化、配置读取、磁盘提交、Alarm 注册均离开主线程。

Android Doze、省电/OEM 策略可延迟任务；强制停止后由用户重新打开恢复，不能承诺固定时刻或系统杀进程后必定执行。参考 [Android 非精确 Alarm 文档](https://developer.android.com/develop/background-work/services/alarms)。

## 验证

- 仅 localDebug：notification/app 编译和打包、AndroidTest 编译通过；未编译、打包或安装 google 渠道。
- JVM：notification 84 项、app 308 项，共 392 项通过；其中独立配置及默认来源回退 22 项、单次循环及故障恢复 20 项，点击来源与 SavedStateHandle 恢复也有测试。
- Lint：notification/app 均 0 Error，既有 Warning 分别为 47/215；Alarm 新文件无 Lint issue。
- Android API 36 设备：本轮打包资产默认规则、独立 Request Code 的非精确系统注册/替换/取消、2 倍字号通知共 3 项通过；此前 Click/Enter 来源、前后台快照、启动页白名单和一次消费的设备核验通过。未在设备上强制重启、强停或等待真实 15 分钟周期；相应状态规则由 JVM 状态机测试覆盖。
- 360dp、2 倍字号的 Alarm 通知使用原生 RemoteViews 渲染核验，按钮无字体裁切；沿用原布局的标题/描述省略策略。截图及本地验证日志位于 `build/alarm-wakeup/`。

本地默认已实际打包并接入运行时回退，无需预先下发 Firebase 参数；本次未修改 Firebase 后台参数。

## Pixel 10a 真实后台投递核验（2026-10-14）

在用户指定的 `Pixel_10a` 模拟器（Android API 37）安装 localDebug，运行模拟器专用 opt-in `AlarmWakeupEmulatorTest`，实际等待系统投递；默认间隔没有缩短，也没有手动发送广播。测试仅把通知模块的会话归因快照切为买量，SDK 持久化归因未修改。测试期间模拟器离线；初始缓存推送策略的免打扰时段为 02:00–08:00，当时纽约时区 04 点的申请被正常拦截，因此临时使用 UTC 进行投递核验，结束后恢复 America/New_York 及原来的 Wi-Fi/移动数据开关。

| 核验 | 结果 |
| --- | --- |
| 本地默认配置 | 已启用总开关、买量开启、自然关闭、15 分钟；Alarm 配置缓存为空，实际使用 bundled defaults |
| 自然用户 | 原有 Alarm 注册和 PendingIntent 均取消 |
| 前台 | 生产通知检查返回 APP_IN_FOREGROUND |
| 实际后台等待 | 958.966 秒，约 15 分 59 秒 |
| 系统合批延迟 | 比配置到期晚 58.991 秒，未提前投递 |
| 通知发布 | 成功发布 ID10000，trigger_source=alarmmanager，from_background=true |
| 点击 Intent | 与 `.alarmmanager` 来源的显式不可变 PendingIntent 匹配 |
| 下一轮 | 新 token，间隔 900.002 秒，仍为 15 分钟单次注册 |
| 测试结果 | OK (2 tests)，local 编译/Lint 通过 |

截图和有界证据 JSON 位于 `build/alarm-wakeup/pixel10a/notification-panel.png`、`evidence.json`；日志及系统 Alarm 注册记录在同目录。截图标题由现有 `local_030_clean` 文案池提供，其中固定的 4.3 GB 是文案内容，本次未扫描文件，不作为容量实测结论。

这次验证的是应用在后台、同一进程的真实系统投递；没有用它声称已验证系统回收后的冷进程或设备重启恢复。测试结束后恢复实际渠道，取消测试普通通知和自然用户的 Alarm。
