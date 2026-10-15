# DEBUG 广告调试

入口：设置 → DEBUG · 广告调试（英文为 `DEBUG · Advertising`）。仅 `debug` / `riskDebug` 源集编译调试页面；`release` 使用空入口，清单与 Kotlin 输出均没有两个调试 Activity。

最初核对 SDK `feature/risk` 分支提交 `1f0103ce61e73d7b637648616f541bf10dc269d1`，按 `bill:support_risk_1.0.11`、`ad-risk:1.0.8` 的实际 AAR 公开签名接入。用户随后已将 Bill 升至 `support_risk_1.0.12`，当前工作区的编译、Lint 和 344 项 app 单测通过；以下公开接口继续兼容：

- [AdSourceController](https://github.com/toukaRemax/remax_sdk/blob/feature/risk/bill/src/main/java/com/android/common/bill/ads/bidding/AdSourceController.kt)：自动竞价、AdMob、GAM、Pangle、TopOn、MAX。固定源不可用时 SDK 不降级到其他平台。
- [GlobalAdSwitchInterceptor](https://github.com/toukaRemax/remax_sdk/blob/feature/risk/bill/src/main/java/com/android/common/bill/ads/interceptor/AdInterceptor.kt)：真实广告总开关。来源与总开关由 SDK 持久化，退出/重启后仍保留；“恢复默认”恢复自动竞价和广告开启。
- [平台限制接口](https://github.com/toukaRemax/remax_sdk/blob/feature/risk/docs/remax-ad-guide/ad-risk-external-api.html)：SDK 支持 PROCESS / TODAY 独立限制。项目真实风控桥接拥有 PROCESS 限制，本工具只读诊断，不清除风险限制或行为风控，也不伪造风险检测结果。

可选择开屏、插屏、激励、全屏原生、原生、Banner，点击后仅请求一次。开屏/激励关闭插屏竞争、插屏忽略全屏原生替代、全屏原生关闭插屏替代，因此选择的广告类型不会被另一类型替换。SDK 初始化、广告位/平台配置、频控、风控和真实填充仍然生效。页面显示实际 SDK 结果、各平台缓存与频控状态；异常显示不可用，缓存可用不等同于一定产生展示。

MAX 调试器改为页面按钮触发，Application 启动时不自动弹出。调试工具文案支持英文和简体中文，其余应用语言回退英文；MissingTranslation 仅在两份 DEBUG 专用技术资源内明确限定，不改变正式页面的语言覆盖。

## 职责与生命周期

- `DebugSettingsEntry` 复用原设置图标并追加独立卡片，不修改原 Figma 五项菜单。
- `AdDebugViewModel` 保存小型格式枚举、控件状态和有界诊断字符串；通过构造函数注入 SDK 设置接口，不持有 Activity/View。SDK DataStore getter/setter 也在有限 IO dispatcher 中串行执行。
- `SdkAdDebugSettings` 使用发布 AAR 保留的公开 API，不反射混淆类，不显示广告位 ID/凭据。
- `AdDebugPreviewActivity` 是独立展示宿主；仅在 RESUMED 且有窗口焦点后消费一次用户请求。原生/Banner 保留在该页，关闭或真实退后台时销毁宿主并取消请求；SDK 自身全屏 Activity 转场不会误取消。重建不会再次请求广告。
- 当前 SDK 发布包未保留 `AdDestroyManager` 的稳定类名，使用独立 Activity 的实际 onDestroy 触发 SDK 原有展示资源清理，保留全局预加载缓存。

## 验证

- `assembleLocalDebug`、`assembleLocalDebugAndroidTest`、`lintLocalDebug` 通过，Lint 0 Error。
- `compileLocalReleaseKotlin` / `processLocalReleaseMainManifest` 通过，release 清单与 Kotlin 编译目录没有调试页面。
- `compileLocalRiskDebugKotlin` / `processLocalRiskDebugMainManifest` 通过。
- app JVM 320 项通过，含新增 12 项调试状态、互斥、错误恢复与生命周期取消测试。
- 用户指定的 Pixel 10a 模拟器：5 项设备测试通过，覆盖设置入口、真实 SDK 参数切换/重置及原值恢复、格式重建、关闭后不展示、中英 320dp/2 倍字体布局，以及真实预览请求的往返。
- 实际 AdMob 原生测试广告显示成功，SDK 返回 `native result=true`。没有点击广告创意；其他平台/格式已接入公开接口，未据此声称全部完成真实填充验证。

截图、SDK 返回值与验证日志位于 `build/ad-debug/`；这些构建产物不提交 Git。真实广告请求的设备测试需要显式传入 `-e adDebugLivePreview true`，普通界面测试不发广告请求。
