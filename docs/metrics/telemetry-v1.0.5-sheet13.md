# 第 13 项新增埋点原表核对

日期：2026-10-22。按用户指定在 Chrome 读取[埋点方案 / 13-电池信息等新增功能](https://pic6ktmsyi.feishu.cn/wiki/GvgKwIzPeiWUmVk0BQFc31dNnVh?sheet=d83obq)，核对单元格及完整属性值，不只依据需求文档内嵌截图。未编辑远端表格。

本表共有 15 个事件：电池 2 个、测速 5 个、卸载挽留 5 个、新用户 3 个。v1.0.5 本次新增对应第 26–35 行的 8 个事件，未发现事件名、大小写、实际调用或已授权属性的遗漏。`Grouping_<value>` 在主需求第 5 节独立定义，不在本工作表中。

## 本次新增：逐行核对

| 原表行 | 事件名（精确大小写） | 触发时机 | 实际业务属性 | 代码入口 |
| --- | --- | --- | --- | --- |
| 26 | `uninstall_retention_show` | 第一层挽留页成功展示，每次进入一次 | 无 | `RetentionViewModel.shown`，由 Activity 确认页面准入后调用 |
| 27 | `uninstall_feature_click` | 点击功能卡片，页面跳转前 | `feature_name` | `RetentionViewModel.feature`；调用先于 `openHome` |
| 28 | `uninstall_retention_click` | 点击第一层页底部按钮 | `action=keep_app/uninstall` | `keep` / `beginAnyway` |
| 29 | `uninstall_reason_show` | 第二层原因页成功展示，每次进入一次 | 无 | `RetentionViewModel.shown`；广告回调后页面切换才曝光 |
| 30–32 | `uninstall_reason_click` | 点击原因页底部按钮 | `action=keep_app/uninstall`、`reason`、`has_other_content`（用户核对后授权补齐） | `RetentionViewModel.keep/uninstall` |
| 33 | `new_user_scan_exposure` | 进入设备扫描页 | 无 | `DeviceScanViewModel.foreground` |
| 34 | `new_user_scan_finish` | 扫描完成 | 无 | 六个真实字段完成且引擎初始化成功后；失败不报完成 |
| 35 | `new_user_start_clean_click` | 扫描完成后点击 Start Clean | 无 | `DeviceScanViewModel.startClean`，请求广告前 |

这些业务事件统一交给现有 `BusinessTelemetry` / `ReportDataManager`，沿用 SDK 内部渠道分发。无属性指无新增业务属性，不表示移除 SDK 公共参数。

I27 单元格完整枚举与 `RetentionFeature.wire` 一致：

```text
junk_cleaner
malware_scan
duplicate_photo_cleaner
screenshot_cleaner
large_file_cleaner
notification_cleaner
```

I28、I30 的值均为 `keep_app`、`uninstall`，属性类型为 String。重复点击等待中的插屏不重复上报/请求；页面重建或普通后台恢复不重复曝光，真正返回前一层再进入则是新的页面曝光。

## 原表反馈字段与最新授权

原表 E31 / I31：`reason`，String，枚举 `none/difficult_to_use/too_many_ads/poor_user_experience/found_similar_app/unmet_needs/other`。

原表 E32 / H32：`has_other_content`，String；说明实际要求 Other 补充文本原文，不能因字段名含 `has` 就误写成 Boolean。

用户在本次核对后明确改为上报这两个字段，覆盖此前“不上传”的要求。原因页两个底部按钮均提交 `reason` 与 String 类型的 `has_other_content`；Other 才取输入原文（既有 500 字符上限），其余为空串，未选原因使用 none。只编辑/选择不发送，点击时才锁存参数。现有清理结果不符合预期、运行卡顿均映射 poor_user_experience，缺少功能映射 unmet_needs；没有找到类似应用选项，不伪造 found_similar_app。详见[卸载挽留实现](../uninstall-retention.md)。

## 第 13 项既有事件复核

| 原表行 | 事件 | 代码/结论 |
| --- | --- | --- |
| 2–4 | `BatteryInfo_scan_result` | `BatteryEntryViewModel` 真实读取成功后上报，名称大小写一致 |
| 5–7 | `BatteryInfo_result_show` | `PageTelemetry` 在电池信息结果页 RESUMED 后上报 |
| 8–10 | `speed_scan_result` | `SpeedTestViewModel` 测速成功后，广告与结果显示之前上报 |
| 11–12 | `speed_scan_back` | 扫描期间主动返回，按测速代次去重 |
| 13–14 | `speed_result_show` | 广告结束且结果真正展示后，按代次一次 |
| 15 | `TestAgain_click` | 结果页再次测速按钮；保留 TestAgain 的大小写 |
| 16–25 | `speed_scan_fail` | 已接入 action/reason；展示时机继续使用用户 2026-10-16 的确认，见下文 |

测速失败 `action`：`cancel/retry`。原因枚举与 H18:H25 一致：`no_internet/weak_network/timeout/network_changed/network_restricted/network_info_failed/server_error/unknown`；未知只在原因不可确定时使用，不伪造具体原因。

原表 C16 的描述仍为“扫描失败展示弹窗时上报”，但现有[测速埋点记录](telemetry-2026-10-12.md)保存了用户 2026-10-16 的确认：**仅用户操作失败弹窗时上报，每条同时携带 reason 与 action**。对应历史提交 `b61bc8e`，代码与测试均执行该规则。本次是新增埋点核对，不恢复已取消的弹窗曝光重复上报。

## 验证范围

初次核对确认事件名及调用完整；随后按用户授权补齐反馈属性。`RetentionTest` 覆盖反馈仅在点击时提交、各原因映射、空值、切换选项、500 字符上限、重建与快照隔离；`NewUserFlowTest` 覆盖三项事件顺序与点击去重；`SpeedTestTelemetryTest` 覆盖事件大小写、两个 action 及八个 reason。本次补齐后的 local 验证见[反馈参数验证](../verification/uninstall-retention/feedback-parameters.md)。

结论针对客户端契约与调用链；未向线上注入测试事件，也未验证 Firebase/数数后台到账。
