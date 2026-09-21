# 64dff000 之后的业务埋点核查

依据 [飞书埋点方案](https://pic6ktmsyi.feishu.cn/wiki/GvgKwIzPeiWUmVk0BQFc31dNnVh?sheet=8lRyXr)，2026-09-20/21 通过用户指定的 Chrome 阅读可见表格。本次核对首页公共表与新增 10–13 表；逐事件字段和触发条件保存在 [event-spec-v1.0.1.json](event-spec-v1.0.1.json)。没有修改云端需求文档。

## 范围和广告隔离

核查 `64dff000..eaf7dee`：视频清理、相同照片、统一权限弹层、电池信息、恶意软件、v1.0.1 广告位、多语言每日推送及 R8 检查。

- 本次补齐 21 个专属业务事件，以及新入口/页面公共参数。原有 9 表历史覆盖报告仍见 [business-events.md](business-events.md)。
- 广告插屏/原生位、slot key、渠道广告配置、广告加载展示与续接实现均没有修改。业务协议转换仅位于 `BusinessPageNames`，不重命名业务路由、枚举或广告标识。
- 权限弹层继续由 `feature_entry_click.perm_granted` 表达业务授权状态；通知权限仍沿用独立的 `Notific_Allow_*` 契约，不另造弹层事件。
- 每日多语言推送仍使用现有 `Notific_Show/Click/Enter` 实际文案快照；本次不重复上报，不变更推送模块。R8 检查本身不增加业务事件。

## 公共协议差异

| 代码内部名称 | 飞书上报值 |
| --- | --- |
| video | vedio |
| video_result | vedion_result |
| duplicate | duplicatephoto |
| duplicate_result | duplicatephoto_result |
| malware | MalwareScan |
| malware_result | MalwareScan_result |
| battery | BatteryInfo |

保留飞书原有大小写和拼写。首页入口同样转换 `entry`；视频/照片继续查询各自真实权限，恶意软件新增 Android 11+ ALL_FILES、旧版 READ_FILES 权限状态查询，不把需要文件权限的功能上报为 `none`。

恶意软件同一个 Activity 内，扫描与结果分别配对 `page_show/page_leave`。广告揭示结果之前仍处于扫描页；后台暂停、配置重建和广告临时覆盖继续使用现有访问去重机制。

## 新增事件覆盖

| 功能 | 事件 | 数据/调用位置 |
| --- | --- | --- |
| 视频 | video_scan_result | FileScanRepository 成功扫描后的真实候选数量/大小 |
| 视频 | video_check | 单项、全选及月份勾选完成后的页面已选 MB |
| 视频 | video_clean_click | 有效 Clean 点击的真实已选 MB |
| 视频 | video_confirm_clean | 应用内 Confirm 点击，冻结快照 MB，广告前上报 |
| 视频 | video_cancel_clean | 应用内 Cancel 按钮点击，无额外参数 |
| 视频 | video_result_show | 完成页首次展示的实际删除 MB |
| 相同照片 | duplicatephoto_scan_result | 成功分析后的可清理候选数量/MB，不含保护的 Original |
| 相同照片 | duplicatephoto_check | 单项/全选后的已选 MB |
| 相同照片 | duplicatephoto_clean_click | 有效 Clean 点击的已选 MB |
| 相同照片 | duplicatephoto_confirm_clean | 应用内 Confirm 点击的冻结快照 MB |
| 相同照片 | duplicatephoto_cancel_clean | 应用内 Cancel 按钮点击，无额外参数 |
| 相同照片 | duplicatephoto_result_show | 完成页实际删除 MB |
| 恶意软件 | virus_declare_show | 声明真正进入前台；恢复同一弹窗不重复 |
| 恶意软件 | virus_declare_click | 明确 Agree/Reject 按钮点击；系统返回不伪装按钮点击 |
| 恶意软件 | virus_scan_result | 每次扫描完成 risk/safe；异常/超时 fail；主动取消不假报完成 |
| 恶意软件 | virus_scan_back | 用户从扫描页（包括失败或等待结果揭示）主动返回 |
| 恶意软件 | virus_result_show | 广告完成、RESUMED 实际渲染结果；每个扫描代次一次 |
| 恶意软件 | virus_risk_click | system_setting/installed_app/apk/pua/unknown + solve/settings/clean/rescan |
| 恶意软件 | virus_scan_again | 结果页 Scan Again；未知风险项的重扫只记 risk_click |
| 电池 | BatteryInfo_scan_result | 入口读取成功进入 Ready；取消/失败不报 |
| 电池 | BatteryInfo_result_show | 真实页面访问；系统广播更新与配置重建不重复 |

大小全部使用数值型十进制 MB。取消事件不附加文档没有要求的 selected_size。确认使用数据库冻结的操作快照，不受随后列表变化或广告延迟影响；同操作重复/迟到回调不重复记账。系统删除授权的确认/拒绝不混为应用内 Confirm/Cancel。

`risk_count` 是系统风险 + Malware + PUA + Unknown。PUA/Unknown 均归入 risk，但保持各自 risk_type；失败、无有效目标、遗漏或访问受限且没有已知风险时，scan_result=fail，不虚构 safe result_show。不存在需求枚举的结果曝光不自行增加第三种类型。

用户在本任务明确确认：**已安装风险应用保持现有系统卸载确认流程，按钮点击使用 action=settings**。因此不按文档注释改成应用详情，不改变原业务行为。

## 验证

- localDebug 编译和全部 213 项单元测试通过；其中新增 7 项覆盖 21 事件中的数值/枚举映射、风险分类、失败/取消、旧广告回调和结果去重、电池取消/失败等关键路径。
- localDebug Lint：0 error，201 warning（报告原样保留，没有通过抑制规则隐藏警告）。
- API 32 模拟器：VideoCleanerDeviceTest、SimilarCleanerDeviceTest、MalwareAdDeviceTest 无失败；全部文件权限删除用例因权限条件跳过，不声称已覆盖该权限下真实删除。
- 新增 CleanupTelemetryDeviceTest 通过：真实索引 + ViewModel 验证月份取消选择后上报全页 1.5 MB、确认冻结大小、同操作去重、拒绝旧 ID、新操作取消。不调用广告或删除用户文件。
- 广告衔接设备用例验证扫描/结果仍使用原广告 slot，失败/取消不触发扫描完成广告；广告位单元测试随全量单测通过。
- 本机仅编译/安装 local 渠道，没有构建、打包或安装 google 渠道。
- 客户端验证不代表统计服务端已接收入库；未做线上后台收数验收或性能测量。

截图与运行摘要见 [验证目录](../verification/telemetry-v1.0.1/)。

## 后续原有表复核

用户追问后，已通过 Chrome 补充逐表核对 02–09 的全部 32 个事件及参数选项：未发现相对旧快照新增的事件/参数/枚举。另单独记录通知栏 `app_badge_count` 的历史缺口，详见 [原有工作表复核](legacy-sheets-recheck.md)。
