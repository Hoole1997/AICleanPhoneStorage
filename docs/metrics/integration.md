# metrics 模块接入

## 渠道配置

app 与 metrics 均读取 `app/src/local/config.gradle`、`app/src/google/config.gradle` 中的 `analytics`，按 `distribution` flavor 生成各自的 BuildConfig，不再读取全局 `findProperty("analytics")`。Debug/Release 由构建类型决定，与渠道独立。

| 字段 | local | google |
| --- | --- | --- |
| ADJUST_APP_TOKEN | nktpi1ta3474 | 空字符串占位 |
| THINKING_DATA_APP_ID | 871c0ebde6be4329b9b74acb3f08ba36 | 空字符串占位 |
| THINKING_DATA_SERVER_URL | https://ds.devs343.com | 空字符串占位 |
| DEFAULT_USER_CHANNEL | paid | natural |

更换渠道参数时只编辑对应 app 渠道脚本；metrics 不另放一份配置，也不复制 Firebase JSON。

## 构建与依赖

- settings 注册 `:metrics`，app 显式依赖该模块。
- metrics 使用 AGP 9 内置 Kotlin、compileSdk 37、minSdk 26、JVM 17，与现有应用适配。
- Firebase Analytics 使用项目统一 Firebase BoM；模块直接声明所需的 Gson、协程及 core 依赖。不引入未存在的 common 模块，也不额外引入完整 bill 广告栈。
- 保留复制模块的 Adjust 5.4.3、Install Referrer 2.2、广告标识库 18.0.1、ThinkingData 3.0.2 版本，本次不做无关 SDK 升级。
- 去掉无用界面资源和库自己的 app_name；consumer 规则只保留本模块反射使用的 RevenueConfigItem。Adjust、ThinkingData 等 SDK 使用各自 AAR 内附的混淆规则，不向宿主导出全局 keep、dontwarn ** 或 ignorewarnings。

## 初始化

Application 的现有 `AdSdkInitializer` 独立统计初始化协程 → `AdAnalytics.initialize` → `MetricsModuleProvider.initialize`。

Provider 只提供 Application Context，不在 Provider.onCreate 阻塞初始化 SDK。ThinkingData 初始化和公共参数存储读取放在 I/O 线程，Adjust SDK 初始化在主线程执行；入口通过 Mutex 保证幂等。应用层不再额外注册一套 Firebase/ThinkingData 上报器，防止覆盖 metrics 的 Adjust/Firebase 收益上报器。

公共参数保留 distribution、build_type，并与 metrics 的登录、归因参数合并。google 未配置 Adjust/ThinkingData 时跳过对应初始化和上报器；Firebase 上报器仅在 FirebaseApp 已存在时注册。日志沿用 `AnalyticsModule`，宿主初始化状态仍可查 `CleanAds`。

这种初始化调用位置与 [Adjust 官方集成文档](https://dev.adjust.com/en/sdk/android/) 推荐的 Application 入口一致。业务事件继续通过既有 `AdAnalytics.report` 或 core 的 ReportDataManager 转发。

本次验证区分配置与客户端初始化、后台实际收数；初始化成功不代表已验证 Adjust 后台归因或 ThinkingData/Firebase 收数。

## 验证结果

- localDebug、googleDebug、localRelease、googleRelease 四个 app APK 均构建通过，包括 Release 的 R8 混淆。
- 逐项核对四个变体的 app/metrics BuildConfig：三项 SDK 配置及 DEFAULT_USER_CHANNEL 完全一致；local 使用提供值，google 为既有空占位与 natural 默认值。
- 141 项 app 单元测试通过；app localDebug、metrics localDebug/googleDebug Lint 均无错误（分别有 137、9、9 条警告）。Release 构建仍有第三方 SDK 的 R8 元数据/可选缺失类警告，未新增全局忽略规则掩盖它们。
- API 32 模拟器初始化测试通过：Provider 可解析、配置一致、多次并发初始化不重复注册；本次 local 日志显示 firebase=true、thinking=true、adjust=true，RevenueAdManager 保持两个上报器。
- 未验证服务端归因结果或后台实际收到的事件。未改动 UI，本次无需视觉截图检查。
