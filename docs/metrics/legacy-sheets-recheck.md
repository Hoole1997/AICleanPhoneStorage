# 原有工作表增量复核

2026-09-21，通过 Chrome 逐表阅读当前 [飞书埋点文档](https://pic6ktmsyi.feishu.cn/wiki/GvgKwIzPeiWUmVk0BQFc31dNnVh?sheet=8lRyXr) 的 02–09 工作表，比较仓库保存的 [旧字段快照](event-spec.json)，并核对现有代码映射。此复核补充上一轮主要针对 01、10–13 的范围。

| 工作表 | 事件数 | 事件名、参数名及枚举增量 |
| --- | ---: | --- |
| 02 垃圾清理 | 5 | 未发现新增；4 类垃圾、check/uncheck、smart_clean/got_it、cleaned/already_clean 一致 |
| 03 流量使用 | 2 | 未发现新增；权限枚举及 Manager 点击一致 |
| 04 通知清理 | 3 | 未发现新增；权限、selected_count、cleared_count 一致 |
| 05 截图清理 | 4 | 未发现新增；count/total_size、action/selected_size、deleted_size 一致 |
| 06 照片压缩 | 4 | 未发现新增；selected_count、compressed_count、saved_size 一致 |
| 07 大文件清理 | 5 | 未发现新增；8 个类型、5 个大小、6 个时间选项一致 |
| 08 应用管理 | 3 | 未发现新增；app_count、4 个 size_band 和 uninstall_jump 一致 |
| 09 未使用文件与通知栏 | 6 | 未发现新增；3 个文件分组及 4 个常驻入口枚举一致 |

合计 32 个原有事件。未发现这 8 张表新增事件、参数或枚举值，不需要为其重复增加上报。未改动业务代码，因此本次只做文档/源码静态核查，没有将上轮测试说成新执行的测试。

首页公共表的新增项已在上一轮处理：4 个功能入口值、7 个页面值（包含 3 个结果页）；详见 [增量实现报告](incremental-v1.0.1.md)。

“在线参数&推送文案配置”表还新增了 v1.0.1 的 8 个配置键：feature_exit_interstitial_enabled、feature_enter_interstitial_enabled、malware_scan_name_ab_test、pushContentD1Json 至 pushContentD5Json。它们是远程配置键，不是埋点事件或事件属性；此次没有改动这些配置，尤其没有修改广告开关或广告位。

## 历史缺口与口径例外

- `notifbar_entry_click.app_badge_count` 在旧快照已有定义，但 `ResidentClickTelemetry.take()` 当前只返回 entry/clean_badge，BusinessTelemetry 再补 user_type；未携带 App 数量。`64dff000` 的同一文件也已缺少该属性，因此属于历史缺口，不能算本轮新增完成。当前常驻通知已经使用共享应用数量快照展示角标；如果补齐该属性，需要传递实际展示时的数字快照，不能在点击时另扫或伪填 0。本轮只确认并记录该历史缺口，未修改通知传参链。
- 未使用文件文档仍写下载目录“90 天未访问”；项目已有用户明确规则是“30 天未修改”，继续以用户规则为准，不因本次文档核对倒退业务口径。
- 表格的确认/广告流程文字不能覆盖已有用户要求的删除确认与安全校验。本次仅对事件协议做增量核查，没有因此移除任何确认或改变广告展示。

此结论表示原有表中没有发现新的事件协议，并不表示原有功能所有历史缺口都已消除或统计后台已验收。
