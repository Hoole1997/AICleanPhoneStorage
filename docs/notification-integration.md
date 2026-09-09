# Notification 新版迁移

## 来源与替换顺序

来源：`/Users/apple/StudioProjects/Remax_Browser`，commit `9d7e51b`。

先把来源 notification 的 100 个受版本管理文件完整复制到当前模块，再做构建与业务适配。原始文件 SHA-256 清单见 `docs/notification-source-import.json`。旧 `com.remax.notification` 实现已经删除，现在使用来源 `io.docview.push` 包下的控制器与服务。

按用户最新要求，news 模块已经移除，包括新闻请求/模型/检查/构建/调度代码、触发枚举、Remote Config 入口、新闻专用布局和图标、混淆规则及仅用于新闻图片加载的 Glide 依赖。

## 渠道配置

| 配置 | local | google |
| --- | --- | --- |
| 文件 | `app/src/local/config.properties` | `app/src/google/config.properties` |
| applicationId | `com.leafmotivation.quizguessoncolor` | `com.example.aicleanphonestorage.google`（示例） |
| Firebase | 原 `app/google-services.json` 原样移动到 `app/src/local/google-services.json` | 仅有 `google-services.json.example` |
| fcmUrl / fcmPkg | 来源 dev 的值 | 来源 prod 的值，启用前替换/确认 |
| remotePushEnabled | true | false（示例渠道不注册 Firebase、不上传 token） |
| userChannel | natural | natural |

配置和 JSON 直接放在与渠道同名的 `app/src/local/` 和 `app/src/google/`。Google Services 显式按 flavor 选择 JSON：localDebug/localRelease 共用 local 配置，googleDebug/googleRelease 共用 google 配置。不再保留 `src/debug` / `src/release` 目录；预览和诊断实现集中在 main，由 `BuildConfig.DEBUG` 控制是否启用。

app 与 notification 都定义 `distribution` 维度的 `local` / `google` flavor。模块各自读取对应配置，生成独立的 `BuildConfig.FCM_URL`、`FCM_PKG`、`REMOTE_PUSH_ENABLED`、`USER_CHANNEL`。不使用 taskNames 猜测渠道，因此同一次 Gradle 调用构建两个渠道也不会串配置。

`fcmPkg` 是后端的包路由标识，保留来源值，不会擅自用 applicationId 替代。安装分发 flavor 与付费/自然用户归因渠道分别配置；归因 SDK 未接入时，userChannel 决定默认 premium_tier / standard_tier。

google 的启用步骤：替换示例 applicationId，放入匹配的 `app/src/google/google-services.json`，确认 fcmUrl/fcmPkg，然后将 remotePushEnabled 改为 true。不要把示例 JSON 直接改后缀当成真实凭据。没有真实 JSON 时 Google Services 任务跳过，google 示例 APK 仍可编译运行。

Firebase 保持当前核验的版本：BoM 34.18.0（Messaging 25.1.2、Remote Config 23.1.0）、Google Services 插件 4.5.0。

## 完整迁入的推送链路

- `provider/Provider` 保留模块初始化入口，转交 Application 级 `NotificationRuntime` 在 IO 初始化配置、偏好和检查器；主线程只注册生命周期。
- `timing/TimingCtrl` 保留前后台、解锁、消息、token 注册和来源主题逻辑，使用 `DefaultLifecycleObserver`。
- `service/MessageService` 保留源消息触发流程，并适配新版 Firebase `onRegistered` 回调；兼容原 `onNewToken`。
- `utils/TopicMgr` 订阅 `ALL_TOKEN` 与 `ALL_TOKEN_(整数 UTC 偏移+24)`。
- `config/ConfigCtrl` / `ContentController` 保留来源资产文件名、缓存键和 Remote Config 的 `pushConfigJson` / `pushContentJson`。
- `check/CheckCtrl` / `ResetCtrl` 保留来源限次、免打扰、冷却、间隔与状态。
- `controller/TriggerCtrl` 保留来源常驻/普通/静音通道和发布流程。
- `controller/TokenUploadCtrl` 完整接入新版 token 上报协议，详见下节。
- `controller/LandingCtrl` 保留来源通知参数处理；宿主同时兼容当前常驻通知的语义路由。

来源 `common` 中的语言、偏好、配置、用户归因和事件上报接口通过 `host/` 适配，不引入整个浏览器和广告依赖。事件上报通过 `NotificationHost.onEvent` 注入；本 App 没有配置额外统计后端，不将该回调视为第三方统计已上报。

来源的地震、CoreService、ServiceMgr、Worker 和重复通知实现仍保留。当前清理 App 没有地震页面和相应数据源，且项目规则不允许为通知栏保活，因此宿主 `earthquakeEnabled`、`backgroundServiceEnabled`、`repeatNotificationsEnabled` 为 false。常驻通知仍可直接由系统 NotificationManager 展示，不需要启动前台保活服务或定时 Worker。news 已按要求实际删除。

