# 场景通知 HTML 颜色配置

2026-10-22。按用户要求移除客户端 b/strong 固定标红，颜色完全由文案 HTML 指定。

示例 JSON：

```json
{
  "id": "1108_3",
  "buttonText": "Manage",
  "condition": "any",
  "text": "<font color=\"#FB4949\"><b>New file saved!</b></font> Manage files."
}
```

`b/strong` 仅加粗；`font color="#RRGGBB"` 可以独立指定颜色，支持单/双引号及与粗体的两种嵌套顺序。不指定颜色就保留通知默认文本颜色，不为旧远程纯 b 文案补红色。可执行 HTML、图片与链接仍按原规则过滤，不启用 WebView/CSS。

本地默认 27 条文案已逐条显式配置 #FB4949。与修改前 JSON 对比确认：可见纯文本、id、buttonText、condition 和数组顺序不变；未修改频控 JSON、通知 ID、循环/FIFO 或纯文本埋点。

验证：

- localDebug notification 150 项单元测试通过，0 失败/跳过，Lint 0 Error；app Kotlin 编译通过。
- Android 37 模拟器 6 项设备测试通过（device-tests.txt）：纯粗体不添加颜色；明确配置颜色不被粗体覆盖；颜色/粗体可独立及嵌套；27 条本地默认的颜色和粗体范围一致；单一 TextView、HTML 安全过滤以及 1x/2x 字体布局与 reapply 回归。
- `default-red.png` 是实际 RemoteViews 的离屏渲染，确认默认强调仍为红色，普通正文保持默认样式。没有改变业务图标、动效、点击或排队逻辑。
- 仅 local 渠道，未改线上参数、未构建 google 渠道。
