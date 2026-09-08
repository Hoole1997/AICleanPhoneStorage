# App Manager

入口为首页 Apps。先通过共用 `TaskLoadingDialogFragment` / `TimedEntryLoader` 加载应用列表，再打开 `AppManagerActivity`。沿用 2–4 秒随机展示窗口，计数和进度来自同一帧；退出 Loading 或首页进入后台时取消，旧请求不会延迟跳转。广告仍为占位。

## 数据与系统交互

- `InstalledAppsReader` 位于 `core/data/apps`，与通知清理共享应用名称查询。使用现有 MAIN/LAUNCHER `<queries>` 声明，显示当前用户下有桌面入口的可见应用，不新增运行时权限或 QUERY_ALL_PACKAGES。
- 应用按包名去重、按当前语言名称排序，以包名作为同名排序的稳定补充。扫描中被卸载的应用从管理列表跳过；通知业务仍可保留缺失包，供用户取消旧规则。
- 点击条目通过 `ACTION_APPLICATION_DETAILS_SETTINGS` + `package:所点包名` 进入系统应用详情。无法打开时显示错误；不会代替用户卸载应用、清数据或修改权限。
- 从设置返回后后台重新查询，保留原列表和滚动状态，使用 ListAdapter/Diff 更新变化条目，不再弹广告 Loading。进程重建时若一次性交接数据丢失，在页面内重新读取并显示轻量进度。

## UI 与生命周期

使用 XML/ViewBinding、ConstraintLayout、MaterialCardView、ShapeableImageView 和 RecyclerView。卡片白底圆角 12dp，内边距 16dp，图标 36dp/圆角 8dp，名称 16sp Medium，间距 12dp。名称可自然增高到两行，避免挤压右侧箭头。系统栏用真实 Android inset。

Figma 列表节点 `5646:182`，Loading `5646:276`。箭头从 `5646:217` 导出，五档密度 WebP 与 PNG 来源校验记录位于 `design/figma/app-manager/assets.json`。背景、返回按钮和 Loading 复用已导出的相同原版资源；应用图标来自设备 PackageManager。

应用元数据和图标查询均离开主线程。AppIconLoader 按可见条目加载，缓存上限 2 MB；页面不可见或条目回收时取消请求、清缓存，避免页面持有位图或错位回填。ViewModel 不持有 Activity、View 或 Bitmap，Intent 只传一次性 token。

## 验证与手动验收

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --max-workers=1
adb shell am instrument -w -e class com.example.aicleanphonestorage.AppManagerDeviceTest com.example.aicleanphonestorage.test/androidx.test.runner.AndroidJUnitRunner
```

单元测试覆盖快/慢查询的 Loading 时间轴、取消、旧弹框结果隔离、数据交接、设置返回刷新、失败重试和后台取消。设备测试覆盖真实应用查询、通知缺失包保留、目标包设置 Intent，以及 320/375/600dp 与 1/1.5/2 倍字体下的原生卡片测量；设备测试不解锁屏幕、不修改权限。

按用户安排，完整交互稍后手动验收：

1. 首页点击 Apps，Loading 计数渐进，完成后显示真实列表。
2. 快速取消 Loading，不应随后自动进入列表。
3. 点击任一应用，系统详情应对应被点击的应用。
4. 返回列表，滚动位置保留；在设置中卸载应用后返回，对应行移除。
5. 横竖屏切换、长名称和大字体下，标题/应用名称不遮挡箭头。

平台依据：[应用详情设置 Intent](https://developer.android.com/reference/android/provider/Settings#ACTION_APPLICATION_DETAILS_SETTINGS)、[Android 包可见性](https://developer.android.com/training/package-visibility/declaring)。不声称此列表包含所有后台系统包或其他用户/工作资料中的应用。
