# 卸载反馈字段上报验证

2026-10-22。用户在原表核对后明确允许 `reason` 和 `has_other_content` 上报，覆盖初次实现时不上传反馈的规则。

## 行为

原因页两个底部按钮都发送 `uninstall_reason_click`：

```json
{
  "action": "uninstall",
  "reason": "other",
  "has_other_content": "用户点击时的输入原文"
}
```

- action 仍为 keep_app/uninstall；三个字段均是 String。
- 未选或无效选项使用 reason=none。Other 文本保留原始空白/换行和既有 500 字符上限；非 Other 或未输入使用空串。
- 现有“清理结果不符合预期”和“运行卡顿”映射 poor_user_experience，“缺少功能”映射 unmet_needs；不增加原界面没有的 found_similar_app 选项。
- 输入、选择、曝光、返回不发送反馈。点击生成不可变快照，后续编辑不会改变排队中的事件。
- 挽留第一页按钮继续仅含 action，不带原因页草稿。沿用已有统一发送通道，不新增上传接口、等待网络或修改系统卸载行为。
- BusinessTelemetry 与自有 Firebase 适配器的诊断日志仅输出遮盖值，真正传入上报接口的参数不被遮盖或改写。

## 验证

执行：`:app:testLocalDebugUnitTest :app:lintLocalDebug :metrics:testLocalDebugUnitTest :metrics:lintLocalDebug`，仅 localDebug。

- app 375 项单元测试、metrics 5 项单元测试通过，0 失败/跳过；两个模块 Lint 均 0 Error。
- RetentionTest 覆盖六个 UI 选项的精确协议映射、未选/无效值、两个按钮、Other 空输入、原文空白/换行、长度上限、切换选项不带旧草稿、页面恢复、返回前页及点击后的快照隔离。
- EventLogParametersTest 验证遮盖日志不修改真实发送参数，其他事件参数保持原样。
- 编译通过，`git diff --check` 通过。此次未改 XML/页面视觉，不重复截图验收；未向线上发送测试反馈，未声称 Firebase/数数后台已确认到账。
