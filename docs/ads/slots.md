# 广告位与联调清单

对照 [需求 v2 广告位总表](https://ai-clean-storage-home.pages.dev/requirements-v2) 的 32 个建议 Slot Key，加上用户增补的 `native_scanning`，共 33 项。**后台配置应使用“当前代码 Key”列。** 原生位按总表命名；现有开屏/插屏保留既有 Key 和时机，此次没有改名或合并历史退出 Key。

## 测试开关

`app/ad/AdExt.kt` 的 `isAdSlotEnabled()` **按用户要求保持 `return true`**，因为线上开关参数尚未配置。首页/功能页/完成页均通过现有 `loadNative(positionName, container)` 接入，统一经过此入口。上线前配置当前代码 Key，再去掉临时 `return true`，启用已有的 `AdSlotSwitchController.isEnabled(positionName)`，无需逐页另写判断。

放行开关仅代表允许调用 SDK，SDK 自身请求资格、频次、缓存和填充规则仍生效。查看 `CleanAds` 日志中的 `Native request: slot=...` 和 `Native result: slot=..., success=...` 可定位实际请求位置；没有真实填充不显示占位广告。

| # | 位置 | 类型 | 需求建议 Key | 当前代码 Key | 状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开屏 | 开屏 | `splash` | `startup_splash` | 已有；冷启动/推送入口 |
| 2 | 垃圾清理确认操作 | 插屏 | `clean_confirm_junk` | `junk_clean_interstitial` | 已有 |
| 3 | 截图清理确认操作 | 插屏 | `clean_confirm_screenshots` | `screenshots_clean_interstitial` | 已有 |
| 4 | 照片压缩确认操作 | 插屏 | `clean_confirm_photo` | `photo_compress_clean_interstitial` | 已有 |
| 5 | 大文件确认操作 | 插屏 | `clean_confirm_large` | `large_files_clean_interstitial` | 已有 |
| 6 | 未使用文件确认操作 | 插屏 | `clean_confirm_unused` | `unused_files_clean_interstitial` | 已有 |
| 7 | 流量确认操作 | 插屏 | `clean_confirm_network` | — | 纯展示，无确认动作，不请求 |
| 8 | 垃圾清理返回首页 | 插屏 | `back_home_junk` | `junk_exit_interstitial / junk_complete_exit_interstitial` | 已有；功能页/完成页沿用两个历史 Key |
| 9 | 截图清理返回首页 | 插屏 | `back_home_screenshots` | `screenshots_exit_interstitial / screenshots_complete_exit_interstitial` | 已有；功能页/完成页沿用两个历史 Key |
| 10 | 照片压缩返回首页 | 插屏 | `back_home_photo` | `photo_compress_exit_interstitial / photo_compress_complete_exit_interstitial` | 已有；功能页/完成页沿用两个历史 Key |
| 11 | 大文件返回首页 | 插屏 | `back_home_large` | `large_files_exit_interstitial / large_files_complete_exit_interstitial` | 已有；功能页/完成页沿用两个历史 Key |
| 12 | 未使用文件返回首页 | 插屏 | `back_home_unused` | `unused_files_exit_interstitial / unused_files_complete_exit_interstitial` | 已有；功能页/完成页沿用两个历史 Key |
| 13 | 通知清理返回首页 | 插屏 | `back_home_notify` | `notifications_exit_interstitial / notifications_complete_exit_interstitial` | 已有；两种出口 |
| 14 | 应用管理返回首页 | 插屏 | `back_home_apps` | `app_manager_exit_interstitial` | 已有；无完成页 |
| 15 | 流量返回首页 | 插屏 | `back_home_network` | `network_traffic_exit_interstitial` | 已有；无完成页 |
| 16 | 首页 | 原生 | `native_home` | `native_home` | 本次接入；摘要与 Manual Clean 之间 |
| 17 | 垃圾清理功能页 | 原生 | `native_feature_junk` | `native_feature_junk` | 本次接入；二级功能页列表下方 |
| 18 | 截图清理功能页 | 原生 | `native_feature_screenshots` | `native_feature_screenshots` | 本次接入；二级功能页列表下方 |
| 19 | 照片压缩功能页 | 原生 | `native_feature_photo` | `native_feature_photo` | 本次接入；二级功能页列表下方 |
| 20 | 大文件功能页 | 原生 | `native_feature_large` | `native_feature_large` | 本次接入；二级功能页列表下方 |
| 21 | 未使用文件功能页 | 原生 | `native_feature_unused` | `native_feature_unused` | 本次接入；二级功能页列表下方 |
| 22 | 通知清理功能页 | 原生 | `native_feature_notify` | `native_feature_notify` | 本次接入；二级功能页列表下方 |
| 23 | 应用管理功能页 | 原生 | `native_feature_apps` | `native_feature_apps` | 本次接入；二级功能页列表下方 |
| 24 | 流量功能页 | 原生 | `native_feature_network` | `native_feature_network` | 本次接入；二级功能页列表下方 |
| 25 | 垃圾清理完成页 | 原生 | `native_result_junk` | `native_result_junk` | 本次接入；共享完成页按来源分流 |
| 26 | 截图清理完成页 | 原生 | `native_result_screenshots` | `native_result_screenshots` | 本次接入；共享完成页按来源分流 |
| 27 | 照片压缩完成页 | 原生 | `native_result_photo` | `native_result_photo` | 本次接入；共享完成页按来源分流 |
| 28 | 大文件完成页 | 原生 | `native_result_large` | `native_result_large` | 本次接入；共享完成页按来源分流 |
| 29 | 未使用文件完成页 | 原生 | `native_result_unused` | `native_result_unused` | 本次接入；共享完成页按来源分流 |
| 30 | 通知清理完成页 | 原生 | `native_result_notify` | `native_result_notify` | 本次接入；共享完成页按来源分流 |
| 31 | 应用管理完成页 | 原生 | `native_result_apps` | `native_result_apps` | 预留；当前没有此完成页，不请求 |
| 32 | 流量完成页 | 原生 | `native_result_network` | `native_result_network` | 预留；当前没有此完成页，不请求 |
| 33 | 通用功能加载弹框 | 原生 | `native_scanning` | `native_scanning` | 本次接入；仅 showAd=true 的加载状态 |

## 原生广告接入约定

- 共接入 **16 个实际广告位**：首页 1、功能页 8、已有完成页 6、通用加载弹框 1。`native_result_apps`、`native_result_network` 只保留 Key，不为广告新增业务完成页。
- `NativeAdFeature` / `NativeAdPlacements` 集中维护原生 Key。文件功能页依据 `CleanupFeature` 选择；完成页优先依据 `CompletionContract.SOURCE`，通知清理依据结果类型。无法识别的通用清理来源不猜成垃圾清理。
- 所有 SDK 容器宽度 `match_parent`、高度 `wrap_content`，内部不设额外 padding，外部间距按所在页面布局设置，在 XML 中默认 `GONE`，不设占位素材或固定高度；显示/隐藏由 `loadNative` 完成。成功后占据正常布局空间，失败时整块收起。
- 功能页底部固定广告（垃圾清理、截图、照片压缩、大文件、未使用文件、通知清理、应用管理、流量）与完成页底部广告均铺满可用宽度，不设置左右外边距。功能页广告位于列表下方、操作按钮上方，采用正常垂直布局避免覆盖按钮；系统栏安全区由页面处理，不加进广告容器内部。
- 首页使用独立广告行，左右与内容卡片对齐（16dp），展示后与 Manual Clean 保持章节间距；复用同一页面容器；滚动回收/数据刷新不会创建另一份广告请求。纯 UI 预览未注入容器，不触发广告。
- `NativeAdCoordinator` 只在前台且容器挂载后请求一次；普通恢复不会重复加载。未完成请求退后台会取消，恢复可重试；失败不轮询重试。销毁时取消请求并释放容器子 View。弹框通过 viewLifecycleOwner 绑定请求，关闭弹框立即释放，不依赖宿主 Activity 退出。
- `loadNative` 现在返回其生命周期 Job，已有不接收返回值的调用仍兼容；取消不被吞成失败回调，避免旧请求回填页面。

`native_scanning` 使用无占位素材、默认 GONE 的全宽容器；SDK 填充后窗口以 wrap_content 自动调整高度，过高时由限高 ScrollView 滚动。进度刷新不重复请求广告，showAd=false 会取消并收起，原有操作中禁用广告的场景继续保留。

未在此次原生接入中调整：热启动开屏、垃圾清理空扫描插屏路径，以及历史功能页/完成页返回插屏 Key 的合并。

## 验证

local/google Kotlin 编译、local Lint、124 项 app 单元测试和 7 项相关模拟器测试通过。模拟器测试使用明确标记的假广告内容核验默认隐藏、失败收起后恢复原布局高度、只加载一次、退后台取消、首页行位置及横竖屏按钮可达性，并验证加载弹框异步增高/收起和弹框关闭取消请求；不以测试素材冒充真实 SDK 填充。业务页面实际调用既有 loadNative/SDK 接口，真实填充请结合 CleanAds 日志和测试渠道进行联调。
