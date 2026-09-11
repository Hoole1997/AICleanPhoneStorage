# 广告 Slot Key 同步后审计

日期：2026-09-11。依据：[需求 v2 第三部分广告位总表](https://ai-clean-storage-home.pages.dev/requirements-v2)，v11，共 33 个 Key。范围：APP 广告位命名、业务来源传递、SDK 请求参数、现有入口与回调时机；不修改 notification 模块或新增业务页面。

## 结论

总表、代码声明/映射、[广告位清单](slots.md) **33 / 33 / 33 完全一致**，没有缺失或多余 Key。生产源码不再使用旧版 `startup_splash`、`*_interstitial` 或 `scan_dialog` 广告位名称。单元测试中保留一个旧 Key，用于验证旧状态不会被重新请求。

其中 **30 个 Key 已接入业务入口**：开屏 1、确认插屏 5、返回首页插屏 8、原生 16。另 3 个是明确不请求的条件/预留位。这里统计的是代码接入覆盖，不表示每个需求场景都已实现，也不保证 SDK 有填充。

本次修复：

- 开屏统一为 `splash`；确认插屏统一为 `clean_confirm_{junk,screenshots,photo,large,unused}`。
- 同一功能的功能页退出、完成页退出统一走 `back_home_*`，不再拆分为两个后台广告位。来源功能仍可识别。
- 原生、插屏、开屏入口均显式传入 SDK 的 `position = positionName`。此前只在 APP 内使用 Key，SDK 调用的 position 使用默认值；本次通过编译后字节码确认三种调用均传入业务字符串。
- 来源缺失时不再生成虚构的 `file_cleanup_*` Key，跳过广告并保持业务/返回流程。首页仅接受 8 个合法返回位，恢复旧待展示状态也不会请求废弃 Key。
- `isAdSlotEnabled()` 按用户要求继续 `return true`，属于测试配置，未作为缺陷处理。

## 各业务核对

| 功能 | 确认插屏 | 返回首页插屏 | 功能页原生 | 完成页原生 | 入口核对 |
| --- | --- | --- | --- | --- | --- |
| 垃圾清理 | `clean_confirm_junk` | `back_home_junk` | `native_feature_junk` | `native_result_junk` | 非空确认路径接入；空扫描路径见下文 |
| 截图清理 | `clean_confirm_screenshots` | `back_home_screenshots` | `native_feature_screenshots` | `native_result_screenshots` | 已接入 |
| 照片压缩 | `clean_confirm_photo` | `back_home_photo` | `native_feature_photo` | `native_result_photo` | 已接入；删除原图也使用同一确认位 |
| 大文件 | `clean_confirm_large` | `back_home_large` | `native_feature_large` | `native_result_large` | 已接入 |
| 未使用文件 | `clean_confirm_unused` | `back_home_unused` | `native_feature_unused` | `native_result_unused` | 已接入 |
| 通知清理 | 无，符合总表 | `back_home_notify` | `native_feature_notify` | `native_result_notify` | 已接入 |
| 应用管理 | 无，符合总表 | `back_home_apps` | `native_feature_apps` | `native_result_apps`（预留） | 当前没有完成页 |
| 网络流量 | `clean_confirm_network`（条件位，不请求） | `back_home_network` | `native_feature_network` | `native_result_network`（预留） | 当前无确认操作、无完成页 |

全局位：`splash` 用于启动页；`native_home` 位于首页摘要与 Manual Clean 之间；`native_scanning` 位于通用功能加载弹框，仅在 `showAd=true` 时接入。

现有时机符合本次约定：确认取消不请求插屏，确认后等待广告关闭/失败回调再执行业务；退出广告先导航首页，待首页 RESUMED、获得焦点并绘制后请求。同一退出 token 先消费再请求，避免重复 Intent/恢复造成重放。

原生容器默认 GONE、宽度 match_parent、高度 wrap_content。首页按内容保留左右间距；功能页/完成页底部使用可用全宽。弹框请求绑定 viewLifecycleOwner，关闭即取消。以上布局和生命周期行为在模拟器组件测试中回归验证。

## 仍存在的场景差异

### 已修复：普通后台回前台的开屏触发

后续已接入进程前后台监听，分别读取 APP_OPEN 总控与 AdPlatform 各平台状态，总控 canShow 且任一平台 canBid 后才打开启动页。热启动遵循用户新要求：结束后返回原 Activity；冷启动与通知入口仍进入首页。详见 [热启动实现与验证](hot-start.md)。

### P2：垃圾扫描为空时缺少 Got it 广告路径

需求第 2.1 节要求四类结果为空时可点击 Got it，请求 `clean_confirm_junk` 后进入干净版完成页。当前 [JunkCleaningActivity.render](../../app/src/main/java/com/example/aicleanphonestorage/feature/junkcleaner/ui/JunkCleaningActivity.kt)（第 127–132 行）在空结果时隐藏清理按钮，无法触发该广告路径。

这不是 Key 错配；本次没有新增空结果操作及完成页导航。

### 保留的业务差异与预留位

- 照片压缩完成后选择删除原图，再次确认时也会请求 `clean_confirm_photo`，随后才删除原图。见 [CleanupOperationCoordinator](../../app/src/main/java/com/example/aicleanphonestorage/feature/filecleaner/ui/CleanupOperationCoordinator.kt) 的 ORIGINALS_AD 分支。这是现有额外触发点，总表没有单独列出，本次保持其行为。
- 垃圾三级分类页返回父级不触发 `back_home_junk`；实际返回首页才请求。总表描述“返回首页”，当前实现符合此边界；功能细则另有三级直接回首页描述，本次未更改导航。
- `native_result_apps`、`native_result_network` 当前仅预留。总表既写“所有功能均有完成页”，规则⑧又要求以技术实现为准、保持已有完成页；本次按规则⑧保留现状，不为广告新增这两个页面。
- `clean_confirm_network` 因没有确认操作不配置、不请求，符合总表规则④。

## 验证结果与边界

| 检查 | 结果 |
| --- | --- |
| 线上总表、代码映射、文档集合比对 | 33 个 Key 完全相同，缺失 0、多余 0 |
| localDebug / googleDebug Kotlin 编译 | 通过 |
| localDebug APK、androidTest APK 构建 | 通过 |
| localDebug 单元测试 | 127 项通过，0 失败、0 跳过 |
| localDebug Lint | 0 错误、139 条警告；不代表全项目无警告 |
| API 32 模拟器测试 | 12 项通过，涵盖 AdTimingDeviceTest、StartupAdCoordinatorTest、NativeAdDeviceTest、ScanDialogNativeAdTest |
| SDK 参数核验 | 编译后原生、插屏、开屏调用均传入 `$positionName`，未省略 position |
| git diff --check | 通过 |

测试包含：返回首页等待恢复与绘制、正确的截图退出 Key、取消确认不出广告、确认后大文件等待回调、重复回调不重复执行、未知来源跳过广告且恢复后继续、开屏正确 Key/等待权限完成/旋转续接、原生默认隐藏/失败收起/后台取消/布局空间恢复、扫描弹框自适应高度与关闭取消。

模拟器广告请求使用构造注入的假 SDK，不点击真实广告。三种 AdExt 的 SDK position 转发由实际依赖编译和字节码核验；真实填充、后台统计归因、线上开关值与 SDK 频控没有完成联调。不能据此声明 30 个位都已实际展示。未改动 UI 资源，布局回归采用模拟器测量断言；本次未另做视觉截图审阅。

后续后台应配置本清单中的新 Key；测试结束再去掉 `isAdSlotEnabled()` 的临时 `return true`。热启动已在后续接入，垃圾空扫描仍需另行补齐业务触发。
