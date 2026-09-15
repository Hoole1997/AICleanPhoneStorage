# 广告和统计 SDK 接入

来源：`/Users/apple/Documents/StreetHtml` 的 `com.example.lcb.app.ad`。迁入代码位于 `com.example.aicleanphonestorage.app.ad`，由 `CleanApplication.onCreate` 调用 `AdSdkInitializer.initialize`。

- 固定依赖 `com.github.toukaremax:core:1.0.15`、`com.github.toukaremax:bill:1.0.51`。
- bill 排除 `com.ironsource.sdk:mediationsdk`，保留依赖树中的 Unity Mediation 9.2.0。
- GitHub Packages 凭据从当前 `local.properties` 的 `github.user`、`github.token` 或 CI 环境读取。凭据不提交 Git；来源实际存放位置是 StreetHtml 的 `build.config.properties`。
- 两个 SDK 的最低 Android 版本为 API 26，app 的 minSdk 相应调整为 26，Java 编译目标为 17。
- `app/src/local/config.gradle` 迁入来源 local 广告和统计配置；`app/src/google/config.gradle` 定义相同结构并将广告及统计标识留空。两个脚本导出独立 Map，避免多渠道构建串用 ID。原应用/Firebase/FCM 配置继续保存在各渠道已有文件中。
- 按来源注册 AdMob、GAM、Pangle、TopOn 原生/全屏原生渲染器及 loading renderer。来源逐图片创建线程的实现改为 Glide 绑定 View 生命周期并限制图标尺寸。
- core 的 ReporterData 接入 Firebase 与 ThinkingData；真实收益通过 RevenueAdReporter 接入 Firebase。Adjust 配置键保留，当前不引入来源 Launcher SDK 或伪造归因回调。
- local、google 均调用 `AppOpenBiddingInitializer.initialize`，在其配置回调中安装本地默认配置及渲染器；本地广告 ID 为空时也由 SDK 拉取云端配置。
- 展示入口使用 `AdExt.loadInterstitial`，请求、展示资格和频次规则由扩展方法及 SDK 处理。启动页使用 `AdExt.loadSplash` 的回调续接首页，详见 `docs/startup-page.md`。

## 插屏业务入口

- 垃圾清理、截图清理、大文件、未使用文件：点击清理按钮只准备并展示确认框，取消不请求广告。点击确认后调用 `AdExt.loadInterstitial`，收到 `call` 后再执行已确认的操作；关闭广告或展示失败均继续业务。压缩原图的单独删除确认遵守同样的时机。
- `CleanupOperationCoordinator` 统一承接确认与广告回调，等待期间记录确认时的操作 ID，ViewModel 在回调后验证 ID。`InterstitialActions` 防止连点及重复回调，并在当前 Activity 恢复后执行；不把 Activity 或闭包放进 ViewModel，进程重启不重放清理操作。
- 上述五个功能，以及通知清理、应用管理、流量：顶部返回和系统返回先回首页，功能页不展示退出广告。内部垃圾分类返回上级页不触发首页广告。
- 清理、压缩、通知清理完成页：顶部返回、系统返回及继续按钮立即把结果交回功能页，再带完成页来源回首页。删除压缩原图按钮仍回到单独确认，不视为退出首页。
- `HomeExitAdContract` 通过 Intent 的 `home.exit_ad.placement` 标记来源，`home.exit_ad.token` 标记本次退出。广告位例如 `screenshots_exit_interstitial`（截图功能页）、`photo_compress_complete_exit_interstitial`（压缩完成页）、`network_traffic_exit_interstitial`（流量页）；首页将同一来源传给 `AdExt.positionName`，并输出 `CleanAds: Home exit interstitial: source=...`。
- `HomeExitAdCoordinator` 同时处理首页 `onCreate` / `onNewIntent`，等待 RESUMED、窗口焦点和首页首帧绘制，再调用广告。权限弹窗或退后台时等待恢复事件，不用固定延时或轮询。请求之前消费来源标记，SavedStateHandle 防止重建或重复 Intent 再次展示；完成页回传来源，兼容父页索引尚未恢复的情况。
- 初始化和展示入口均不判断本地广告 ID 是否为空；SDK 负责云端广告 ID、请求时限、频次与展示规则。应用层不添加初始化超时来取消 SDK 的配置获取。
- 统计初始化作为独立协程运行，不能阻止 SDK 初始化；`CleanAds` 标签输出初始化开始、SDK 调用、配置安装和最终结果，重复调用也会输出当前状态。

验证命令（D8 处理聚合广告依赖时使用 4GB Gradle 堆及两个 worker，避免默认 2GB 的 GC thrashing）：

```sh
./gradlew :app:assembleLocalDebug :app:assembleGoogleDebug :app:testLocalDebugUnitTest :app:lintLocalDebug --max-workers=2 -Dorg.gradle.jvmargs='-Xmx4096m -Dfile.encoding=UTF-8'
```

构建、单元测试和 Lint 的验证结果不代表广告填充成功。`InterstitialContinuationTest` 使用注入的假请求验证重复回调、后台恢复和 Activity 重建续接；`CompletionDeviceTest` 的完整业务流程会调用真实 SDK，需在明确的广告测试环境手动运行，不能将 google 的空本地 ID 当成无广告环境。真实广告填充、展示和关闭需单独验证。

`AdTimingDeviceTest` 使用假 SDK 验证确认取消、确认后等待回调、首页恢复并绘制后请求及来源去重；`InterstitialContinuationTest` 验证失败/重复回调与页面重建续接。单元测试覆盖确认操作 ID 和退出来源在状态恢复后的消费行为。完整 `CompletionDeviceTest` 会请求真实 SDK，仅在明确的广告测试环境运行。

本轮时机调整已通过 local/google Debug 构建、单元测试、local Lint，以及上述两组共 4 项 API 32 模拟器测试；展示请求采用假回调，未据此验证真实广告填充。

local 真机冷启动已观察到 SDK 初始化从 STARTED 到 READY；这只表示 SDK 返回成功，不代表所有广告网络已填充或广告展示成功。

设备检查另发现：API 37 模拟器启动时显示原生库 16KB 兼容模式提示，包含 `liballiance.so` 的 RELRO segment not aligned 等条目。测试环境关闭该系统提示后继续流程验证；这不代表原生库兼容性已解决，需要单独检查 SDK 所带二进制库。

照片压缩按最新交互：点击 Compress (N) 后冻结选择并直接请求现有压缩插屏，广告回调后开始创建副本，不再展示压缩前确认框。
