# 场景推送：仅 D1 地区多语言

2026-10-22。用户明确仅对齐 D1 支持的地区，不覆盖整个 App 的语言列表。

## 数据范围与结构

`docs/push/day-pool-templates/pushContentD1Json.json` 是英/中文结构示例；实际地区以 `notification/src/main/assets/pushContentD1Json.json` 为准：

| 标签 | 地区版本 |
| --- | --- |
| pt-BR | 巴西葡萄牙语 |
| es-MX | 墨西哥西班牙语 |
| es-ES | 西班牙西班牙语 |
| id-ID | 印度尼西亚语 |
| hi-IN | 印地语（印度） |
| ja-JP | 日语（日本） |
| ko-KR | 韩语（韩国） |

场景 `scene_push_content.json` 的 27 条文案各加入上述 7 套 `translations`，共 189 组正文/按钮译文。根部英文保留；每条翻译只有完整 HTML `text` 与 `buttonText`，不引入 title/desc。所有译文强调片段显式声明 font color="#FB4949" 和 b 标签，颜色不由客户端硬编码。

与上一版自动比对确认英文正文、按钮、id、condition 及数组顺序全部不变。文件约 3.5 万字符、4 万 UTF-8 字节，在已有 MAX_JSON/单条长度上限内，不提高资源限制。

## 语言选择

场景文案复用既有 `PushContentLanguage.candidates`，不改旧日龄推送：精确标签 → 通用语言 → 约定地区 → 本条英文。es 默认 es-MX，明确 es-ES 使用西班牙版本；pt/id/hi/ja/ko 分别匹配对应地区，大小写、下划线、旧 in_ID 印尼标签按现有规则规范化。

若远程指定了有效条目但缺少某语言，回退该条英文，不混入本地其他版本译文。语言切换只改变显示文字，不重置文案轮播、计时、通知 ID 或排队状态。

## 验证

- notification localDebug 153 项单元测试通过，0 失败/跳过；Lint 0 Error；app Kotlin 编译通过。
- 单测直接对比实际 D1 文件的语言键集合，保证每条恰好 7 套翻译；覆盖地区精确优先、通用/别名匹配、英文回退、坏翻译回退与跨语言轮播游标延续。
- Android 37 独立模拟器 4 项设备测试通过：189 条翻译的颜色/粗体范围及按钮；7 个地区在 1x/2x 字体下的折叠/展开 RemoteViews 测量与 reapply；前轮 HTML 颜色行为回归。
- 28 张截图在 screenshots/。已查看印地语、日语与西班牙语代表图；字形无高度裁切。仍沿用通知原有行数上限，大字体或长文案会出现省略号，未因翻译改动通知布局。
- 未修改 D1 数据、旧推送流程或频控 JSON，未上传线上远程参数，未构建 google 渠道。
