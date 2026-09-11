# 广告位与联调清单

已按 [需求 v2 广告位总表](https://ai-clean-storage-home.pages.dev/requirements-v2) **v11 · 2026-09-11** 同步全部 33 个 Key；后台应使用下表名称。当前有 30 个 Key 接入实际业务入口，另有 1 个条件位与 2 个完成页预留位。接入入口不等于 SDK 一定填充，也不代表总表中每种触发场景均已实现，差异见 [审计报告](slot-key-audit.md)。

## 测试开关与 SDK 参数

`app/ad/AdExt.kt` 的 `isAdSlotEnabled()` **按用户要求保持 `return true`**。线上开关参数尚未配置；联调完成后移除该行，恢复 `AdSlotSwitchController.isEnabled(positionName)` 即可，页面无需逐个加判断。

原生、插屏、开屏均将同一个 `positionName` 传入 SDK 的 `position` 参数；SDK 自身请求资格、频次、缓存与填充规则仍生效。原生失败时收起容器，没有填充不展示占位素材。

## 总表对应关系

| # | 位置 | 类型 | 当前代码 / 后台 Key | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 开屏 | 开屏广告 | `splash` | 已接入冷启动、通知入口、满足总控且任一平台限频的热启动 |
| 2 | 垃圾清理 · 功能内 | 插屏 | `clean_confirm_junk` | 确认后请求；回调后执行业务 |
| 3 | 截图清理 · 功能内 | 插屏 | `clean_confirm_screenshots` | 确认后请求；回调后执行业务 |
| 4 | 照片压缩 · 功能内 | 插屏 | `clean_confirm_photo` | 确认后请求；回调后执行业务 |
| 5 | 大文件 · 功能内 | 插屏 | `clean_confirm_large` | 确认后请求；回调后执行业务 |
| 6 | 未使用文件 · 功能内 | 插屏 | `clean_confirm_unused` | 确认后请求；回调后执行业务 |
| 7 | 流量 · 功能内 | 插屏 | `clean_confirm_network` | 仅保留 Key；当前无确认动作，不配置、不请求 |
| 8 | 垃圾清理 · 返回首页 | 插屏 | `back_home_junk` | 先回首页再请求；功能页/完成页共用 |
| 9 | 截图清理 · 返回首页 | 插屏 | `back_home_screenshots` | 先回首页再请求；功能页/完成页共用 |
| 10 | 照片压缩 · 返回首页 | 插屏 | `back_home_photo` | 先回首页再请求；功能页/完成页共用 |
| 11 | 大文件 · 返回首页 | 插屏 | `back_home_large` | 先回首页再请求；功能页/完成页共用 |
| 12 | 未使用文件 · 返回首页 | 插屏 | `back_home_unused` | 先回首页再请求；功能页/完成页共用 |
| 13 | 通知清理 · 返回首页 | 插屏 | `back_home_notify` | 先回首页再请求；功能页/完成页共用 |
| 14 | 应用管理 · 返回首页 | 插屏 | `back_home_apps` | 先回首页再请求；当前仅功能页出口 |
| 15 | 流量 · 返回首页 | 插屏 | `back_home_network` | 先回首页再请求；当前仅功能页出口 |
| 16 | 首页 | 原生 | `native_home` | 已接入 |
| 17 | 垃圾清理 · 功能页 | 原生 | `native_feature_junk` | 已接入 |
| 18 | 截图清理 · 功能页 | 原生 | `native_feature_screenshots` | 已接入 |
| 19 | 照片压缩 · 功能页 | 原生 | `native_feature_photo` | 已接入 |
| 20 | 大文件 · 功能页 | 原生 | `native_feature_large` | 已接入 |
| 21 | 未使用文件 · 功能页 | 原生 | `native_feature_unused` | 已接入 |
| 22 | 通知清理 · 功能页 | 原生 | `native_feature_notify` | 已接入 |
| 23 | 应用管理 · 功能页 | 原生 | `native_feature_apps` | 已接入 |
| 24 | 流量 · 功能页 | 原生 | `native_feature_network` | 已接入 |
| 25 | 垃圾清理 · 完成页 | 原生 | `native_result_junk` | 已接入 |
| 26 | 照片压缩 · 完成页 | 原生 | `native_result_photo` | 已接入 |
| 27 | 未使用文件 · 完成页 | 原生 | `native_result_unused` | 已接入 |
| 28 | 截图清理 · 完成页 | 原生 | `native_result_screenshots` | 已接入 |
| 29 | 大文件 · 完成页 | 原生 | `native_result_large` | 已接入 |
| 30 | 通知清理 · 完成页 | 原生 | `native_result_notify` | 已接入 |
| 31 | 应用管理 · 完成页 | 原生 | `native_result_apps` | 预留；当前没有此完成页，不请求 |
| 32 | 流量 · 完成页 | 原生 | `native_result_network` | 预留；当前没有此完成页，不请求 |
| 33 | 扫描中弹窗 | 原生 | `native_scanning` | 已接入；仅 showAd=true 的功能加载弹框 |

## 插屏来源与状态恢复

- `InterstitialPlacements.clean()` 集中映射 5 个清理功能，`exit()` 集中映射同一功能的全部首页出口。通知清理两种出口统一使用 `back_home_notify`；应用管理、流量保留返回广告，不接入功能内插屏。
- `HomeExitAdContract` 仅接受 8 个 `back_home_*`。首页恢复、获得焦点并绘制后才请求；同一 token 先消费后展示，避免回调、旋转和重复 Intent 重放。
- 来源缺失时不再生成 `file_cleanup_*` 兜底 Key：正常继续业务/返回首页，跳过无法归属的广告。恢复旧版本待展示状态时，旧 Key 会被过滤；不会再向 SDK 发送历史 Key。
- `StartupAdCoordinator` 使用 `splash`。冷启动/通知仍先处理推送权限；热启动须通过 APP_OPEN 总控且任一平台限频，广告回调后仅关闭启动页返回原 Activity。详见 [热启动说明](hot-start.md)。

## 原生广告接入约定

- 共接入 **16 个实际广告位**：首页 1、功能页 8、已有完成页 6、通用加载弹框 1。`native_result_apps`、`native_result_network` 只保留 Key，不为广告新增业务完成页。
- `NativeAdFeature` / `NativeAdPlacements` 集中维护原生 Key。文件功能页依据 `CleanupFeature` 选择；完成页优先依据 `CompletionContract.SOURCE`，通知清理依据结果类型。无法识别的通用清理来源不猜成垃圾清理。
- 所有 SDK 容器宽度 `match_parent`、高度 `wrap_content`，内部不设额外 padding，外部间距按所在页面布局设置，在 XML 中默认 `GONE`，不设占位素材或固定高度；显示/隐藏由 `loadNative` 完成。成功后占据正常布局空间，失败时整块收起。
- 功能页底部固定广告（垃圾清理、截图、照片压缩、大文件、未使用文件、通知清理、应用管理、流量）与完成页底部广告均铺满可用宽度，不设置左右外边距。功能页广告位于列表下方、操作按钮上方，采用正常垂直布局避免覆盖按钮；系统栏安全区由页面处理，不加进广告容器内部。
- 首页使用独立广告行，左右与内容卡片对齐（16dp），展示后与 Manual Clean 保持章节间距；复用同一页面容器；滚动回收/数据刷新不会创建另一份广告请求。纯 UI 预览未注入容器，不触发广告。
- `NativeAdCoordinator` 只在前台且容器挂载后请求一次；普通恢复不会重复加载。未完成请求退后台会取消，恢复可重试；失败不轮询重试。销毁时取消请求并释放容器子 View。弹框通过 viewLifecycleOwner 绑定请求，关闭弹框立即释放，不依赖宿主 Activity 退出。
- `loadNative` 返回其生命周期 Job，已有不接收返回值的调用仍兼容；取消不被吞成失败回调，避免旧请求回填页面。

`native_scanning` 使用无占位素材、默认 GONE 的全宽容器；SDK 填充后窗口以 wrap_content 自动调整高度，过高时由限高 ScrollView 滚动。进度刷新不重复请求广告，showAd=false 会取消并收起，原有操作中禁用广告的场景继续保留。

## 验证

本次编译、单元测试、模拟器时机测试与限制说明统一记录于 [审计报告](slot-key-audit.md)。
