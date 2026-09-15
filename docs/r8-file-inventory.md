# R8 逐文件静态扫描清单

由 `python3 tools/r8/audit_files.py` 生成。

范围：三个模块中 rg 可见的所有源码、测试、XML、配置及资源；排除 build、Git 忽略文件和本机凭据。
文本文件完整读取，XML/JSON 逐个解析；二进制仅核对类型与大小。命中行包括 import/注释，须结合 [审计结论](r8-audit.md) 判断。
这份自动清单不代表逐行人工语义审查，也不替代设备验证。

共 1033 个文件。

| 模块 | 类型 | 数量 |
| --- | --- | ---: |
| app | JSON | 3 |
| app | XML | 290 |
| app | 二进制资源 | 306 |
| app | 构建/配置 | 7 |
| app | 测试源码 | 92 |
| app | 生产源码 | 205 |
| metrics | JSON | 2 |
| metrics | XML | 1 |
| metrics | 构建/配置 | 2 |
| metrics | 测试源码 | 1 |
| metrics | 生产源码 | 9 |
| notification | JSON | 2 |
| notification | XML | 38 |
| notification | 二进制资源 | 19 |
| notification | 构建/配置 | 2 |
| notification | 测试源码 | 6 |
| notification | 生产源码 | 48 |

## app

