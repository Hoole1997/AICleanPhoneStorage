# v1.0.5 场景推送（需求第 3 部分）

依据：[需求文档](https://pic6ktmsyi.feishu.cn/wiki/M6lAwdAtyijk3jkj1PRchPOonDb)、[Figma 7133:404](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb-%E9%A1%B5%E9%9D%A2UI?node-id=7133-404&m=dev)。用户后续确认优先于文档冲突条款：

- 间隔单位为分钟，10 就是 10 分钟。
- 八种方式分别计时，分别使用通知 ID；不考虑优先级、不设每日总条数上限，不共享旧推送额度。
- 充满使用充满文案；未充满结束使用真实的结束/断电文案。
- 点击仍经过开屏 → 首页 → 对应功能，保留原有权限和广告入口。

## 模块与生命周期

生产实现位于 `notification/src/main/java/io/docview/push/scene/`。`SceneEventRegistration` 由现有 `CoreService` 创建/关闭，分别组合电池/网络来源、媒体/包来源、纯规则、配置仓库、调度器和展示器。没有新增后台保活、事件检测轮询、Alarm、WorkManager 或 Application 层场景广播注册。

停止、超时或销毁服务会注销监听并取消延迟任务；开关变化会重新核验注册资格。进程退出不补发丢失的事件，也不恢复未结束的循环悬浮任务。首次电池/网络快照仅建立基线。每个场景最多一个待发任务，新事件替换该场景旧候选。各场景达到自己的延迟/间隔条件后，进入新场景专属 FIFO 展示队列。到期后重新核验条件和最新配置，间隔未到则等待剩余时间，再次核验；无效事件直接丢弃。

历史、文案游标、下载去重元数据使用独立 `scene_push` preferences，排除备份；用户开关单独在 `scene_push_choices` 保存。只有通知实际交给系统后才记录时间、推进文案轮播。前台提示、权限拒绝、条件失效、构建失败均不推进。旧 CheckCtrl、日龄文案池、计数、Alarm、FCM、重复推送及删除重发逻辑保持独立。

## 场景与路由

按用户最新要求，保留固定确认的场景统一等待 1 分钟（充电结束、网络切换/断网、安装、卸载、下载）；充电开始、低电量和截图没有固定确认延迟。远程间隔、充电防抖、网络抖动过滤及 FIFO 循环展示窗口分别管理。

| 编号 / ID | 场景 | 事件确认 | 功能目标 |
| --- | --- | --- | --- |
| 1101 / 13101 | 充电开始 | 插电/进入充电边沿，5 分钟插拔防抖；插电时在前台只给页面提示 | Battery |
| 1102 / 13102 | 充电结束 | 拔线/停止充电后等待 1 分钟，重新充电取消；已知小于 1 分钟会话跳过 | Battery，展示真实结束时间、电量及已知时长 |
| 1103 / 13103 | 低电量 | 首次跨入 ≤20%，跨入 ≤10% 可再次触发，仍受自身间隔约束；充电/恢复电量取消 | Battery |
| 1104 / 13104 | 网络变化 | Wi-Fi→移动数据或接入 Wi-Fi 稳定 1 分钟；离线持续 1 分钟；VPN/短暂 Wi-Fi 回连跳过 | Network Traffic，提供用户主动点击的测网速入口 |
| 1105 / 13105 | 截图 | 授权范围内 Pictures/Screenshots、DCIM/Screenshots 完成项目；识别后无固定等待，仅按远程截图间隔进入队列，发送前复核 | Screenshots，最近 7 天筛选 |
| 1106 / 13106 | 安装应用 | 非自身/非系统/非替换更新，延迟 1 分钟，复核仍安装 | App Manager，目标应用优先 |
| 1107 / 13107 | 卸载应用 | 当前用户真实移除、非替换更新且有用户应用快照，延迟 1 分钟，复核已不存在 | 现有垃圾清理入口 |
| 1108 / 13108 | 下载完成 | 授权 Download(s) 元数据；单个 >100MB、30 分钟累计 >200MB 或多个小文件聚合，延迟 1 分钟 | Large Files，匹配项目优先，否则现有大小排序 |

文件项目须非空、非 pending/trashed，排除临时文件、可识别的广告路径/文件、自身与私有路径；通知前重新检查访问权、URI、大小和修改时间。每次查询最多 64 行，下载聚合最多 256 项，仅下载使用最多 512 项去重记录；不读取图片、APK 或文档内容。包名/显示名快照最多 512 项，无法确认时跳过。

网络不申请位置权限、不读取 SSID/BSSID；无法可靠区分 Wi-Fi→Wi-Fi 漫游与新网络时保守跳过，避免误报。截图使用 MediaStore 观察。按用户最新要求，截图不做 URI/已处理项目过滤，每次识别成功都提交本批最新截图候选，由 `screenshotPushIntervalMinutes` 与现有 FIFO 控制；同一场景仍最多保留一个待处理候选，不额外等 5 分钟。Android 14 的原生截图回调只覆盖可见 Activity，不能代替本需求的服务内观察。下载观察在 Android 11+ 使用已有所有文件访问权；Android 10 无权读取其他应用下载时跳过。场景监听本身不主动申请媒体权限。

## 远程参数

两个参数均由现有宿主远程配置适配器读取，JSON 字符串。有效线上配置优先，随后有效缓存，最后本地 assets 默认值。线上显式 `false` 生效。无需修改旧推送参数。

### `scene_push_config`

完整默认值见 `notification/src/main/assets/scene_push_config.json`：总开关、八个场景开关默认 true，八个间隔默认 10。

```json
{
  "enabled": true,
  "chargeStartPushIntervalMinutes": 10,
  "chargeCompletePushIntervalMinutes": 10,
  "lowBatteryPushIntervalMinutes": 10,
  "networkChangePushIntervalMinutes": 10,
  "screenshotPushIntervalMinutes": 10,
  "appInstallPushIntervalMinutes": 10,
  "appUninstallPushIntervalMinutes": 10,
  "newDownloadPushIntervalMinutes": 10,
  "scenes": { "1105": { "enabled": false } }
}
```

间隔须为正整数分钟，缺失/错误回退 10；其他未配置场景保持开启。充电开始另有需求规定的 5 分钟防抖。用户在 Settings → Smart alerts 中可关闭全部或指定场景；远程开关与用户开关同时允许才启用。本页和落地提示覆盖项目 16 种语言。

### `scene_push_content`

完整默认文案在 `notification/src/main/assets/scene_push_content.json`，八个场景原表文案加三条未充满结束文案，共 27 条。默认采用需求中的英文，支持远程按语言覆盖：

```json
{
  "1102": [
    {
      "id": "1102_full_v1",
      "text": "<font color='#FB4949'><b>Fully charged!</b></font> Check health.",
      "buttonText": "Check",
      "condition": "full",
      "translations": {
        "zh": { "text": "<font color='#FB4949'><b>充电已完成！</b></font> 查看电池状态。", "buttonText": "查看" }
      }
    },
    {
      "id": "1102_partial_v1",
      "text": "<font color='#FB4949'><b>Charging ended!</b></font> View status.",
      "buttonText": "View",
      "condition": "not_full"
    }
  ]
}
```

只覆盖提供了有效条目的场景，其余使用本地默认。每场景最多 16 条、单条最多 32 种语言；优先完整语言标签，再基础语言，最后默认。`id` 应稳定且场景内唯一；轮播只选择条件匹配条目，成功展示后推进，旧 ID 移除后从头开始。

`condition` 可选：`any`、`full`、`not_full`、`charging`、`connected`、`not_full_unplugged`、`percent_20`、`critical`、`mobile`、`wifi`。请为含事实断言的文案设置对应条件，尤其充满、电量 20%、正在充电。默认文案已配置这些条件。

支持变量 `{battery_percent}`、`{app_name}`、`{file_count}`、`{size_mb}`；缺失事实或未知变量会跳过该条，不填造数据。替换内容做 HTML 转义。只接受一个完整的 `text` 富文本字段，明确不兼容 `title/desc`。可见长度 1～130；整个参数最多 262144 字符，单个富文本字段最多 500。

富文本支持 `b/strong`、`i/em`、`u`、`br`、十六进制颜色 `font`。`b/strong` 仅加粗，不隐式修改颜色。颜色通过 `<font color="#RRGGBB">...</font>` 显式配置，支持单/双引号；不配置颜色的片段沿用通知 TextView 默认色。本地 27 条默认文案已显式配置 `#FB4949`，远程可以改成其他颜色或移除颜色标签。不使用 WebView，不支持远程图片、脚本或链接。

## 通知展示与埋点

用户补充 [Figma 7136:4768](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb-%E9%A1%B5%E9%9D%A2UI?node-id=7136-4768&m=dev) 后，新场景采用独立 XML 布局，复用原通知构建器与点击链路，旧通知文案与内容布局保持原样；按用户最新要求，旧日龄通知按钮也共用增强呼吸/水波纹，只有背景动效与静态替代层更新。折叠态设计包含 28dp App 图标、24dp 场景图标、单一富文本文案、77dp 最小宽按钮；Android 12+ 已由系统在同一行提供 App 图标，因此隐藏自定义 App 图标，避免重复挤占文案。动画展开态使用居中 56dp 场景图标、文案、39dp 最小高按钮，减少动效时采用稿中横排展开态。系统提供 App 名称、真实时间和展开/收起控件。新场景使用独立 HIGH 渠道 `scene_smart_alerts_v1`（Smart alerts），有声音、无振动，不使用全屏 Intent。八个 ID、PendingIntent 和分组隔离，不触发旧删除重发接收器。原版图标离线转换为五档密度 WebP，源图、SHA-256 和节点来源记录在 `design/figma/scene-push/assets.json`。按用户视频参考，按钮使用 XML AnimatedVectorDrawable + objectAnimator 连续插值：1100ms 明显呼吸、两层错开 700ms 的 1400ms 扩散淡出水波纹、1400ms 单向扫光，圆角裁切不会溢出；图标原版位图采用 48 帧/960ms 平滑呼吸，双箭头采用 32 帧/960ms 平滑向内位移。均经原生 ProgressBar 承载，无 App 帧循环、重复 notify 或运行时图片转换。额外素材与 SHA-256 见 `design/figma/scene-push/layout-assets.json`；系统折叠箭头保留源文件供对照，由系统实际绘制。省电、灭屏或减少动效时构建为静态，XML 动画不靠反复 notify 驱动；循环悬浮展示由下面独立的有限重发任务管理。

后台使用系统通知；到期时前台使用可见业务页短提示，不缓存到下次前台。1101 额外以插电时前后台判定。系统仍决定实际是否弹出悬浮横幅（通知权限、用户渠道设置、勿扰及系统限频），多个 ID 可共存不代表系统会同时显示多个横幅。

Notific_Show/Click/Enter 保持旧分类协议并携带来源 `charge_start`、`charge_end`、`low_battery`、`network_change`、`screenshot`、`app_install`、`app_uninstall`、`new_download`。新场景没有标题，展示与点击埋点的 `title` 留空，`text` 保存整句的有界纯文本快照；系统标准正文、前台提示和自定义 RemoteViews 均使用同一句话，加粗和颜色分别由 HTML 标签控制，普通文字不加粗；本地默认将强调片段配置成红色加粗。点击快照在开屏即消费，跨页/重建不重复。业务元数据只传五个白名单标量，不传列表、不复制原始 extras。

## 循环悬浮展示

新场景通过 `ScenePresentationQueue` 串行取得展示位，首条成功发布后，由 `SceneRepeatCoordinator` / `SceneRepeatLoop` 持有展示位完成该通知的一轮循环；不调用旧 `checkAndStartRepeatNotification` / `stopRepeatNotification`，不共享旧 Handler、Runnable、currentNotificationId 或计数。

- 复用宿主 `repeatNotificationsEnabled`、现有 `repeat_notification_enabled`（ConfigCtrl.getHoverDurationStrategySwitch）和 `repeat_cycle_count`（ConfigCtrl.getHoverDurationLoopCount），由旧配置的当前用户 tier 提供。
- 与旧实现一致，每 4 秒更新一次同 ID 通知，循环次数是首条之后的额外次数。例：配置 2，即初次展示 + 4 秒时重复 + 8 秒时重复，再保留最后 4 秒展示窗口，12 秒时才允许下一条首次发布。循环关闭/次数为 0 时，首条仍保留 4 秒展示窗口再交棒。
- 不同场景先等待当前一轮结束，不抢占；同场景只保留最新候选，独立通知 ID 与各自间隔不变。旧推送不入队、不被队列阻塞或取消。旧卡片划除回调带独立会话 token，不会误停同 ID 的新一轮。
- 初次及第一次重复可响铃，第二次重复起使用独立 HIGH 静音渠道 `scene_smart_alerts_silent_v1`；新场景保持不振动。用户渠道设置仍优先。
- 同一轮沿用原文案、HTML 布局、点击 PendingIntent 和来源快照。不再次推进轮播、重置场景间隔、增加旧每日计数或重复上报 Notific_Show。
- 排队时不构建/发布通知、不锁定文案、不记频控；出队重新读取最新配置、历史、事实和文案，不再满足原有发布条件的触发事件跳过，若远程延长了自身间隔则释放展示位回到自身等待。触发条件只用于尚未展示候选及首条发布前校验；首条成功后进入独立展示周期，不再复核原始电池/网络/文件快照。每次重复仅复核循环开关/次数、场景开关、前后台、通知权限、展示位与本 App 中对应通知是否仍存在且 token 相符。触发源后续的状态变化/取消只撤销尚未展示候选，不中断已开始的周期。关闭场景、划除、点击自动取消、替换后不复活旧卡片。
- 划除/当前轮不再满足条件时提前交棒下一条；循环结束后原通知可留在通知栏。回前台及 CoreService 会话停止/超时/销毁会结束新场景循环并清空待播队列；队列只保存当前服务会话中尚未展示的触发事件/条件（每场景最多一项）；不缓存 Notification、RemoteViews、已选文案或已展示卡片，不因等待时长设置过期丢弃。只有取得展示位后才按当前条件生成通知并首次 notify；不持久化恢复。有限循环不借 Alarm、WorkManager 或唤醒锁维持。划除回调是服务会话内的非导出广播，不使用旧 DeleteReceiver。

循环调用的是系统通知更新机制；系统通知渠道、勿扰、冷却与 OEM 策略仍决定实际悬浮效果，不承诺横幅一定持续可见。

## 调试日志

新场景统一调用 `io.docview.push.utils.Logger`，使用原有 Tag `NotificationModule`，说明文字为中文。Android Studio Logcat 可过滤 `tag:NotificationModule message:场景推送`。

覆盖监听注册/释放、配置来源及开关、触发候选、延迟/独立间隔、队列入队/出队/清空、发布前复核、初次提交系统、计数与轮播提交、循环序号/声音状态/结束、划除与旧轮次回调等。日志保留场景中文名/编号、通知 ID 和有界计数，不打印文件路径、包名、文案、完整配置或会话 token。

沿用 `Logger` 的总开关和 debug/release 默认行为，不额外开启 release 日志；不打印动画帧或每次电池快照。纯调度器通过构造函数注入 `Logger::d`，保持 JVM 规则测试不依赖 Android Log。

## 验证

验证结果与截图见 `docs/verification/scene-push/README.md`。仅使用 local 渠道编译和模拟器测试，未发布远程配置，也未构建 google 渠道。

平台约束参考：[截图检测](https://developer.android.com/about/versions/14/features/screenshot-detection)、[共享媒体存储](https://developer.android.com/training/data-storage/shared/media)、[网络监听](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)。

## 2026-10-22 核对后调整

充电开始连接防抖为 **60 秒**（满 60 秒允许），独立于远程发送间隔；持续抖动以最近一次连接为起点。新旧通知按钮均采用增强呼吸、双层扩散水波纹和扫光，文字与点击区域不缩放。XML 由 `tools/generate_notification_motion.py` 统一维护；原图图标未重绘。减少动效、灭屏和省电时构建静态版本，隐藏后原生 ProgressBar 停止动画。

## 2026-10-22 HTML 颜色配置

客户端已移除 b/strong → 红色的固定映射。例如 `"text": "<font color=\"#FB4949\"><b>New file saved!</b></font> Manage files."`。只写 `<b>New file saved!</b> Manage files.` 时只加粗；`<font color="#12AB34">...</font>` 可以独立指定绿色，不必同时加粗。参数名、id、condition、轮播、频控及纯文本埋点不变；旧的纯 b 远程配置不会自动补红色。
