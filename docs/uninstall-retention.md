# 卸载入口与挽留流程

来源：[v1.0.5 需求第 2 章](https://pic6ktmsyi.feishu.cn/wiki/M6lAwdAtyijk3jkj1PRchPOonDb)，Figma [桌面快捷菜单示意](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=7121-9431)、[挽留页](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=7121-9276)、[原因页](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=7121-9376)。

## 动态入口

- 使用 `ShortcutManager` 代码发布动态快捷入口 `uninstall_retention`，标签为本地化的 Uninstall；没有 shortcuts.xml、静态快捷入口 metadata 或 activity-alias 开关。Launcher 长按菜单由系统/桌面实现绘制，不能将 Figma 菜单当作应用界面复制。参见 [Android 动态快捷入口](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts)。
- 仅 `ChannelUserController.isChannelSetOnce()` 且 `isPaidChannel()` 为 true 时允许启用，不将默认 paid 当作归因已确认。自然和未确认用户不启用，即使配置的 organic.enabled 为 true 也不扩张文档规定人群。
- `uninstall_retention_config` 默认值：`{"paid":{"enabled":true},"organic":{"enabled":false}}`。缺失、格式或类型错误使用文档默认值；paid.enabled 显式 false 生效。
- 首次初始化、前台进入、归因变化、成功的远程更新和语言变化触发短任务，重复事件合并；无轮询、额外服务或保活。刷新挂在既有 SDK 配置回调分发点，保留 Alarm 刷新，不覆盖其他业务监听。
- 关闭时仅移除自家动态入口，同时 disable 用户已固定的副本。已有旧 Intent 在开屏后以及页面恢复时重新核验资格，失效时返回首页，不再显示挽留页。其他快捷入口不受影响。
- 入口直接到现有 StartupActivity，沿用现有权限/consent/开屏流程。开屏关闭、失败、无填充或原有离线放行后才进入挽留页面，不进入新用户设备扫描，也不伪造通知点击。

## 界面与操作

使用独立 `UninstallRetentionActivity`、ViewBinding、ViewModel 与两个 XML 页面。原版资源来源、SHA-256、开发时转换脚本记录在 `design/figma/uninstall-retention/assets.json` 和 `tools/convert_retention_assets.py`。

- 第一页保留原图、两列三行的六张卡片、Keep the App 和 Uninstall Anyway；同时保留需求正文要求但 Figma 未展示的 Before you go 与功能损失说明，因此页面内容允许滚动。
- 常规字号采用 Figma 图标在左的卡片布局；小屏/大字号改为文档描述的图标在上、文字在下，卡片自然测量高度，不裁切长翻译。
- 六个卡片通过首页现有业务路由进入垃圾清理、恶意软件扫描、重复照片、截图、大文件和通知清理，继续使用各功能原有声明/权限/确认流程。
- Keep the App、Don’t uninstall yet 返回首页，不新增保留按钮广告。
- Uninstall Anyway 触发插屏 `uninstall_anyway`，Slot Key 和 Position Name 相同。关闭、失败、无填充或开关关闭后进入原因页；回调在后台到达时等待前台，连续点击不会重复请求。同一 Activity 重建保留请求，进程恢复不自动重放旧广告。
- 原因页单选，默认未选，可不选择直接卸载；输入最多 500 字符，仅保留有界页面 SavedState。用户已确认将贷款文案改为“清理效果不符合预期”“缺少需要的功能”和当前应用名称，其余理由保持原意。
- 点击 Uninstall 调用本应用包名的系统 ACTION_DELETE 确认界面；应用不静默卸载、不自动确认，不把系统返回当作卸载成功。保留应用管理和病毒页禁止卸载自家包的原规则，使用独立的自卸载协调器。参见 [Android Intent.ACTION_DELETE](https://developer.android.com/reference/android/content/Intent#ACTION_DELETE)。
- 原因页返回键回到挽留页；挽留页返回键正常退出。系统卸载取消后留在原因页；打开系统界面失败时显示可重试提示。系统原生卸载按钮、设置中的卸载路径不受本功能影响。
- 文案覆盖 16 种语言；插画内文字保持 Figma 原图。没有新权限、文件扫描或独立服务器反馈接口；反馈按下文用户最新授权通过已有埋点通道提交。

## 埋点与反馈边界

`uninstall_retention_show`、`uninstall_reason_show`：每次真正进入页面一次；重建和后台恢复不重复。

`uninstall_feature_click`：仅含白名单 `feature_name`，在导航前发送。

`uninstall_retention_click` 仅含 `action=keep_app/uninstall`。

用户于 2026-10-22 原表核对后改为允许上报：`uninstall_reason_click` 在原因页点击 Keep/Uninstall 时，携带 String 类型的 `action`、`reason`、`has_other_content`。未选择时 `reason=none`；选择 Other 时 `has_other_content` 为输入原文（保留既有 500 字符上限），非 Other 或空输入为空串，不发送 Boolean、不上传旧的 Other 草稿。选项变化、输入、曝光、返回和广告回调不提交反馈。

现有清理选项映射：Difficult to use → difficult_to_use；Too many ads → too_many_ads；清理结果不符合预期 / Loading stutter → poor_user_experience；Missing features I need → unmet_needs；Others → other。页面没有“找到类似应用”选项，不能把其他选项强行映射到 found_similar_app。

参数在点击时锁存，经现有 BusinessTelemetry/ReportDataManager 分发；不等待网络返回才打开系统卸载页。应用日志及自有 Firebase 适配器日志遮盖输入正文，实际提交值保持原样。

广告点位已接线；实际线上填充、频控与广告平台配置需要另行联调。本次不实现需求第 3–5 章的场景推送、开机自启或 grouping。
