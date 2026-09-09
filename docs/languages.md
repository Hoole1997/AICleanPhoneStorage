# 应用语言与国际化

支持跟随系统及 16 种语言：en、zh-Hans、hi、es、ar、pt-BR、bn、ur、id、ru、fr、de、ja、ko、vi、tr。列表使用本族语名称及当前语言的说明，不用国家旗帜替代语言。

使用 Android 原生 `values-*/strings.xml`、复数资源和 BCP-47 标签。`locales_config.xml` 与代码目录相对应；英语是默认回退。语言包随安装包一起提供，切换不依赖网络。语言／地区匹配由 Android 处理，系统语言未覆盖时回退至默认资源。

- API 33+：使用 AppCompatDelegate 转交 LocaleManager，系统设置中的应用语言与本页保持一致。
- API 24–32：AppCompatDelegate 负责应用语言配置，DataStore 异步保存选择，不启用主线程 XML autoStoreLocales。升级到 API 33+ 时迁移一次，已有系统级选择优先。
- 跟随系统保存空标签并传递空 LocaleList，不把当时的系统语言写死为应用偏好。
- 初次恢复为一次性异步任务；首帧等待有上限，读失败回退系统状态。选择保存完成后再应用配置，Activity 重建不会丢失选择。
- 禁止整体 RTL 镜像：Manifest supportsRtl=false，页面方向固定 LTR。阿拉伯语、乌尔都语仍使用正常 Unicode 双向文字排版；设置项和语言列表文字按视图起点对齐，返回和勾选位置不互换。

设置项使用 SettingsItemView，图标／标题／箭头独立约束，避免多语言和关闭 RTL 后依赖按钮 padding 叠放而产生重叠。

## 翻译维护

`tools/i18n/generate_translations.py` 是开发时的机器初译工具，输出资源提交到仓库；APP 和 Gradle 构建不调用翻译服务。缓存被 Git 忽略。`translation_hints.json` 补充短词语境，`overrides.json` 保存人工校对；中文主要功能文案及关键语言选项已经校对，其余翻译建议在正式发行前做母语润色。

离线校验：`python3 tools/i18n/verify_resources.py`。校验资源覆盖、格式参数、复数 other 回退与残留标记。完成页的数字独立于单位标签渲染，仅对这三组单位复数标注 ImpliedQuantity 例外；不是全局关闭国际化检查。品牌名称、广告 AD 标记和仅 Debug 使用的预览文字保持不翻译。

验证包含选择持久化、恢复跟随系统、各语言格式参数、API 32 与新系统的切换／冷启动，以及设置项在中英阿德语言和双倍字号下的几何边界。

平台参考：[Android 应用语言](https://developer.android.com/guide/topics/resources/app-languages)、[语言与布局方向](https://developer.android.com/training/basics/supporting-devices/languages)。
