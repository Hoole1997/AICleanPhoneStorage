# 2026-10-12 黄色新增埋点

来源：[飞书埋点方案](https://pic6ktmsyi.feishu.cn/wiki/GvgKwIzPeiWUmVk0BQFc31dNnVh?sheet=0pRVXK)。通过 Chrome 阅读并下载保留样式的 Excel，逐张检查全部 14 张工作表、合并单元格及黄色填充（`FFFFF258`）。13 张事件表共有 66 个事件名，其中 5 个新事件标黄。其他 11 张事件表没有黄色新增项。

## 首页与公共页面

| 来源 | 新增项 | 实际落点 |
| --- | --- | --- |
| 01，I14 | `page_show.page=speedtestScan` | 测速扫描页面；`page_leave` 使用相同页面标识配对 |
| 01，I15 | `page_show.page=speedtest_result` | 插屏回调后实际展示的测速结果页面 |
| 01，I49 | `feature_entry_click.entry=speedtest` | 首页测网速入口，入口与扫描页面标识独立 |
| 01，E54:I55 | `feature_entry_click.home_badge=yes/no`（字符串） | Smart Clean、Malware Scan 及全部工具入口；点击时使用该卡片已绑定显示的角标快照，先采集后导航 |

权限参数沿用现有触发时的异步采集；不会为埋点申请权限。首页设计预览不发送入口事件。

## 测网速

| 来源（13 工作表） | 事件 | 触发 |
| --- | --- | --- |
| A8:A10 | `speed_scan_result` | 一次完整测速成功；取消、部分结果或失败不发送 |
| A11 | `speed_scan_back` | 用户从测速扫描页面通过顶部或系统返回实际离开；结果页返回及失败 Cancel 不算扫描返回 |
| A12:A14 | `speed_result_show` | 插屏回调后，结果在前台实际显示；同一测速代次只发送一次 |
| A15 | `TestAgain_click` | 结果页面点击 Test Again；失败 Retry 不发送此事件 |
| A16:A25 | `speed_scan_fail` | 用户操作失败弹窗时上报；展示弹窗不再上报（2026-10-16 修正） |

前四个事件没有业务参数，不上传测速节点、运营商、设备型号、Ray ID 或速度数值。

`speed_scan_fail.reason` 对应 `no_internet`、`weak_network`、`timeout`、`network_changed`、`network_restricted`、`network_info_failed`、`server_error`、`unknown`。

按用户 2026-10-16 确认，移除失败弹窗展示时的上报，仅在用户操作时发送 `speed_scan_fail`，每条必须同时携带 `reason` 与 `action=cancel/retry`。自动失败、Retry 后再次失败、弹窗恢复与前后台切换均不额外上报；历史验证日志中的无 `action` 记录属于修正前行为。Cancel 关闭并返回首页，Retry 开始新测速。系统取消等同 Cancel。旧弹窗和重复结果不改变新测速，也不重复上报。

## 配置页的黄色条目

在线参数页 B13:B19 的 `malware_scan_name_ab_test`、`ad_slot_switch`、`pushContentD1Json` 至 `pushContentD5Json` 是配置项，不是新增事件。本次不调整其行为。

## 验证边界

本机只构建、安装和测试 local 渠道。客户端 `BusinessMetrics` 日志说明已调用上报 SDK，服务端接收仍需在埋点平台确认。真机验证记录见 [验证目录](../verification/telemetry-2026-10-12/README.md)。
