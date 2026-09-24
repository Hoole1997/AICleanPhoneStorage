# 按日拆分的 Remote Config 文案模板

**结构示例，尚未接入客户端。每个文件只有一条英/中文示例，不是正式 85 条文案及 D1～D5 分配方案，不要直接上线。**

| 参数名 | 对应模板 | 用途 |
| --- | --- | --- |
| pushContentD1Json | pushContentD1Json.json | 首次启动自然日 D1 |
| pushContentD2Json | pushContentD2Json.json | D2 |
| pushContentD3Json | pushContentD3Json.json | D3 |
| pushContentD4Json | pushContentD4Json.json | D4 |
| pushContentD5Json | pushContentD5Json.json | D5 |

Remote Config 参数值填写对应文件完整 JSON 对象文本。每个参数内包含该日的全部文案与各语言译文，D6+ 不新增参数，客户端按 D1 → D2 → D3 → D4 → D5 的顺序合并，正式五池合计 85 条。

- 五个参数使用相同 configVersion，并在同一次 Remote Config 发布中更新。schemaVersion 固定为新协议版本 2。
- 拉取/激活后，客户端一次读取并验证完整五池快照，再原子替换缓存；字段非法、缺池、版本不一致或文案总数不符合要求时保留上一份有效快照，没有有效快照时使用完整本地兜底。
- 正式配置每个日池非空、id 在五池中唯一、字段类型及动作有效。只缺某条的目标语言时回退该条英文，不使整个快照失效。
- 翻译字段沿用 title/desc/buttonText；根部为英文兜底，translations 按 App 的语言标签配置。App 语言切换不切换业务池或重置游标。
- 本地 assets 也应对应拆为五份并使用相同协议；目前只有文档模板，现有 assets 和运行时代码尚未修改。
- 本地 65,536 字符读取限制会分别作用于每个参数，而 Firebase 项目总参数值字符限额不会因拆分增加。
- 若均分为每天 17 条，按当前英文长度模拟 16 种语言，每池约 3.6 万字符（压缩）或 5.5 万字符（缩进）；实际译文仍须逐池测量。不是强制每天 17 条。
- 不给 D6 的 85 条再次套用单参数 65,536 字符限制，因为它是内存中由已验证的五池合并得到的列表；仍保留明确的内存/文案数量边界。
- 买量拦截、首次启动自然日、重装归零、顺序循环、每日游标规则和无限每日次数属于分发策略，不在五份文案配置中重复维护。

客户端仍需新增五参数读取、完整配置快照校验、日龄选池和语言选择。旧 pushContentJson 可保留供旧客户端使用；若保留旧参数，其内容仍占 Firebase 总额度。
