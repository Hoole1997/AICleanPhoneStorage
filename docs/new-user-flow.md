# 新用户流程（v1.0.5）

来源：[需求 1.1–1.2](https://pic6ktmsyi.feishu.cn/wiki/M6lAwdAtyijk3jkj1PRchPOonDb)、[扫描中 Figma 7121:9029](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=7121-9029)、[完成 Figma 7121:9151](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=7121-9151)。本次不包含卸载挽留、推送调整、开机自启和 grouping。

## 入口与状态

- 沿用启动页通知权限、consent、开屏广告能力；开屏关闭/失败/无填充后判定新用户路由。只有桌面启动入口进入独立 `DeviceScanActivity`，通知目标与热启动不变。
- `new_user_flow_config` 参数值为 `{"enabled":true}`。严格 Boolean，缺失、类型错误、非法 JSON、未初始化或读取失败均使用 true，显式 false 跳过引导；不新增其他开关。
- 2026-10-21 用户明确采用需求 **1.1.5** 的阶段规则，覆盖 1.1.4 第 6 条：开屏/扫描退出保持 pending；点击 Start Clean 先持久化 interstitial，再请求广告，之后退出不重走；首页实际创建后记为 completed。
- 状态存储于 DataStore，排除云备份与设备迁移。旧版本已有首页扫描索引且没有阶段记录时迁移为 completed；写过 pending 后不会把本次引擎创建的索引误判为老用户。
- 普通配置重建保留 ViewModel 和已完成字段；进程死亡后恢复扫描 Activity 时，pending 回启动页重走开屏，其余阶段恢复首页。

## 扫描与资源

- 真实读取 Build 品牌/型号与系统版本、默认显示屏实际像素/dpi、内部存储 StatFs 容量，单位 GB（十进制）。字段读取失败显示 Unknown。
- 引擎准备打开本应用扫描索引并加载现有垃圾分类规则，不创建扫描会话、不枚举用户文件、不申请存储权限、不初始化 Trustlook 云查杀。
- 引擎失败显示失败状态和 Retry，只重试未完成项。后台取消未完成读取，回前台继续；动画仅在前台、聚焦、可见且系统允许动效时运行，完成/失败后停止。
- 图片来自原版图层，资源清单和 SHA-256 位于 `design/figma/new-user/assets.json`；转换脚本为 `tools/convert_new_user_assets.py`。系统栏使用系统原生显示，不使用稿件的时间/信号图标。没有整页截图资源。
- 所有业务文案覆盖现有 16 种语言。卡片与按钮按文本自然测量高度；单层滚动区域支持小屏、2 倍字号及长翻译，系统 inset 处理一次。

## 广告与事件

新增插屏 Slot Key 与 Position Name 均为 `new_user_start_clean`。仅完成扫描且用户点击后请求一次，复用 `InterstitialActions`；广告关闭、无填充、失败或点位关闭后继续。SDK 回调前不跳首页，外部广告页面返回继续等待关闭；回调到达后台时延后至前台导航。

三个事件通过现有 BusinessTelemetry 上报，无设备字段或用户文件参数：`new_user_scan_exposure`、`new_user_scan_finish`、`new_user_start_clean_click`。同一 ViewModel 会话各一次；失败的引擎重试成功才上报 finish。

广告后台仍需配置 `new_user_start_clean`，接线成功不代表线上一定有填充。
