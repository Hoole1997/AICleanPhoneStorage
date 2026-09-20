# v1.0.1 新增广告位验证

日期：2026-09-20。需求来源：[飞书需求第 4 章](https://pic6ktmsyi.feishu.cn/wiki/T4vOwfFKviXZBYkwCgFcH6xmnAc)。广告位明细见 [清单](../../ads/slots.md)。

## 范围

- 11 个扫描成功插屏 Key 接入首页扫描成功出口，病毒扫描还覆盖 Scan Again。
- 相似照片确认删除插屏 `clean_confirm_duplicate`。
- 病毒扫描返回 `back_home_malware`、扫描/初始化原生 `native_feature_virus`、结果原生 `native_result_virus`。
- 现有视频/相似照片加载原生、功能原生、完成页原生和返回位复核后复用。
- 原生广告容器宽度 `match_parent`、高度 `wrap_content`，无固定高度，默认 GONE；尺寸由 SDK 内容决定。本目录截图中的灰色块和测试计数仅为测试样例，不进入正式页面。

## 结果

- local Debug 编译、APK 与 instrumentation APK 构建通过。
- 206 项 local 单元测试全部通过，包括 11 个扫描 Key、相似照片确认 Key、病毒扫描成功等待回调、旧代次回调隔离。
- `lintLocalDebug` 通过。
- 用户解锁后的真机：`InterstitialContinuationTest` 3 项、`MalwareAdDeviceTest` 2 项、`NativeAdDeviceTest#featureAndResultLayoutsCollapseEmptySlotAndKeepActionsAccessible` 1 项，合计 6 项通过。
- 追加滚动到底的可达性断言后，`MalwareAdDeviceTest#scanAndSafeResultKeepScrollableContentAboveAdAtLargeFont` 再次通过；最终 instrumentation 构建与 Lint 通过。
- 首次真机运行因锁屏导致 Activity 无法 RESUMED，该失败由解锁后的通过运行替代，未计入通过结果。临时启动的备用模拟器已关闭，最终结果来自真机。
- 截图覆盖 320×640dp、1 倍与 2 倍字号、扫描页与 Safe 结果页、滚动前后。实际检查确认广告容器在列表之外，进度条/Scan Again 可滚动至完整可见。

所有时机测试使用注入的假广告请求；截图为 Android 原生布局渲染。没有以这些结果声称真实广告已填充、真实 SDK 展示成功或完成性能测量。本机未编译、打包或安装 google 渠道。

## 复现命令

```sh
./gradlew :app:testLocalDebugUnitTest :app:lintLocalDebug :app:assembleLocalDebug :app:assembleLocalDebugAndroidTest --max-workers=2 -Dorg.gradle.jvmargs='-Xmx4096m -Dfile.encoding=UTF-8'
```

设备解锁后安装 local APK 和测试 APK，再运行上述指定测试类/方法。不要运行会请求真实 SDK 的全量业务测试来代替假回调验证。