| 文件 | 字节数 | 类型 | 检查项 |
| --- | ---: | --- | --- |
| `app/build.gradle.kts` | 7332 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/AdLoadingAnimationDeviceTest.kt` | 9902 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/AdTimingDeviceTest.kt` | 8938 | 测试源码 | 框架恢复/工厂 L9,115,120,121 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/AppManagerDeviceTest.kt` | 17055 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/BuglistAcceptanceDeviceTest.kt` | 15518 | 测试源码 | 框架恢复/工厂 L14,136,138 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/BusinessMetricsDeviceTest.kt` | 5232 | 测试源码 | 框架恢复/工厂 L6,14,41,43,44 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/CleanupFeatureDeviceTest.kt` | 19074 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/CompletionDeviceTest.kt` | 15482 | 测试源码 | 类名依赖 L269；框架恢复/工厂 L14,32,220,223,271 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/CompressButtonDeviceTest.kt` | 9050 | 测试源码 | 框架恢复/工厂 L11,99,115 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/CompressionQualityLayoutTest.kt` | 3585 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/CompressionSizeDeviceTest.kt` | 9760 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/CoreServiceDeviceTest.kt` | 4158 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/DirectCompressionDeviceTest.kt` | 5591 | 测试源码 | 框架恢复/工厂 L6,48,50 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/EmptyStateDeviceTest.kt` | 14048 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/EntryStateObserverDeviceTest.kt` | 2606 | 测试源码 | 框架恢复/工厂 L23,25 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/FirebaseRegistrationTest.kt` | 1155 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/HomeCleaningDeviceTest.kt` | 6090 | 测试源码 | 框架恢复/工厂 L44 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/HomeLifecycleTest.kt` | 3150 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/HomeMetricsDeviceTest.kt` | 7051 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/HomeMotionDeviceTest.kt` | 6460 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/HomeRenderingTest.kt` | 6030 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/HotStartAdDeviceTest.kt` | 10556 | 测试源码 | 框架恢复/工厂 L130,136 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/InterstitialContinuationTest.kt` | 2526 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/JunkCleanerDeviceTest.kt` | 14932 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/JunkDocumentTreeDeviceTest.kt` | 9166 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/JunkFourCategoriesDeviceTest.kt` | 8058 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/JunkSelectionDeviceTest.kt` | 5440 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/JunkStorageAccessDeviceTest.kt` | 8918 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/LanguageDeviceTest.kt` | 8042 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/LoadingCapacityDeviceTest.kt` | 8134 | 测试源码 | 框架恢复/工厂 L98,104,119 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/LoadingProgressDeviceTest.kt` | 3163 | 测试源码 | 框架恢复/工厂 L30 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/MetricsIntegrationTest.kt` | 2279 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/NativeAdDeviceTest.kt` | 12131 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/NotificationBusinessRoutingTest.kt` | 3567 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/NotificationDraftDeviceTest.kt` | 3745 | 测试源码 | 框架恢复/工厂 L4,9,41,42,44 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/NotificationLaunchTelemetryDeviceTest.kt` | 5168 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/NotificationPermissionDeviceTest.kt` | 3620 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/PermissionUiDeviceTest.kt` | 8156 | 测试源码 | 框架恢复/工厂 L54,130,143,160,166 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/PushPermissionGuideTest.kt` | 8307 | 测试源码 | 框架恢复/工厂 L10,65,89 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/PushPermissionTelemetryDeviceTest.kt` | 6375 | 测试源码 | 框架恢复/工厂 L3,44,62,86 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/RatingPromptDeviceTest.kt` | 7972 | 测试源码 | 框架恢复/工厂 L40,43,79,81,82,83,84 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/ResidentAppCountDeviceTest.kt` | 3401 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/ResidentDismissalDeviceTest.kt` | 3831 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/ResidentNotificationTest.kt` | 7338 | 测试源码 | 资源/动画 L25,58,86 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/ScanDialogNativeAdTest.kt` | 6625 | 测试源码 | 框架恢复/工厂 L15,34,38,75,79 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/ScreenshotSummaryDeviceTest.kt` | 14035 | 测试源码 | 框架恢复/工厂 L11,84,102,119,132,137 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/SettingsDeviceTest.kt` | 16818 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/StartupActivityFlowTest.kt` | 5109 | 测试源码 | 类名依赖 L79,85 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/StartupAdCoordinatorTest.kt` | 10980 | 测试源码 | 框架恢复/工厂 L136,208,222,226,230 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/StartupNavigationTest.kt` | 6454 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/StartupRenderingTest.kt` | 4613 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/StartupTransitionDeviceTest.kt` | 4534 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/TrafficFeatureDeviceTest.kt` | 7387 | 测试源码 | 框架恢复/工厂 L17,79,84,104 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/TrafficRepositoryDeviceTest.kt` | 5743 | 测试源码 | 框架恢复/工厂 L6,13,75 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/UnusedFilesDeviceTest.kt` | 10039 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/UnusedFilesUiDeviceTest.kt` | 4767 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/androidTest/java/com/example/aicleanphonestorage/testing/NoHotStartAdsRule.kt` | 759 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/google/config.gradle` | 3321 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `app/src/google/config.properties` | 308 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `app/src/google/google-services.json.example` | 460 | JSON | 已解析 JSON；配置 DTO 见审计正文，Firebase/Lottie 交由 SDK 规则 |
| `app/src/local/config.gradle` | 4153 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `app/src/local/config.properties` | 298 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `app/src/local/google-services.json` | 709 | JSON | 已解析 JSON；配置 DTO 见审计正文，Firebase/Lottie 交由 SDK 规则 |
| `app/src/main/AndroidManifest.xml` | 5512 | XML | Manifest .app.CleanApplication；Manifest .app.MainActivity；Manifest .core.ui.completion.CompletionActivity；Manifest .feature.appmanager.ui.AppManagerActivity；Manifest .feature.filecleaner.ui.FileCleanupActivity；Manifest .feature.junkcleaner.ui.JunkCleaningActivity；Manifest .feature.networktraffic.ui.NetworkTrafficActivity；Manifest .feature.notifications.service.NotificationCleanerService；Manifest .feature.notifications.ui.NotificationCleanerActivity；Manifest .feature.settings.AboutActivity；Manifest .feature.settings.FeedbackActivity；Manifest .feature.settings.LanguageSettingsActivity；Manifest .feature.settings.SettingsActivity；Manifest .feature.startup.StartupActivity；Manifest androidx.core.content.FileProvider |
| `app/src/main/assets/licenses/Roboto-OFL.txt` | 4393 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `app/src/main/java/com/example/aicleanphonestorage/app/AppContainer.kt` | 4835 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/CleanApplication.kt` | 2537 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/MainActivity.kt` | 18489 | 生产源码 | 框架恢复/工厂 L33,36,38,44,50,53,58,61,68,69,72,74,84,87,95,99,108,111,118,121,227,260,272,281 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/AdAnalytics.kt` | 823 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/AdConfiguration.kt` | 5253 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/AdExt.kt` | 4597 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/AdSdkInitializer.kt` | 3576 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/FeatureExitCoordinator.kt` | 808 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/HomeExitAdContract.kt` | 1513 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/HomeExitAdCoordinator.kt` | 3348 | 生产源码 | 框架恢复/工厂 L8,12,23,70 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/HomeExitAdState.kt` | 1494 | 生产源码 | 框架恢复/工厂 L3,4,7 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/HotStartAdCoordinator.kt` | 6855 | 生产源码 | 类名依赖 L62,117,125 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/HotStartAdEligibility.kt` | 3505 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/HotStartAdLog.kt` | 1247 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/HotStartState.kt` | 702 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/InterstitialActionState.kt` | 1357 | 生产源码 | 框架恢复/工厂 L3,10 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/InterstitialActions.kt` | 2677 | 生产源码 | 框架恢复/工厂 L7,22,44 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/InterstitialPlacements.kt` | 1378 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/NativeAdCoordinator.kt` | 2790 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/NativeAdPlacements.kt` | 1744 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/AdIconLoader.kt` | 520 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/AdLoadingAnimationView.kt` | 4157 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultAdLoadingDialogRenderer.kt` | 936 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultAdmobFullScreenNativeAdRenderer.kt` | 2687 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultAdmobNativeAdRenderer.kt` | 1907 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultGamFullScreenNativeAdRenderer.kt` | 2493 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultGamNativeAdRenderer.kt` | 1884 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultPangleFullScreenNativeAdRenderer.kt` | 2350 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultPangleNativeAdRenderer.kt` | 1977 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultToponFullScreenNativeAdRenderer.kt` | 2373 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/ad/renderer/DefaultToponNativeAdRenderer.kt` | 1997 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/app/analytics/FeatureTelemetry.kt` | 2353 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/analytics/BusinessTelemetry.kt` | 3204 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/analytics/MetricEvent.kt` | 2203 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/analytics/PageTelemetry.kt` | 3099 | 生产源码 | 框架恢复/工厂 L7,8,30,65 |
| `app/src/main/java/com/example/aicleanphonestorage/core/coroutines/AppDispatchers.kt` | 404 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/coroutines/TaskExecutor.kt` | 1329 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/data/OneShotTransfer.kt` | 851 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/data/apps/InstalledAppCountMonitor.kt` | 2944 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/data/apps/InstalledAppCountRepository.kt` | 1507 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/data/apps/InstalledAppsReader.kt` | 2731 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/data/apps/LauncherAppCountSource.kt` | 1013 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/diagnostics/PerformanceDiagnostics.kt` | 925 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/format/StorageSizeFormatter.kt` | 717 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/lifecycle/ForegroundTransitionGuard.kt` | 1281 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/locale/AppLanguageController.kt` | 4167 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/locale/AppLanguages.kt` | 1606 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/locale/LanguageActivityCallbacks.kt` | 2265 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/locale/LanguagePreferences.kt` | 1387 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/permissions/PermissionAccess.kt` | 5191 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/permissions/PermissionCoordinator.kt` | 9383 | 生产源码 | 框架恢复/工厂 L25,28,55,131,146,147 |
| `app/src/main/java/com/example/aicleanphonestorage/core/permissions/PermissionDialogFragment.kt` | 6093 | 生产源码 | 框架恢复/工厂 L12,17,145 |
| `app/src/main/java/com/example/aicleanphonestorage/core/permissions/PermissionFlowViewModel.kt` | 11182 | 生产源码 | 框架恢复/工厂 L3,4,34,36,39 |
| `app/src/main/java/com/example/aicleanphonestorage/core/permissions/PermissionSettingsNavigator.kt` | 4450 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/apps/AppIconLoader.kt` | 1821 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionActivity.kt` | 6553 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionBurstView.kt` | 1981 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionContract.kt` | 2833 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionMotion.kt` | 2583 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionRenderer.kt` | 5039 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/completion/CompletionReport.kt` | 988 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/empty/EmptyStateView.kt` | 2607 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/AdPlaceholderView.kt` | 580 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/ContinuousEntryProgress.kt` | 3728 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/EntryProgressTimeline.kt` | 413 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/EntryStateObserver.kt` | 639 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/LoadingCapacityRenderer.kt` | 2847 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/LoadingDialogScrollView.kt` | 1117 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/TaskLoadingDialogFragment.kt` | 8282 | 生产源码 | 框架恢复/工厂 L16,31,35,209；资源/动画 L3,37,161 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/TimedEntryLoader.kt` | 4039 | 生产源码 | 框架恢复/工厂 L73 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/loading/TimedEntryProgress.kt` | 2783 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/motion/MotionPreferences.kt` | 2738 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/core/ui/motion/ShimmerPillButton.kt` | 4855 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/data/AppManagerModels.kt` | 2614 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/data/AppManagerRepository.kt` | 7265 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppDetailsSettings.kt` | 982 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerActions.kt` | 4265 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerActivity.kt` | 8038 | 生产源码 | 框架恢复/工厂 L38,41,51,54,125 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerAdapter.kt` | 5385 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerEntryCoordinator.kt` | 2858 | 生产源码 | 框架恢复/工厂 L14,19,30,49 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerEntryViewModel.kt` | 3430 | 生产源码 | 框架恢复/工厂 L3,22,25 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerPresentation.kt` | 3312 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerSortControls.kt` | 2064 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/appmanager/ui/AppManagerViewModel.kt` | 3311 | 生产源码 | 框架恢复/工厂 L3,4,22,26,28 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/analytics/CleanupTelemetry.kt` | 7247 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/CleanupModels.kt` | 4420 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/DirectoryScanIndex.kt` | 2663 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/FileScanRepository.kt` | 8679 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/data/ScanIndex.kt` | 24064 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/operations/EmptyDirectoryDeleter.kt` | 4127 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/operations/FileContentAccess.kt` | 4933 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/operations/FileOperationEngine.kt` | 13258 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/operations/ImageContentChecks.kt` | 1748 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/operations/PhotoCompressor.kt` | 9505 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/scan/CleanupAccess.kt` | 3814 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/scan/DirectStorageScanner.kt` | 4666 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/scan/DocumentAccessPolicy.kt` | 894 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/scan/DocumentTreeScanner.kt` | 6725 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/scan/FileScanSources.kt` | 6735 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupActionRenderer.kt` | 2876 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupCompletionReport.kt` | 1425 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupEntryCoordinator.kt` | 5848 | 生产源码 | 框架恢复/工厂 L15,36,56,79 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupEntryViewModel.kt` | 6345 | 生产源码 | 框架恢复/工厂 L3,4,35,37,38 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupFilesAdapter.kt` | 8866 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupFilters.kt` | 5848 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupListStateRenderer.kt` | 3279 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupMessageDialog.kt` | 4062 | 生产源码 | 框架恢复/工厂 L6,9,10 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupOperationCoordinator.kt` | 9895 | 生产源码 | 框架恢复/工厂 L23,63,113,126 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupPresentation.kt` | 1190 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupThumbnailLoader.kt` | 4404 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupViewModel.kt` | 14360 | 生产源码 | 框架恢复/工厂 L4,5,42,45,49 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CompressionQualityDialog.kt` | 5853 | 生产源码 | 框架恢复/工厂 L20,24,25 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/FileCleanupActivity.kt` | 16225 | 生产源码 | 框架恢复/工厂 L49,52 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/ScanScopeText.kt` | 616 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/ScreenshotCleanupRenderer.kt` | 1475 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/data/AndroidHomeOverviewRepository.kt` | 1821 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/data/HomeCleaningState.kt` | 2566 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/data/HomeCleaningSync.kt` | 2056 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/data/HomeFileMetricsSource.kt` | 2394 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/data/HomeOverview.kt` | 1219 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/data/HomeOverviewRepository.kt` | 695 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/data/HomePlatformMetricsSource.kt` | 5457 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/data/HomeToolMetrics.kt` | 1451 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/preview/HomePreviewSupport.kt` | 3755 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeContent.kt` | 4765 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeEntryActions.kt` | 1972 | 生产源码 | 框架恢复/工厂 L4,6,7,8,13,14,15,16 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeGridSpacing.kt` | 1982 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeListAdapter.kt` | 10739 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeRenderer.kt` | 5632 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeRow.kt` | 931 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeUiActions.kt` | 468 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeUiState.kt` | 625 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/HomeViewModel.kt` | 2989 | 生产源码 | 框架恢复/工厂 L3,22,25 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/home/ui/motion/HomeHeroMotion.kt` | 3775 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/data/JunkIndex.kt` | 4696 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/data/JunkModels.kt` | 5301 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/data/JunkPhotoAnalyzer.kt` | 8233 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/data/JunkSummaryRepository.kt` | 1799 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/ui/JunkCategoriesAdapter.kt` | 7330 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/ui/JunkCleaningActivity.kt` | 7825 | 生产源码 | 框架恢复/工厂 L42,45,54,56 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/ui/JunkOverviewViewModel.kt` | 873 | 生产源码 | 框架恢复/工厂 L3,14 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/ui/JunkPresentation.kt` | 1510 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/data/AndroidNetworkTrafficRepository.kt` | 2981 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/data/AndroidTrafficDataSource.kt` | 4913 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/data/AppMetadataDataSource.kt` | 1806 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/data/NetworkTrafficRepository.kt` | 250 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/data/TrafficModels.kt` | 3048 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/data/TrafficSnapshotTransfer.kt` | 314 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/data/UsageAccessChecker.kt` | 314 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/EntryLoadingProgress.kt` | 1132 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/NetworkTrafficActivity.kt` | 7151 | 生产源码 | 框架恢复/工厂 L38,40 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/NetworkTrafficEntryCoordinator.kt` | 6094 | 生产源码 | 框架恢复/工厂 L9,14,17,41,44,48,51,95,99,100,114,117,118 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/NetworkTrafficRenderer.kt` | 5108 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/NetworkTrafficViewModel.kt` | 9881 | 生产源码 | 框架恢复/工厂 L3,4,42,44,50 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/TrafficEntryDialogFragment.kt` | 1902 | 生产源码 | 框架恢复/工厂 L6,10,11,27 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/TrafficListAdapter.kt` | 12073 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/TrafficRefreshProgress.kt` | 3578 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/networktraffic/ui/TrafficRowDiff.kt` | 1628 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/data/NotificationAccess.kt` | 645 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/data/NotificationAppsRepository.kt` | 2626 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/data/NotificationRulesStore.kt` | 2085 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/service/NotificationCleanerService.kt` | 7568 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/service/NotificationClearPolicy.kt` | 730 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/service/NotificationListenerConnection.kt` | 478 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/service/NotificationRemovalTracker.kt` | 896 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/ui/NotificationAppsAdapter.kt` | 8211 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/ui/NotificationCleanerActivity.kt` | 7939 | 生产源码 | 框架恢复/工厂 L33,37 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/ui/NotificationCleanerRenderer.kt` | 2299 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/ui/NotificationCleanerViewModel.kt` | 13488 | 生产源码 | 框架恢复/工厂 L3,4,56,59,63 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/ui/NotificationEntryCoordinator.kt` | 4440 | 生产源码 | 框架恢复/工厂 L10,16,28,31,68,71,78,79,82,83 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/notifications/ui/NotificationEntryDialogFragment.kt` | 1571 | 生产源码 | 框架恢复/工厂 L6,10,22 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/CleanNotificationHost.kt` | 8461 | 生产源码 | 资源/动画 L6,97,102,106,130 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/NotificationClickContext.kt` | 1075 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/NotificationLaunchTelemetry.kt` | 3010 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/NotificationNavigation.kt` | 3549 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/PushPermissionCoordinator.kt` | 6820 | 生产源码 | 框架恢复/工厂 L10,14,118 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/PushPermissionGuideDialog.kt` | 3351 | 生产源码 | 框架恢复/工厂 L13,16 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/PushPermissionRequester.kt` | 2704 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/PushPermissionTelemetry.kt` | 3267 | 生产源码 | 框架恢复/工厂 L3,20,22 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/PushPermissionViewModel.kt` | 4317 | 生产源码 | 框架恢复/工厂 L3,4,22 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/push/ResidentClickTelemetry.kt` | 1179 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/rating/PlayReviewLauncher.kt` | 1865 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/rating/RatingPromptCoordinator.kt` | 5445 | 生产源码 | 框架恢复/工厂 L7,8,20,31,32,76 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/rating/RatingPromptDialog.kt` | 5139 | 生产源码 | 框架恢复/工厂 L15,18 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/rating/RatingPromptStore.kt` | 1178 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/rating/RatingPromptViewModel.kt` | 2973 | 生产源码 | 框架恢复/工厂 L3,4,15,17,19 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/AboutActivity.kt` | 2150 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/FeedbackActivity.kt` | 3104 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/LanguageOptionsAdapter.kt` | 3603 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/LanguageSettingsActivity.kt` | 3903 | 生产源码 | 框架恢复/工厂 L24,26 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/LanguageSettingsViewModel.kt` | 1953 | 生产源码 | 框架恢复/工厂 L3,21,22 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/SettingsActivity.kt` | 2497 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/SettingsDestinations.kt` | 2094 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/SettingsItemView.kt` | 7197 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/settings/SettingsPage.kt` | 1843 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/startup/StartupActivity.kt` | 7909 | 生产源码 | 框架恢复/工厂 L23,26,33,37,41,44,51,52,69 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/startup/StartupAdCoordinator.kt` | 4636 | 生产源码 | 框架恢复/工厂 L20,39 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/startup/StartupNavigation.kt` | 4094 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/startup/StartupNetworkMonitor.kt` | 3222 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/startup/StartupRenderer.kt` | 4507 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/startup/StartupViewModel.kt` | 8186 | 生产源码 | 框架恢复/工厂 L3,4,35,36,39 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/unused/data/UnusedClassifier.kt` | 5071 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/unused/data/UnusedPackages.kt` | 900 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/unused/data/UnusedRules.kt` | 1322 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/java/com/example/aicleanphonestorage/feature/unused/ui/UnusedGroupsAdapter.kt` | 3021 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/main/keepRules/rules.keep` | 1091 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `app/src/main/res/anim/startup_home_enter.xml` | 392 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/anim/startup_home_exit.xml` | 314 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/animator/home_clean_press.xml` | 1092 | XML | android:propertyName=scaleX；android:propertyName=scaleY |
| `app/src/main/res/color/app_manager_sort_background.xml` | 171 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/color/app_manager_sort_text.xml` | 171 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/color/cleanup_action.xml` | 185 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/color/cleanup_selection_action_background.xml` | 198 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/color/cleanup_selection_action_text.xml` | 198 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/color/quality_option_text.xml` | 207 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/color/traffic_filter_background.xml` | 198 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/color/traffic_filter_text.xml` | 198 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/bg_ad_cta_button.xml` | 302 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/bg_native_ad_card.xml` | 333 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/cleanup_badge.xml` | 150 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/cleanup_check_selector.xml` | 218 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/cleanup_confirm_surface.xml` | 151 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/cleanup_footer_fade.xml` | 197 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/cleanup_photo_check_selector.xml` | 230 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/cleanup_tile_shade.xml` | 165 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/completion_halo.xml` | 137 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/home_arrow_surface.xml` | 172 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/home_usage_progress.xml` | 500 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/ic_app_manager_sort_ascending.xml` | 216 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/ic_launcher_background.xml` | 108 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 310 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/junk_check_selector.xml` | 211 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/notification_switch_track.xml` | 232 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/permission_icon_surface.xml` | 140 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/push_badge.xml` | 140 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/quality_check_selector.xml` | 230 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/quality_option_background.xml` | 583 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/rating_drag_handle.xml` | 151 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/startup_loading_static.xml` | 523 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/task_ad_surface.xml` | 156 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/task_dialog_surface.xml` | 151 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable/traffic_usage_progress.xml` | 434 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/drawable-hdpi/app_logo_rounded.webp` | 31038 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/app_logo_square.webp` | 26984 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/cleanup_check_off.webp` | 502 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/cleanup_check_on.webp` | 728 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/cleanup_chevron_down.webp` | 350 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/cleanup_chevron_right.webp` | 162 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/cleanup_expand.webp` | 580 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/cleanup_photo_check_off.webp` | 280 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/cleanup_photo_check_on.webp` | 634 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/empty_state_robot.webp` | 41794 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/home_robot.webp` | 42140 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_app_manager_back.webp` | 206 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_app_manager_installed.webp` | 316 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_app_manager_last_used.webp` | 464 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_app_manager_next.webp` | 410 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_app_manager_size.webp` | 202 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_app_manager_sort_active.webp` | 280 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_app_manager_sort_inactive.webp` | 218 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_home_available.webp` | 1890 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_home_clean.webp` | 632 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_home_download.webp` | 1700 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_home_more.webp` | 328 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_home_settings.webp` | 1300 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_home_used.webp` | 1588 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_push_permission_close.webp` | 202 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_resident_accelerate.webp` | 3372 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_resident_apps.webp` | 4926 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_resident_clean.webp` | 4736 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_resident_photos.webp` | 4364 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_settings_about.webp` | 822 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_settings_feedback.webp` | 420 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_settings_language.webp` | 926 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_settings_more.webp` | 150 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_settings_notifications.webp` | 566 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_settings_privacy.webp` | 484 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_task_close.webp` | 358 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_task_spinner.webp` | 4126 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_tool_apps.webp` | 4144 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_tool_compress.webp` | 4052 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_tool_large_files.webp` | 3904 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_tool_network.webp` | 4190 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_tool_notifications.webp` | 3958 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_tool_screenshots.webp` | 4396 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_tool_unused_files.webp` | 3872 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_traffic_back.webp` | 358 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_traffic_mobile.webp` | 438 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/ic_traffic_wifi.webp` | 796 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/junk_check_off.webp` | 554 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/junk_check_on.webp` | 714 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/junk_file.webp` | 824 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/junk_hero.webp` | 19560 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/junk_next.webp` | 394 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/notification_switch_off.webp` | 1044 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/notification_switch_on.webp` | 1116 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/push_permission_bell.webp` | 18596 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/startup_background.webp` | 40614 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-hdpi/startup_logo.webp` | 9416 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/app_logo_rounded.webp` | 16694 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/app_logo_square.webp` | 14602 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/cleanup_check_off.webp` | 336 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/cleanup_check_on.webp` | 498 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/cleanup_chevron_down.webp` | 240 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/cleanup_chevron_right.webp` | 116 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/cleanup_expand.webp` | 424 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/cleanup_photo_check_off.webp` | 182 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/cleanup_photo_check_on.webp` | 448 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/empty_state_robot.webp` | 22576 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/home_robot.webp` | 21538 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_app_manager_back.webp` | 142 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_app_manager_installed.webp` | 240 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_app_manager_last_used.webp` | 332 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_app_manager_next.webp` | 274 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_app_manager_size.webp` | 150 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_app_manager_sort_active.webp` | 224 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_app_manager_sort_inactive.webp` | 172 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_home_available.webp` | 1276 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_home_clean.webp` | 516 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_home_download.webp` | 954 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_home_more.webp` | 232 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_home_settings.webp` | 802 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_home_used.webp` | 970 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_push_permission_close.webp` | 138 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_resident_accelerate.webp` | 1970 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_resident_apps.webp` | 2644 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_resident_clean.webp` | 2672 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_resident_photos.webp` | 2470 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_settings_about.webp` | 554 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_settings_feedback.webp` | 306 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_settings_language.webp` | 596 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_settings_more.webp` | 118 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_settings_notifications.webp` | 412 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_settings_privacy.webp` | 332 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_task_close.webp` | 238 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_task_spinner.webp` | 2240 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_tool_apps.webp` | 2260 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_tool_compress.webp` | 2210 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_tool_large_files.webp` | 2198 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_tool_network.webp` | 2352 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_tool_notifications.webp` | 2212 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_tool_screenshots.webp` | 2412 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_tool_unused_files.webp` | 2204 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_traffic_back.webp` | 198 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_traffic_mobile.webp` | 294 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/ic_traffic_wifi.webp` | 406 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/junk_check_off.webp` | 328 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/junk_check_on.webp` | 488 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/junk_file.webp` | 600 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/junk_hero.webp` | 11730 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/junk_next.webp` | 272 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/notification_switch_off.webp` | 632 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/notification_switch_on.webp` | 668 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/push_permission_bell.webp` | 9624 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/startup_background.webp` | 24066 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-mdpi/startup_logo.webp` | 5182 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-nodpi/home_header_background.webp` | 32614 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-nodpi/home_hero_background.webp` | 38128 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/app_logo_rounded.webp` | 47118 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/app_logo_square.webp` | 42434 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/cleanup_check_off.webp` | 688 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/cleanup_check_on.webp` | 986 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/cleanup_chevron_down.webp` | 412 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/cleanup_chevron_right.webp` | 194 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/cleanup_expand.webp` | 708 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/cleanup_photo_check_off.webp` | 382 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/cleanup_photo_check_on.webp` | 854 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/empty_state_robot.webp` | 64878 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/home_robot.webp` | 72692 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_app_manager_back.webp` | 244 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_app_manager_installed.webp` | 398 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_app_manager_last_used.webp` | 586 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_app_manager_next.webp` | 512 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_app_manager_size.webp` | 240 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_app_manager_sort_active.webp` | 368 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_app_manager_sort_inactive.webp` | 264 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_home_available.webp` | 2374 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_home_clean.webp` | 922 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_home_download.webp` | 2430 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_home_more.webp` | 386 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_home_settings.webp` | 1908 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_home_used.webp` | 2126 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_push_permission_close.webp` | 200 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_resident_accelerate.webp` | 4948 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_resident_apps.webp` | 7380 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_resident_clean.webp` | 7146 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_resident_photos.webp` | 6252 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_settings_about.webp` | 1110 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_settings_feedback.webp` | 414 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_settings_language.webp` | 1204 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_settings_more.webp` | 154 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_settings_notifications.webp` | 694 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_settings_privacy.webp` | 508 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_task_close.webp` | 430 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_task_spinner.webp` | 5866 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_tool_apps.webp` | 6288 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_tool_compress.webp` | 6208 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_tool_large_files.webp` | 5852 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_tool_network.webp` | 6440 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_tool_notifications.webp` | 5928 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_tool_screenshots.webp` | 6694 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_tool_unused_files.webp` | 5892 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_traffic_back.webp` | 412 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_traffic_mobile.webp` | 522 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/ic_traffic_wifi.webp` | 1124 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/junk_check_off.webp` | 692 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/junk_check_on.webp` | 950 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/junk_file.webp` | 1078 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/junk_hero.webp` | 28204 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/junk_next.webp` | 510 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/notification_switch_off.webp` | 1368 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/notification_switch_on.webp` | 1444 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/push_permission_bell.webp` | 27906 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/startup_background.webp` | 61024 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xhdpi/startup_logo.webp` | 14486 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/app_logo_rounded.webp` | 91434 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/app_logo_square.webp` | 83440 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/cleanup_check_off.webp` | 454 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/cleanup_check_on.webp` | 498 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/cleanup_chevron_down.webp` | 266 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/cleanup_chevron_right.webp` | 162 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/cleanup_expand.webp` | 414 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/cleanup_photo_check_off.webp` | 334 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/cleanup_photo_check_on.webp` | 456 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/empty_state_robot.webp` | 132776 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/home_robot.webp` | 138818 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_app_manager_back.webp` | 316 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_app_manager_installed.webp` | 524 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_app_manager_last_used.webp` | 882 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_app_manager_next.webp` | 762 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_app_manager_size.webp` | 312 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_app_manager_sort_active.webp` | 514 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_app_manager_sort_inactive.webp` | 370 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_home_available.webp` | 898 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_home_clean.webp` | 274 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_home_download.webp` | 1018 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_home_more.webp` | 230 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_home_settings.webp` | 1108 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_home_used.webp` | 900 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_push_permission_close.webp` | 270 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_resident_accelerate.webp` | 8598 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_resident_apps.webp` | 13624 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_resident_clean.webp` | 13266 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_resident_photos.webp` | 11490 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_settings_about.webp` | 1642 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_settings_feedback.webp` | 644 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_settings_language.webp` | 1844 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_settings_more.webp` | 190 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_settings_notifications.webp` | 1050 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_settings_privacy.webp` | 764 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_task_close.webp` | 244 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_task_spinner.webp` | 5458 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_tool_apps.webp` | 10100 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_tool_compress.webp` | 9706 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_tool_large_files.webp` | 9346 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_tool_network.webp` | 10996 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_tool_notifications.webp` | 9774 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_tool_screenshots.webp` | 11034 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_tool_unused_files.webp` | 9382 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_traffic_back.webp` | 186 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_traffic_mobile.webp` | 190 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/ic_traffic_wifi.webp` | 566 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/junk_check_off.webp` | 1190 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/junk_check_on.webp` | 1656 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/junk_file.webp` | 1626 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/junk_hero.webp` | 50078 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/junk_next.webp` | 762 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/notification_switch_off.webp` | 794 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/notification_switch_on.webp` | 740 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/push_permission_bell.webp` | 53404 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/rating_hint.webp` | 678 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/rating_illustration.webp` | 17074 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/rating_star_empty.webp` | 1118 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/rating_star_filled.webp` | 1644 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/startup_background.webp` | 73042 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxhdpi/startup_logo.webp` | 26628 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/app_logo_rounded.webp` | 136534 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/app_logo_square.webp` | 133192 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/cleanup_check_off.webp` | 2180 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/cleanup_check_on.webp` | 3082 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/cleanup_chevron_down.webp` | 1082 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/cleanup_chevron_right.webp` | 458 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/cleanup_expand.webp` | 1574 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/cleanup_photo_check_off.webp` | 1070 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/cleanup_photo_check_on.webp` | 2512 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/empty_state_robot.webp` | 204036 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/home_robot.webp` | 225588 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_app_manager_back.webp` | 412 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_app_manager_installed.webp` | 652 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_app_manager_last_used.webp` | 1112 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_app_manager_next.webp` | 306 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_app_manager_size.webp` | 428 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_app_manager_sort_active.webp` | 608 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_app_manager_sort_inactive.webp` | 472 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_home_available.webp` | 6112 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_home_clean.webp` | 1858 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_home_download.webp` | 6322 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_home_more.webp` | 1032 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_home_settings.webp` | 5258 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_home_used.webp` | 5490 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_push_permission_close.webp` | 312 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_resident_accelerate.webp` | 11058 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_resident_apps.webp` | 19706 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_resident_clean.webp` | 18036 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_resident_photos.webp` | 15304 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_settings_about.webp` | 2144 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_settings_feedback.webp` | 736 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_settings_language.webp` | 2430 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_settings_more.webp` | 204 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_settings_notifications.webp` | 1290 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_settings_privacy.webp` | 884 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_task_close.webp` | 922 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_task_spinner.webp` | 15994 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_tool_apps.webp` | 17804 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_tool_compress.webp` | 16694 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_tool_large_files.webp` | 16708 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_tool_network.webp` | 19028 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_tool_notifications.webp` | 16570 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_tool_screenshots.webp` | 18784 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_tool_unused_files.webp` | 16514 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_traffic_back.webp` | 952 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_traffic_mobile.webp` | 1070 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/ic_traffic_wifi.webp` | 2952 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/junk_check_off.webp` | 580 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/junk_check_on.webp` | 610 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/junk_file.webp` | 744 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/junk_hero.webp` | 52262 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/junk_next.webp` | 306 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/notification_switch_off.webp` | 4082 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/notification_switch_on.webp` | 4642 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/push_permission_bell.webp` | 84712 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/startup_background.webp` | 73042 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/drawable-xxxhdpi/startup_logo.webp` | 40222 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/font/home_roboto_black.ttf` | 46736 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/font/home_roboto_bold.ttf` | 46588 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/font/home_roboto_medium.ttf` | 46428 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/font/home_roboto_regular.ttf` | 46400 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/font/home_roboto_semibold.ttf` | 46576 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/layout/dialog_app_permission.xml` | 3862 | XML | XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/dialog_cleanup_confirm.xml` | 2236 | XML | XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/dialog_compression_quality.xml` | 3015 | XML | XML View androidx.appcompat.widget.AppCompatRadioButton；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/dialog_push_permission_guide.xml` | 2916 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.core.widget.NestedScrollView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/dialog_rating_prompt.xml` | 5762 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.core.widget.NestedScrollView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/dialog_task_loading.xml` | 5038 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.example.aicleanphonestorage.core.ui.loading.LoadingDialogScrollView；XML View com.google.android.material.progressindicator.LinearProgressIndicator；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_app_manager.xml` | 5095 | XML | XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.imageview.ShapeableImageView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_cleanup_file.xml` | 2147 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_cleanup_filter.xml` | 821 | XML | XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_cleanup_photo.xml` | 4017 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_home_hero.xml` | 7120 | XML | XML View androidx.constraintlayout.widget.Barrier；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.example.aicleanphonestorage.core.ui.motion.ShimmerPillButton；XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_home_section_title.xml` | 409 | XML | XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_home_stats.xml` | 884 | XML | XML View com.google.android.material.card.MaterialCardView |
| `app/src/main/res/layout/item_home_tool.xml` | 2977 | XML | XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_junk_category.xml` | 3304 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_junk_overview.xml` | 3097 | XML | XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_junk_section.xml` | 713 | XML | XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_language_option.xml` | 773 | XML | XML View androidx.appcompat.widget.AppCompatRadioButton |
| `app/src/main/res/layout/item_notification_app.xml` | 2051 | XML | XML View androidx.appcompat.widget.SwitchCompat；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_notification_empty.xml` | 226 | XML | XML View com.example.aicleanphonestorage.core.ui.empty.EmptyStateView |
| `app/src/main/res/layout/item_notification_header.xml` | 1093 | XML | XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_settings_entry.xml` | 2638 | XML | XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_traffic_app.xml` | 2787 | XML | XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/item_traffic_empty.xml` | 226 | XML | XML View com.example.aicleanphonestorage.core.ui.empty.EmptyStateView |
| `app/src/main/res/layout/item_traffic_header.xml` | 2468 | XML | XML View com.google.android.material.chip.Chip；XML View com.google.android.material.chip.ChipGroup；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/layout_ad_loading.xml` | 2589 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.example.aicleanphonestorage.app.ad.renderer.AdLoadingAnimationView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/layout_full_native_ad_admob.xml` | 5218 | XML | XML View androidx.appcompat.widget.AppCompatTextView；XML View androidx.cardview.widget.CardView；XML View com.google.android.libraries.ads.mobile.sdk.nativead.MediaView；XML View com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView |
| `app/src/main/res/layout/layout_full_native_ad_pangle.xml` | 3382 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/layout_full_native_ad_topon.xml` | 3365 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/layout_fullscreen_loading.xml` | 1117 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/layout_native_ad_admob.xml` | 4403 | XML | XML View androidx.appcompat.widget.AppCompatTextView；XML View androidx.cardview.widget.CardView；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.android.common.bill.ui.view.AdLabelView；XML View com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView |
| `app/src/main/res/layout/layout_native_ad_pangle.xml` | 2567 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/layout_native_ad_topon.xml` | 2557 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/notification_shortcuts.xml` | 5940 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/notification_shortcuts_accessible.xml` | 5912 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/notification_shortcuts_compact.xml` | 5912 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/screen_app_manager.xml` | 5672 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.helper.widget.Flow；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.recyclerview.widget.RecyclerView；XML View com.example.aicleanphonestorage.core.ui.empty.EmptyStateView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.progressindicator.LinearProgressIndicator；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/screen_completion.xml` | 8623 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.core.widget.NestedScrollView；XML View com.example.aicleanphonestorage.core.ui.completion.CompletionBurstView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/screen_file_cleanup.xml` | 8578 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.recyclerview.widget.RecyclerView；XML View com.example.aicleanphonestorage.core.ui.empty.EmptyStateView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.progressindicator.LinearProgressIndicator；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/screen_home.xml` | 4240 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.recyclerview.widget.RecyclerView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/screen_junk_cleaning.xml` | 4164 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.recyclerview.widget.RecyclerView；XML View com.example.aicleanphonestorage.core.ui.empty.EmptyStateView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.progressindicator.LinearProgressIndicator；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/screen_network_traffic.xml` | 4741 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.recyclerview.widget.RecyclerView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.progressindicator.LinearProgressIndicator；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/screen_notification_cleaner.xml` | 4470 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.recyclerview.widget.RecyclerView；XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.progressindicator.LinearProgressIndicator；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/screen_settings_shell.xml` | 3304 | XML | XML View androidx.appcompat.widget.AppCompatImageButton；XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.core.widget.NestedScrollView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/screen_startup.xml` | 4720 | XML | XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.constraintlayout.widget.Guideline；XML View com.google.android.material.progressindicator.LinearProgressIndicator |
| `app/src/main/res/layout/view_ad_placeholder.xml` | 996 | XML | XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/view_empty_state_content.xml` | 1423 | XML | XML View androidx.constraintlayout.widget.ConstraintLayout；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/view_home_statistic.xml` | 1296 | XML | XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/view_language_picker.xml` | 1384 | XML | XML View androidx.recyclerview.widget.RecyclerView；XML View com.google.android.material.progressindicator.LinearProgressIndicator；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/view_native_ad_slot.xml` | 308 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/layout/view_settings_feedback.xml` | 4360 | XML | XML View com.google.android.material.button.MaterialButton；XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.textfield.TextInputEditText；XML View com.google.android.material.textfield.TextInputLayout；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/view_settings_info.xml` | 2344 | XML | XML View com.google.android.material.card.MaterialCardView；XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout/view_settings_menu.xml` | 2199 | XML | XML View com.example.aicleanphonestorage.feature.settings.SettingsItemView；XML View com.google.android.material.card.MaterialCardView |
| `app/src/main/res/layout/view_traffic_total.xml` | 1641 | XML | XML View com.google.android.material.textview.MaterialTextView |
| `app/src/main/res/layout-land/screen_startup.xml` | 4891 | XML | XML View androidx.constraintlayout.widget.ConstraintLayout；XML View androidx.constraintlayout.widget.Guideline；XML View com.google.android.material.progressindicator.LinearProgressIndicator |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 272 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | 272 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/mipmap-hdpi/ic_launcher.webp` | 6200 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp` | 7110 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-mdpi/ic_launcher.webp` | 3454 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp` | 3982 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-xhdpi/ic_launcher.webp` | 9566 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp` | 10882 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp` | 17486 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp` | 20014 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` | 26984 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp` | 31038 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `app/src/main/res/raw/ad_loading_tiles.json` | 7292 | JSON | 已解析 JSON；配置 DTO 见审计正文，Firebase/Lottie 交由 SDK 规则 |
| `app/src/main/res/values/app_manager_strings.xml` | 2285 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/app_manager_styles.xml` | 1349 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/bugfix_strings.xml` | 794 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/cleanup_selection_strings.xml` | 170 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/cleanup_strings.xml` | 3887 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/completion_strings.xml` | 2215 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/compression_quality.xml` | 1408 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/compression_size_strings.xml` | 365 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/empty_state_strings.xml` | 175 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/home_colors.xml` | 679 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/home_dimens.xml` | 686 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/home_metrics_strings.xml` | 591 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/home_preview_strings.xml` | 339 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/home_styles.xml` | 2614 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/junk_category_strings.xml` | 652 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/junk_strings.xml` | 1875 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/language_strings.xml` | 481 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/loading_capacity_strings.xml` | 185 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/notification_strings.xml` | 1457 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/notification_styles.xml` | 786 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/permission_strings.xml` | 1922 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/push_guide_strings.xml` | 327 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/push_guide_styles.xml` | 934 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/push_strings.xml` | 871 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/rating_strings.xml` | 369 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/rating_styles.xml` | 737 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/screenshot_summary_strings.xml` | 277 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/settings_attrs.xml` | 206 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/settings_endpoints.xml` | 356 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/settings_icons.xml` | 228 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/settings_strings.xml` | 1861 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/startup_design.xml` | 1515 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/startup_strings.xml` | 158 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/strings.xml` | 1697 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/themes.xml` | 981 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/traffic_strings.xml` | 2866 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/traffic_styles.xml` | 1803 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values/unused_strings.xml` | 362 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/bugfix_strings.xml` | 886 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/cleanup_selection_strings.xml` | 97 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/compression_size_strings.xml` | 456 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/junk_category_strings.xml` | 786 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/push_strings.xml` | 1071 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/rating_strings.xml` | 422 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/screenshot_summary_strings.xml` | 622 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/startup_strings.xml` | 184 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/strings.xml` | 30480 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ar/unused_strings.xml` | 446 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/bugfix_strings.xml` | 718 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/cleanup_selection_strings.xml` | 96 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/compression_size_strings.xml` | 353 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/junk_category_strings.xml` | 562 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/rating_strings.xml` | 379 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/screenshot_summary_strings.xml` | 219 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/startup_strings.xml` | 160 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/strings.xml` | 21011 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-b+zh+Hans/unused_strings.xml` | 355 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/bugfix_strings.xml` | 960 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/cleanup_selection_strings.xml` | 111 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/compression_size_strings.xml` | 521 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/junk_category_strings.xml` | 984 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/push_strings.xml` | 1436 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/rating_strings.xml` | 580 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/screenshot_summary_strings.xml` | 367 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/startup_strings.xml` | 227 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/strings.xml` | 37252 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-bn/unused_strings.xml` | 619 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/bugfix_strings.xml` | 751 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/cleanup_selection_strings.xml` | 99 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/compression_size_strings.xml` | 380 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/junk_category_strings.xml` | 600 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/push_strings.xml` | 920 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/rating_strings.xml` | 390 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/screenshot_summary_strings.xml` | 290 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/startup_strings.xml` | 167 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/strings.xml` | 24751 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-de/unused_strings.xml` | 366 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/bugfix_strings.xml` | 749 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/cleanup_selection_strings.xml` | 96 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/compression_size_strings.xml` | 389 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/junk_category_strings.xml` | 626 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/push_strings.xml` | 898 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/rating_strings.xml` | 384 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/screenshot_summary_strings.xml` | 377 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/startup_strings.xml` | 161 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/strings.xml` | 25119 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-es/unused_strings.xml` | 371 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/bugfix_strings.xml` | 770 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/cleanup_selection_strings.xml` | 97 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/compression_size_strings.xml` | 395 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/junk_category_strings.xml` | 647 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/push_strings.xml` | 928 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/rating_strings.xml` | 381 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/screenshot_summary_strings.xml` | 379 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/startup_strings.xml` | 170 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/strings.xml` | 25885 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-fr/unused_strings.xml` | 400 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/bugfix_strings.xml` | 970 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/cleanup_selection_strings.xml` | 124 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/compression_size_strings.xml` | 541 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/junk_category_strings.xml` | 1065 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/push_strings.xml` | 1352 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/rating_strings.xml` | 586 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/screenshot_summary_strings.xml` | 358 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/startup_strings.xml` | 232 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/strings.xml` | 36189 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-hi/unused_strings.xml` | 602 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/bugfix_strings.xml` | 747 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/cleanup_selection_strings.xml` | 94 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/compression_size_strings.xml` | 379 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/junk_category_strings.xml` | 603 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/push_strings.xml` | 904 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/rating_strings.xml` | 401 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/screenshot_summary_strings.xml` | 230 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/startup_strings.xml` | 157 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/strings.xml` | 22685 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-in/unused_strings.xml` | 367 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/bugfix_strings.xml` | 814 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/cleanup_selection_strings.xml` | 102 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/compression_size_strings.xml` | 398 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/junk_category_strings.xml` | 739 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/push_strings.xml` | 1035 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/rating_strings.xml` | 429 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/screenshot_summary_strings.xml` | 277 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/startup_strings.xml` | 178 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/strings.xml` | 26196 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ja/unused_strings.xml` | 437 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/bugfix_strings.xml` | 751 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/cleanup_selection_strings.xml` | 99 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/compression_size_strings.xml` | 375 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/junk_category_strings.xml` | 640 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/push_strings.xml` | 942 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/rating_strings.xml` | 401 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/screenshot_summary_strings.xml` | 229 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/startup_strings.xml` | 180 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/strings.xml` | 23961 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ko/unused_strings.xml` | 390 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-land/startup_dimensions.xml` | 176 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/bugfix_strings.xml` | 745 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/cleanup_selection_strings.xml` | 96 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/compression_size_strings.xml` | 387 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/junk_category_strings.xml` | 619 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/push_strings.xml` | 878 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/rating_strings.xml` | 383 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/screenshot_summary_strings.xml` | 364 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/startup_strings.xml` | 167 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/strings.xml` | 24837 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-pt/unused_strings.xml` | 381 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/bugfix_strings.xml` | 877 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/cleanup_selection_strings.xml` | 97 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/compression_size_strings.xml` | 434 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/junk_category_strings.xml` | 815 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/push_strings.xml` | 1168 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/rating_strings.xml` | 476 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/screenshot_summary_strings.xml` | 453 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/startup_strings.xml` | 203 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/strings.xml` | 33260 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ru/unused_strings.xml` | 482 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/bugfix_strings.xml` | 753 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/cleanup_selection_strings.xml` | 99 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/compression_size_strings.xml` | 405 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/junk_category_strings.xml` | 618 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/push_strings.xml` | 938 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/rating_strings.xml` | 395 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/screenshot_summary_strings.xml` | 307 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/startup_strings.xml` | 173 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/strings.xml` | 24007 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-tr/unused_strings.xml` | 395 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/bugfix_strings.xml` | 848 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/cleanup_selection_strings.xml` | 101 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/compression_size_strings.xml` | 464 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/junk_category_strings.xml` | 803 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/push_strings.xml` | 1155 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/rating_strings.xml` | 458 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/screenshot_summary_strings.xml` | 326 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/startup_strings.xml` | 198 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/strings.xml` | 29694 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-ur/unused_strings.xml` | 481 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-v29/themes.xml` | 167 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/bugfix_strings.xml` | 801 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/cleanup_selection_strings.xml` | 91 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/compression_size_strings.xml` | 405 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/junk_category_strings.xml` | 688 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/push_strings.xml` | 942 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/rating_strings.xml` | 430 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/screenshot_summary_strings.xml` | 245 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/startup_strings.xml` | 184 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/strings.xml` | 25371 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-vi/unused_strings.xml` | 426 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-zh-rCN/junk_category_strings.xml` | 562 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-zh-rCN/push_strings.xml` | 846 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/values-zh-rCN/startup_strings.xml` | 160 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/xml/backup_rules.xml` | 483 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/xml/cleanup_file_paths.xml` | 120 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/xml/data_extraction_rules.xml` | 985 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/main/res/xml/locales_config.xml` | 628 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `app/src/test/java/com/example/aicleanphonestorage/app/ad/HomeExitAdStateTest.kt` | 2054 | 测试源码 | 框架恢复/工厂 L3,9,21,34,49 |
| `app/src/test/java/com/example/aicleanphonestorage/app/ad/HotStartStateTest.kt` | 2096 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/app/ad/InterstitialActionStateTest.kt` | 2227 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/app/ad/InterstitialPlacementsTest.kt` | 1702 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/app/ad/NativeAdPlacementsTest.kt` | 2456 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/core/analytics/BusinessTelemetryTest.kt` | 7928 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/core/coroutines/TaskExecutorTest.kt` | 3543 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/core/data/apps/InstalledAppCountRepositoryTest.kt` | 3192 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/core/locale/LanguagePreferencesTest.kt` | 1944 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/core/permissions/PermissionFlowViewModelTest.kt` | 9591 | 测试源码 | 框架恢复/工厂 L3,44,47,52,166 |
| `app/src/test/java/com/example/aicleanphonestorage/core/ui/completion/CompletionReportTest.kt` | 1358 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/core/ui/loading/ContinuousEntryProgressTest.kt` | 6121 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/core/ui/loading/EntryLoadingRecoveryTest.kt` | 3397 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/appmanager/AppManagerOrderingTest.kt` | 2093 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/appmanager/AppManagerViewModelTest.kt` | 8600 | 测试源码 | 框架恢复/工厂 L3,49,55,57,175,198,208,230 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/filecleaner/CleanupPolicyTest.kt` | 3376 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/home/HomeAttributionSyncTest.kt` | 1248 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/home/HomeCleaningStateTest.kt` | 3413 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/home/HomeOverviewTest.kt` | 1263 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/home/HomeViewModelTest.kt` | 3974 | 测试源码 | 框架恢复/工厂 L8,41,42,46 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/home/ui/HomeContentTest.kt` | 4925 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/junkcleaner/JunkRulesTest.kt` | 4171 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/networktraffic/EntryLoadingProgressTest.kt` | 2181 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/networktraffic/NetworkTrafficViewModelTest.kt` | 8915 | 测试源码 | 框架恢复/工厂 L3,27,36,57,70,76,85,141 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/networktraffic/TrafficAppVisibilityTest.kt` | 1184 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/networktraffic/TrafficPeriodTest.kt` | 3441 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/notifications/NotificationCleanerViewModelTest.kt` | 5801 | 测试源码 | 框架恢复/工厂 L3,38,105 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/notifications/NotificationClearPolicyTest.kt` | 1396 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/notifications/NotificationRemovalTrackerTest.kt` | 1369 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/notifications/NotificationRulesStoreTest.kt` | 1745 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/push/PushPermissionTelemetryTest.kt` | 3944 | 测试源码 | 框架恢复/工厂 L3,10 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/push/PushPermissionViewModelTest.kt` | 5543 | 测试源码 | 框架恢复/工厂 L3,10,22,34,35,44,52,67,68,74,83,89,95,111,120,127,128,130 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/rating/RatingPromptViewModelTest.kt` | 4415 | 测试源码 | 框架恢复/工厂 L3,19,20,29,42,60,65,75,78 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/startup/StartupOfflineTest.kt` | 4786 | 测试源码 | 框架恢复/工厂 L3,14,16 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/startup/StartupViewModelTest.kt` | 13489 | 测试源码 | 框架恢复/工厂 L3,17,19,21,36,37,39,60,85,114,138,160,180,201,229,231,236,238,254,272,298 |
| `app/src/test/java/com/example/aicleanphonestorage/feature/unused/UnusedRulesTest.kt` | 3244 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |

