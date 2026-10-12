# MAX 原生渲染资源

来源：`/Users/apple/StudioProjects/Remax_Browser/app/src/main/`，2026-10-12 迁入；来源项目未修改。

| 来源 | 当前项目 |
| --- | --- |
| `res/layout/layout_max_native_ads.xml` | `res/layout/layout_native_ad_max.xml` |
| `res/layout/layout_max_native_ads_large.xml` | `res/layout/layout_native_ad_max_large.xml` |
| `res/layout/layout_max_full_screen_native_ads.xml` | `res/layout/layout_full_native_ad_max.xml` |
| `res/drawable/bg_ad_label_gray.xml` | `res/drawable/bg_ad_label_gray.xml` |
| `java/com/example/browser/ad/BrowserMaxNativeAdRenderer.kt` | `app/ad/renderer/DefaultMaxNativeAdRenderer.kt` |
| `java/com/example/browser/ad/BrowserMaxFullScreenNativeAdRenderer.kt` | `app/ad/renderer/DefaultMaxFullScreenNativeAdRenderer.kt` |

现有 `bg_native_ad_card` 和 `bg_ad_cta_button` 与来源外观相同，直接复用，不覆盖其他广告平台资源。大卡 AD 文案复用已有不参与翻译的 `task_ad_label`。

`AdConfiguration.install()` 注册普通与全屏 Renderer。`nativeStyleStandard` 指向普通布局，`nativeStyleLarge` 指向大卡布局。当前 `bill:support_risk_1.0.10` 的原生接口为 `createNativeAdView(context, style)`，从 `style.layoutResId` 创建视图；来源项目旧接口只有 context，直接复制会编译失败，并不能支持当前配置的布局选择。

MAX 原生资产由 `MaxNativeAdViewBinder` 绑定：标题、描述、图标、CTA、隐私选项；全屏另绑定媒体容器。Renderer 不持有 Context、View 或广告对象，不启动任务，广告加载、媒体播放和释放继续由 SDK 与宿主生命周期管理。

全屏布局显式包含 SDK 原版 `AdLabelView`（30dp 广告标识），不会由 MAX 的全屏宿主自动补齐。标签在左上独立区域，正文从其下方开始；右侧隐私选项保留，标签带无障碍描述，不注册为广告点击资产。媒体改为使用正文与 CTA 之间的剩余空间，避免标签、大字号或小屏使媒体遮挡正文。

广告标识与隐私图标是不同元素：[Google 原生广告要求](https://support.google.com/admob/answer/6329638?hl=en) 要求显式广告归属标识与 AdChoices；[MAX 官方说明](https://support.applovin.com/en/max/android/ad-formats/native-ads) 要求隐私图标及遵循各广告源的资产要求。补标签只修复该项缺口，不代表所有广告源的完整政策验收已完成。

普通卡 72dp 改为最小高度，CTA 改为自然测量并保留至少 48dp（全屏 50dp），AD 标签自然测量。普通卡在 CTA 上方预留隐私选项，全屏标题右侧预留隐私选项空间，避免控件遮挡。原 XML 的 Test MAX Ads/Test MAX desc/INSTALL 样例只用于 `tools:text`，运行时内容由广告 SDK 填充。未引入演示广告或修改渠道广告 ID、SDK Key、云端广告配置。

验证记录见 [MAX 布局验证](../verification/max-renderers/README.md)。设备测试只构造真实 MAX View 并注入隔离的布局样例，不请求、点击广告或发送曝光；不能据此确认广告平台真实填充。
