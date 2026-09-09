# Notification 模块接入

## 来源与边界

源项目： http://v4.9ms.co:3000/ReMax/ReMax_PhotoRecovery_variant_1

提取依据：commit `6ddd26cd684a4b7fc8cd87d0e74695e0c943e690` 的 `notification/`。这是适配后的独立 Android Library，保留 `com.remax.notification` 包和来源职责，不依赖源项目 `base`、广告归因或照片恢复页面。

- `service/FCMService`：FCM 数据消息接收、恢复生效的 version 匹配，通过宿主接口导航。
- `timing/NotificationTimingController`：保留前后台和系统解锁事件。
- `check/NotificationCheckController`：纯策略，保留冷却、间隔、免打扰、每天额度；常驻通知独立于推送额度。
- `config/PushConfig`、`assets/push_config.json`：沿用 organic_channel 协议；没有接入付费渠道归因。
- `config/NotificationConfigController`：支持原 `pushConfigJson` Remote Config 键。启动时冻结已激活缓存/本地默认策略，异步获取下一次进程启动使用的值；12 小时最短请求间隔，10 秒超时。无实时后台监听。
- `utils/Topic`、`utils/DateUtil`：直接提取原 ALL 与时区主题命名规则（整数 UTC 偏移 + 24）。`FCMTopicManager` 改为 SDK 任务，时区变化取消旧主题。
- `controller/NotificationTriggerController`：保留双通道与 RemoteViews，改为注入 NotificationHost。

依据本项目开发规则，去掉原轮询保活 Service/Worker、午夜计时器、重复悬浮推送、统计 SDK、设备标识上传及原照片恢复文案。没有复制来源项目的 FCM 项目、服务器地址或签名密钥。设备注册通过当前测试 Firebase 项目和主题订阅完成；未接入来源项目的私有 token 上传 API。原 `pushContentJson` 的照片恢复动作/随机文案没有直接迁入本 App；当前采用 App 多语言默认文案或 FCM data 明确提供的内容。

## 测试渠道和版本

`applicationId = com.leafmotivation.quizguessoncolor`，使用用户放在 `app/google-services.json` 的配置。Kotlin namespace 保留 `com.example.aicleanphonestorage`，无需移动所有业务源码；FileProvider authority 由 applicationId 生成。

2026-09-09 核对 Firebase 官方发布记录与 Google Maven 元数据：

- Firebase BoM 34.18.0
- Firebase Messaging 25.1.2
- Firebase Remote Config 23.1.0
- Google Services Gradle 插件 4.5.0

版本集中在 `gradle/libs.versions.toml`，Firebase 库由 BoM 锁定，使用主模块，不使用停止发布的 `-ktx` 模块。

## 常驻通知

`app/feature/push/CleanNotificationHost` 负责布局、图标、文案与四个可选角标；模块不认识首页 ViewModel 或扫描数据。

| 入口 | 目标 |
| --- | --- |
| 通知内容背景 / contentIntent | 首页 |
| Clean | SMART_CLEAN 垃圾扫描清理 |
| Network | Network Traffic 流量监控 |
| Photos | PHOTO_COMPRESS 照片压缩 |
| Unused / 闲置文件 | UNUSED_FILES 候选扫描 |

原需求“限制文件”暂按首页已有“闲置文件”实现。点击使用不同 requestCode 与 action 的 immutable Activity PendingIntent。冷启动与 onNewIntent 都复用首页入口；旧的授权/扫描互斥取消，Intent extra 只消费一次，旋转不会重扫。真正删除仍走已有用户选择和确认流程。

`CleanApplication.updateResidentBadges(ResidentBadges(...))` 提供事件更新接口，四项独立，null/空字符串隐藏，最长 8 字符。默认没有设计示例数据；不会为角标进行扫描或后台采样。

Figma： https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb?node-id=5666-1611

Clean 原图来自节点 5666:1803 的透明 image fill，存档于 `design/figma/resident/source/clean.png`，开发时生成五档 WebP。其余图标复用首页资源。系统绘制通知标题区、背景和展开箭头；部分 OEM 点击系统标题区会展开通知，属于系统行为，内容空白区和 contentIntent 返回首页；展开内容采用 36dp 图标、12sp 自然高度文字、红色 11sp 角标。收起状态适配 Android 最小 48dp 限制；大字号采用更小图标，角标在展开状态显示，辅助功能描述保留附属信息。文字使用系统通知主题支持浅色/深色背景。

使用 ongoing 通知，不假借前台服务保活。用户关闭通道/撤回权限时不发送；系统允许用户划走时遵循系统行为，下次主动打开 App 才会刷新常驻入口。不是后台强行不可移除的通知。

## 权限与推送测试

首次首页复用统一权限说明 UI，并通过 Android POST_NOTIFICATIONS 系统回调授权；拒绝后不循环弹框。设置 → 通知设置可管理应用与两个通知通道。

默认普通推送策略：每日 3 次，前后台/解锁间隔各 10 分钟，新安装冷却 24 分钟，02:00–08:00 免打扰，前台不提示。原源码 `new_user_cooldown` 的单位为分钟。按本地日历惰性换日，不用午夜唤醒。只有成功发布才计数；FCM messageId 最多保留 32 个作去重，不记录正文或 token。

FCM 后台集成使用 **data-only** 消息，经客户端策略、版本过滤和导航白名单处理。示例 HTTP v1 的 message 内容（token 在服务端填入当前测试设备 token）：

```json
{
  "message": {
    "token": "<current-test-device-token>",
    "android": { "priority": "HIGH" },
    "data": {
      "version": "1.0",
      "title": "Review your storage",
      "body": "Choose which files you want to keep.",
      "destination": "home"
    }
  }
}
```

白名单 destination：`home`, `clean`, `network`, `photos`, `unused_files`, `screenshots`。未知值回首页。省略 version 为全量版本，指定时必须等于 versionName。只有 version 的原触发消息也可工作，文案回落到本 App 的多语言资源。带 notification 字段的 Firebase Console 消息在后台由 Firebase SDK/系统直接展示，不经过此 data-only 策略；其默认点击回首页。

## 验证

- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :notification:testDebugUnitTest :app:lintDebug :notification:lintDebug`
- `ResidentNotificationTest`：RemoteViews 真机 apply、280/343/600dp × 1/1.3/2 倍字体 × 收起/展开，文字裁切检查、独立 PendingIntent、角标边界和系统通知属性。
- `FirebaseRegistrationTest`：在线确认测试包名、Firebase 初始化、成功获取非空 FCM token；不输出 token，不向 ALL 或其他设备发送消息。
- 截图来自 Android 15 真机。没有将渲染测试或架构设计表述为 ANR/功耗性能测量。
- 未从服务端发出端到端 FCM 消息；设备注册成功不等于服务端投递联调已完成。