## notification

| 文件 | 字节数 | 类型 | 检查项 |
| --- | ---: | --- | --- |
| `notification/build.gradle.kts` | 2502 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `notification/consumer-rules.pro` | 3154 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `notification/src/main/AndroidManifest.xml` | 2737 | XML | Manifest .provider.Provider；Manifest .receiver.DeleteReceiver；Manifest .receiver.ResidentDismissReceiver；Manifest .service.CoreService；Manifest .service.MessageService；Manifest androidx.startup.InitializationProvider |
| `notification/src/main/assets/pvvvush_config.json` | 743 | JSON | 已解析 JSON；配置 DTO 见审计正文，Firebase/Lottie 交由 SDK 规则 |
| `notification/src/main/assets/pvvvvush_content_config.json` | 22687 | JSON | 已解析 JSON；配置 DTO 见审计正文，Firebase/Lottie 交由 SDK 规则 |
| `notification/src/main/java/io/docview/push/NotificationDestination.kt` | 1040 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/NotificationHost.kt` | 1237 | 生产源码 | 资源/动画 L4,14 |
| `notification/src/main/java/io/docview/push/NotificationPermissionAccess.kt` | 1232 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/NotificationRuntime.kt` | 3267 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/analytics/NotificationContent.kt` | 1055 | 生产源码 | 资源/动画 L3 |
| `notification/src/main/java/io/docview/push/analytics/NotificationContentIntent.kt` | 1201 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/analytics/NotificationVisibility.kt` | 1270 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/analytics/NotificationVisibilityState.kt` | 750 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/builder/MsgBuilder.kt` | 10657 | 生产源码 | Gson 模型/转换 L10,158,168；资源/动画 L8,57,58 |
| `notification/src/main/java/io/docview/push/check/BlockReason.kt` | 985 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/check/CheckCtrl.kt` | 14222 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/config/Config.kt` | 2505 | 生产源码 | Gson 模型/转换 L3,9,11,13,15,17,19,21,23,25,27,35,37,43 |
| `notification/src/main/java/io/docview/push/config/ConfigCtrl.kt` | 8940 | 生产源码 | Gson 模型/转换 L4 |
| `notification/src/main/java/io/docview/push/config/Content.kt` | 2676 | 生产源码 | Gson 模型/转换 L3,8,9,10,11,12,13,39,40 |
| `notification/src/main/java/io/docview/push/config/ContentCtrl.kt` | 4846 | 生产源码 | Gson 模型/转换 L5,7 |
| `notification/src/main/java/io/docview/push/controller/BgNotiInterceptController.kt` | 871 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/controller/LandingCtrl.kt` | 3146 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/controller/ResidentNotificationDismissal.kt` | 3074 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/controller/TokenUploadCtrl.kt` | 5023 | 生产源码 | 类名依赖 L55 |
| `notification/src/main/java/io/docview/push/controller/TriggerCtrl.kt` | 21113 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/earthquake/Earthquake.kt` | 3513 | 生产源码 | Gson 模型/转换 L3,9,11,13,18,20,22,24,26,28,33,35,37,39,44,46,48,50,52,54,56,58,60,62,64,69,71,82,88,94,100,112,118,124,130,136,142 |
| `notification/src/main/java/io/docview/push/earthquake/EarthquakeController.kt` | 22770 | 生产源码 | Gson 模型/转换 L3,80,263 |
| `notification/src/main/java/io/docview/push/host/PushEnvironment.kt` | 1307 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/host/PushLanguage.kt` | 1131 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/host/PushPreferences.kt` | 2511 | 生产源码 | 反射/动态加载 L6 |
| `notification/src/main/java/io/docview/push/host/PushRemoteConfig.kt` | 1572 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/host/PushUserChannel.kt` | 1012 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/provider/Provider.kt` | 1771 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/receiver/DeleteReceiver.kt` | 719 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/receiver/ResidentDismissReceiver.kt` | 1959 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/service/CoreService.kt` | 12555 | 生产源码 | 类名依赖 L249 |
| `notification/src/main/java/io/docview/push/service/CoreServiceLifecycle.kt` | 3065 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/service/MessageService.kt` | 4272 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/service/ServiceMgr.kt` | 1782 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/timing/ScreenEventGate.kt` | 1562 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/timing/ScreenEventRegistration.kt` | 2911 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/timing/TimingCtrl.kt` | 11223 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/DataValidator.kt` | 1333 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/DateUtil.kt` | 1000 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/Logger.kt` | 3977 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/NotiLogger.kt` | 3981 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/ResetCtrl.kt` | 6071 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/SecurityHelper.kt` | 2735 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/StringProcessor.kt` | 1466 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/Topic.kt` | 140 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/TopicMgr.kt` | 2557 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/main/java/io/docview/push/utils/ViewBuilder.kt` | 2162 | 生产源码 | 资源/动画 L8,15,70 |
| `notification/src/main/java/io/docview/push/worker/KeepAliveWorker.kt` | 2144 | 生产源码 | 框架恢复/工厂 L4,20 |
| `notification/src/main/res/drawable/ic_noti_cleaner.xml` | 19628 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_noti_decrypt.xml` | 4039 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_noti_encrypt.xml` | 3988 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_noti_fav.xml` | 4009 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_noti_icon.png` | 29739 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/drawable/ic_noti_image2_pdf.xml` | 4620 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_noti_merge.xml` | 5054 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_noti_scan.xml` | 4376 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_noti_search.xml` | 3066 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_noti_split.xml` | 3923 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_resident_restore.xml` | 1146 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_resident_restored.xml` | 1455 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/ic_resident_tip.xml` | 372 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_bg_badge.xml` | 222 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_bg_button_primary.xml` | 221 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_bg_r12_red.xml` | 190 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_bg_r16_white.xml` | 192 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_bg_r8_blue.xml` | 189 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_bg_r8_green.xml` | 189 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_circle_red.xml` | 247 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_timing_notify_btn_long.xml` | 3475 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable/noti_timing_notify_btn_simple.xml` | 274 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/drawable-hdpi/ic_noti_process.webp` | 1374 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/drawable-mdpi/ic_noti_process.webp` | 866 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/drawable-xhdpi/ic_noti_process.webp` | 1820 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/drawable-xxhdpi/ic_noti_process.webp` | 2780 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/drawable-xxxhdpi/ic_noti_process.webp` | 3762 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/layout/layout_notification_earthquake.xml` | 2198 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/layout/layout_notification_earthquake_12.xml` | 2156 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/layout/layout_notification_general.xml` | 3538 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/layout/layout_notification_general_12.xml` | 3500 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/layout/layout_notification_general_big.xml` | 3656 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/layout/layout_notification_general_big_12.xml` | 3620 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/layout-v33/layout_notification_general_big_12.xml` | 3337 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/mipmap-xxhdpi/ic_home_duplicate.webp` | 4954 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_home_similar.webp` | 4944 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_home_speed.webp` | 5338 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_arrow_right.webp` | 202 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_bookmark.webp` | 3012 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_earthquake.webp` | 1870 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_junk.webp` | 8668 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_main.webp` | 1556 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_process.webp` | 11398 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_scan.webp` | 3700 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_short_video.webp` | 10630 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_small_icon.webp` | 25382 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/mipmap-xxhdpi/ic_noti_weather.webp` | 13140 | 二进制资源 | 按类型/大小核对；无 JVM 类或成员规则 |
| `notification/src/main/res/values/colors.xml` | 424 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/values/strings.xml` | 283 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/values/ta_public_config.xml` | 122 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/values/themes.xml` | 815 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/values-ja/strings.xml` | 64 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/values-ko/strings.xml` | 64 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/values-night/themes.xml` | 73 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/values-zh/strings.xml` | 64 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/main/res/values-zh-rTW/strings.xml` | 64 | XML | 已解析 XML；无自定义类/反射属性入口 |
| `notification/src/test/java/io/docview/push/analytics/NotificationVisibilityStateTest.kt` | 2108 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/test/java/io/docview/push/config/ConfigProtocolTest.kt` | 2363 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/test/java/io/docview/push/config/ContentBusinessContractTest.kt` | 4171 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/test/java/io/docview/push/controller/TokenUploadProtocolTest.kt` | 1940 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/test/java/io/docview/push/service/CoreServiceLifecycleTest.kt` | 5308 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `notification/src/test/java/io/docview/push/timing/ScreenEventGateTest.kt` | 2142 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |

