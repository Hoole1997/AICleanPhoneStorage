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
- 未选或无效选项使用 reason=none。输入内容与原因独立，始终保留原始空白/换行和既有 500 字符上限；只有未输入时使用空串，非 Other 选项也携带正文。
- 现有“清理结果不符合预期”和“运行卡顿”映射 poor_user_experience，“缺少功能”映射 unmet_needs；不增加原界面没有的 found_similar_app 选项。
- 输入、选择、曝光、返回不发送反馈。点击生成不可变快照，后续编辑不会改变排队中的事件。
- 挽留第一页按钮继续仅含 action，不带原因页草稿。沿用已有统一发送通道，不新增上传接口、等待网络或修改系统卸载行为。
- 按用户最新要求，BusinessTelemetry 与自有 Firebase 适配器日志均直接显示实际参数；空串为空，非空文本为原文，不再替换为 [redacted]。

## 验证

执行：`:app:testLocalDebugUnitTest :app:lintLocalDebug :metrics:testLocalDebugUnitTest :metrics:lintLocalDebug`，仅 localDebug。

- app 375 项单元测试、metrics 5 项单元测试通过，0 失败/跳过；两个模块 Lint 均 0 Error。
- RetentionTest 覆盖六个 UI 选项的精确协议映射、未选/无效值、两个按钮、Other 空输入、原文空白/换行、长度上限、切换选项保留输入正文、页面恢复、返回前页及点击后的快照隔离。
- 上述 380 项是最初字段接入时的验证。用户后续要求日志显示原文，已删除脱敏帮助类及其测试；最新验证见下方记录。
- 编译通过，`git diff --check` 通过。此次未改 XML/页面视觉，不重复截图验收；未向线上发送测试反馈，未声称 Firebase/数数后台已确认到账。

## 2026-10-22 日志原文显示修正

用户确认日志也需要显示实际填写内容，因此移除统一脱敏帮助类。BusinessTelemetry 直接打印发送参数，自有 Firebase 适配器打印规范化后的实际参数；没有输入时依然是空字符串，有输入时按既有 500 字符上限显示原文，最新规则不再限定 Other，不修改日志开关。

验证：`:metrics:testLocalDebugUnitTest :app:testLocalDebugUnitTest --tests '*RetentionTest' :metrics:lintLocalDebug :app:compileLocalDebugKotlin` 通过。metrics 3 项、RetentionTest 9 项，共 12 项测试通过；覆盖未选择及 Other 空输入、非空原文、切换选项与快照边界。生产代码已无脱敏帮助类或 [redacted] 替换逻辑；没有向线上发送测试反馈。

## 2026-10-22 输入内容不再限定 Other

用户确认选中 too_many_ads 等任意原因时，也必须提交输入框实际内容。已去掉 Other 条件，reason 只表达选项，has_other_content 只表达点击时的输入；未选择原因时也保留正文，仅空输入传空串。Keep/Uninstall 使用同一规则，不修改前页埋点。

localDebug 定向 RetentionTest 10 项测试及 app Lint/编译通过，0 失败/跳过，Lint 0 Error。覆盖先输入再选择广告过多、全部原因及未选/无效选项、切换原因、重建、清空输入、两个底部按钮和点击快照。UI 未改变，未向线上发送测试反馈。
