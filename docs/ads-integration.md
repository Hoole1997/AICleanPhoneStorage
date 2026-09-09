# 广告和统计 SDK 接入

来源：`/Users/apple/Documents/StreetHtml` 的 `com.example.lcb.app.ad`。迁入代码位于 `com.example.aicleanphonestorage.app.ad`，由 `CleanApplication.onCreate` 调用 `AdSdkInitializer.initialize`。

- 固定依赖 `com.github.toukaremax:core:1.0.15`、`com.github.toukaremax:bill:1.0.51`。
- bill 排除 `com.ironsource.sdk:mediationsdk`，保留依赖树中的 Unity Mediation 9.2.0。
- GitHub Packages 凭据从当前 `local.properties` 的 `github.user`、`github.token` 或 CI 环境读取。凭据不提交 Git；来源实际存放位置是 StreetHtml 的 `build.config.properties`。
- 两个 SDK 的最低 Android 版本为 API 26，app 的 minSdk 相应调整为 26，Java 编译目标为 17。
- `app/src/local/config.gradle` 迁入来源 local 广告和统计配置；`app/src/google/config.gradle` 定义相同结构并将广告及统计标识留空。两个脚本导出独立 Map，避免多渠道构建串用 ID。原应用/Firebase/FCM 配置继续保存在各渠道已有文件中。
- 按来源注册 AdMob、GAM、Pangle、TopOn 原生/全屏原生渲染器及 loading renderer。来源逐图片创建线程的实现改为 Glide 绑定 View 生命周期并限制图标尺寸。
- core 的 ReporterData 接入 Firebase 与 ThinkingData；真实收益通过 RevenueAdReporter 接入 Firebase。Adjust 配置键保留，当前不引入来源 Launcher SDK 或伪造归因回调。
- google 广告 ID 为空时不初始化广告网络；渲染器和配置结构仍已注册。
- 本次只初始化 SDK、渲染器和统计出口，未添加任何广告加载/展示调用，启动页仍按已有无广告过渡逻辑运行。

验证状态：依赖解析、两个渠道 Kotlin 编译及重复类检查通过；完整 APK 打包在 D8 阶段因 Gradle 2GB 堆内存不足、GC thrashing 中断，尚未完成本轮完整打包与真机初始化验证。不能将配置接入表述为广告填充/展示验证成功。