## Token 上报协议

- GET：`${FCM_URL}/browser/wnfree`
- 参数：`wndk` = token、`weid` = 本地持久化用户 ID、`dfk` = FCM_PKG。
- Header：`seg`。
- 签名：来源同一客户端签名常量 + 参数按字母顺序拼接 + MD5，保持与新版后端一致。

传输实现进行了工程适配：OkHttp 复用客户端，URL 参数编码，单消费者/最新 token 合并，25 秒总超时，响应最多 64 KiB，关闭响应资源，保留当前成功上报的 SHA-256 指纹，重复 token 不重复成功上传。HTTP 或业务 code 失败不记成功，损坏 JSON 不误记成功。日志不打印 token、签名、用户 ID 或完整请求 URL；相关偏好排除备份。

## 通知业务

常驻通知由宿主 `CleanNotificationHost` 提供原生 RemoteViews，保留 Figma 四项布局、首页 WebP 资源和可选角标：

| 入口 | 目标 |
| --- | --- |
| 内容背景 / contentIntent | 首页 |
| Clean | Smart Cleaning 垃圾扫描 |
| Network | 网络流量 |
| Photos | 照片压缩 |
| Unused | 闲置文件候选 |

每个 PendingIntent 独立且不可变，直接打开首页 Activity，不通过广播/Service 中转。所有清理仍复用既有授权、扫描、选择和确认流程。通知 extra 消费一次，重建不重复扫描。通知标题区的展开行为由系统/OEM 决定。

普通推送沿用源模块从 ContentController 轮转配置内容的行为；FCM data 消息负责触发，不直接拿 data.title/body 替换配置文案。源模块 version 过滤原本注释关闭，这次没有改变此服务端兼容行为。后台带 notification 字段的 Firebase Console 消息仍由 Firebase SDK/系统展示。

当前业务类型只在 `Content.TYPE_*` 中定义：1 清理、2 流量、3 照片压缩、4 闲置文件、5 截图、6 首页。`NotificationDestination` 绑定这些常量，内容解析、图标选择、普通通知 PendingIntent、常驻入口和首页消费共享该业务表。`Content.destination` 与 `iconDestination` 将原 JSON 整数转为强类型；无效动作整条过滤，未知图标回退到该动作的业务图标。外部缺失/未知落地参数回首页，不触发默认清理。普通通知按业务动作区分 PendingIntent，避免更新另一类卡片时覆盖路由；操作按钮也显式绑定同一 PendingIntent。配置最多载入 64 条，限制单条文本长度，拒绝无效配置并回退本地内容。默认文案不伪造扫描结果。来源的随机角标已移除，真实角标通过 `CleanApplication.updateResidentBadges` 更新。

首页 onResume 的通知判断/请求继续使用 XXPermissions 28.3；发送前再检查权限/通道，处理撤权竞态。通知监听清理功能的权限独立不变。

## 资源转换

来源 `drawable/ic_noti_process.xml` 有约 96 万字符的 pathData，超出 Android 字符串编码限制。原始文件存档在 `design/notification-source/`，按原始路径开发时渲染为 60dp 五档透明 WebP，不进行运行时转换。转换脚本为 `tools/convert_notification_assets.py`。

## 构建和验证

```sh
./gradlew :app:assembleLocalDebug :app:assembleGoogleDebug
./gradlew :app:assembleLocalRelease :app:assembleGoogleRelease
./gradlew :app:testLocalDebugUnitTest :notification:testLocalDebugUnitTest :notification:testGoogleDebugUnitTest
./gradlew :app:lintLocalDebug :notification:lintLocalDebug
./gradlew :app:assembleLocalDebugAndroidTest
```

协议测试使用本机 MockWebServer 检查路径、参数转义、来源签名 golden 值和返回码；不会将伪造 token 发往真实后端。配置测试验证新 premium_tier / standard_tier 结构及旧结构拒绝。Android 测试验证常驻通知属性、布局/大字号/角标、PendingIntent 和 Firebase 注册。注册成功、协议单测通过与真实 token 后端确认分别核验，不混为一个结论。

业务协调验证：`ContentBusinessContractTest` 覆盖六种类型、无效浏览器动作、未知图标和实际 assets 配置；`NotificationBusinessRoutingTest` 在真机验证内容构建、六种资源映射、两类 Intent 的统一消费与独立 PendingIntent。

本次实测结果：local/google 的 Debug 和 Release 均构建通过；Lint 通过；app 78 个单元测试，notification 每个渠道 9 个单元测试通过；7 个真机测试通过，包含新增业务映射用例。Firebase token 获取和 ALL_TOKEN 主题订阅成功。真实 token 上报请求得到 HTTP 200，但当前响应未通过业务成功校验，尚不能宣称上报成功；不会将此结果计为成功或伪造成功记录。