## metrics

| 文件 | 字节数 | 类型 | 检查项 |
| --- | ---: | --- | --- |
| `metrics/build.gradle.kts` | 2551 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `metrics/consumer-rules.pro` | 1149 | 构建/配置 | 完整文本扫描；规则装配/静态配置见审计正文 |
| `metrics/src/main/AndroidManifest.xml` | 680 | XML | Manifest .provider.MetricsModuleProvider |
| `metrics/src/main/assets/firebase_revenue_config.json` | 104 | JSON | 已解析 JSON；配置 DTO 见审计正文，Firebase/Lottie 交由 SDK 规则 |
| `metrics/src/main/assets/revenue_config.json` | 114 | JSON | 已解析 JSON；配置 DTO 见审计正文，Firebase/Lottie 交由 SDK 规则 |
| `metrics/src/main/java/net/corekit/metrics/adjust/AdjustTracker.kt` | 13861 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `metrics/src/main/java/net/corekit/metrics/data/FirebaseNotificationParameters.kt` | 660 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `metrics/src/main/java/net/corekit/metrics/data/FirebaseReporter.kt` | 7387 | 生产源码 | Gson 模型/转换 L5,18,95 |
| `metrics/src/main/java/net/corekit/metrics/data/ThinkingReporter.kt` | 4607 | 生产源码 | Gson 模型/转换 L5,16,67 |
| `metrics/src/main/java/net/corekit/metrics/log/MetricsLogger.kt` | 3507 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `metrics/src/main/java/net/corekit/metrics/provider/MetricsModuleProvider.kt` | 4064 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `metrics/src/main/java/net/corekit/metrics/report/SharedParamsManager.kt` | 5515 | 生产源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
| `metrics/src/main/java/net/corekit/metrics/revenue/AdjustRevenueReporter.kt` | 7798 | 生产源码 | Gson 模型/转换 L5,6,25,27,37,59,65 |
| `metrics/src/main/java/net/corekit/metrics/revenue/FirebaseRevenueReporter.kt` | 8507 | 生产源码 | Gson 模型/转换 L8,9,29,51,57 |
| `metrics/src/test/java/net/corekit/metrics/data/FirebaseNotificationParametersTest.kt` | 1164 | 测试源码 | 静态入口扫描无上述特殊边界；静态调用由 R8 追踪 |
