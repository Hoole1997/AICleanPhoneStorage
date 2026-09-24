# 推送文案协议与多语言模板（设计稿）

这是协议说明与建议模板，**尚未修改运行时解析器或上线远程配置**。当前客户端会忽略 `translations`，仍显示根部英文。

## 当前实现

本地文件：`notification/src/main/assets/pvvvvush_content_config.json`，当前 103 条，均为英文。
远程配置：Firebase Remote Config 参数 `pushContentJson`，内容是 JSON 数组文本，不是下载文件的 URL。

当前启动先读取已缓存的有效远程文案，没有缓存/缓存解析失败时读取 assets。远程配置初始化后再次读取；新的有效列表整体替换当前列表并缓存，空或异常远程值保留已有列表。按保存的索引顺序循环取文案。

| 数值 | iconType 内容区图标 | actionType 点击入口 | 图标资源 |
| --- | --- | --- | --- |
| 1 | 垃圾清理 | 首页 Smart Cleaning / 垃圾扫描流程 | ic_resident_clean |
| 2 | 网络流量 | Network Traffic | ic_tool_network |
| 3 | 照片压缩 | Photo Compress | ic_tool_compress |
| 4 | 闲置文件 | Unused Files | ic_tool_unused_files |
| 5 | 截图清理 | Screenshots | ic_tool_screenshots |
| 6 | 应用图标 | 首页，不自动进入清理工具 | ic_launcher |
| 1001 | 大文件 | Large Files | ic_tool_large_files |
| 1002 | 通知清理 | Notification Cleaner | ic_tool_notifications |
| 1003 | 应用管理 | App Manager | ic_tool_apps |

- 两个字段可不同；例如 iconType=1001/actionType=1 会显示大文件图标但进入垃圾清理，通常应保持一致。
- iconType 不控制状态栏小图标，小图标统一由宿主 smallIcon 提供。
- 通知整体点击和按钮使用同一个 PendingIntent，经启动页、首页分发，沿用业务入口的权限检查。点击不等同于自动执行删除。
- 未支持的 actionType（包括 0、7、8、12 等）会让整条文案被过滤；未知/缺失 iconType 回退为 actionType 对应图标。
- 本地文案没有使用 2 和 5，但代码支持远程配置这两个入口。
- 常驻通知的入口和多语言来自宿主资源，不由这份普通通知文案列表控制。

## 建议的兼容格式

见同目录 `content-multilingual-template.json`。保持数组根结构、已有 id 和两个类型编号：

- 根部 title / desc / buttonText 为默认英文，兼容旧客户端。
- translations 按语言标签提供完整的三字段文案，一条业务内容只用一个稳定 id。
- 不把不同语言复制成独立数组项，否则当前轮播会把不同语言依次推送。
- 不为每个语言重复 iconType/actionType，避免翻译修改业务路由。
- 样例中仅展示三种翻译；其他语言按同一结构补充，并不代表已完成 103 条文案的全部翻译。

当前 App 支持的标签：`en`、`zh-Hans`、`hi`、`es`、`ar`、`pt-BR`、`bn`、`ur`、`id`、`ru`、`fr`、`de`、`ja`、`ko`、`vi`、`tr`。英文使用根部字段，无需在 translations 重复维护 en。

建议复用 AppLanguages 的语言匹配规则：应用内手动语言优先，否则跟随系统；语言规范化后选择对应 translations。目标语言缺失或三字段不完整时整条回退根部英文，避免标题和正文混用不同语言。

## 下发与实现边界

- 新版解析器实现后，本地 assets 和远程 pushContentJson 使用完全相同的数组结构。远程后台参数值填写数组 JSON 文本，不能再包一层 `{"pushContentJson": ...}`。
- 仅添加 translations 不会让当前客户端自动翻译；需要增加翻译 DTO、解析校验及语言选择器。
- notification 模块通过 NotificationHost 接口获取宿主当前语言，不反向依赖 app 的语言控制器。
- 在构建通知时解析当前语言，不能在进程初始化时永久缓存某个语言；语言切换后的下一条通知要生效。
- 展示、点击、进入埋点继续共用实际展示文案的快照，避免展示译文、上报英文。
- 本地与远程共用同一解析和校验流程；坏配置不覆盖上一份有效缓存。新增 Gson DTO 要配套检查 consumer-rules.pro。
- 当前 getString 上限是 65,536 个 Kotlin String 字符，列表最多处理前 256 条；id/title/desc/buttonText 分别截到 100/120/500/40 个字符。103 条英文原文件已有 22,687 个字符，扩展 16 种语言很可能超出原远程字符串上限。
- 实现多语言时必须同时设计有界的总配置大小、语言数和单字段长度。例如可设 512 KiB UTF-8 总上限，并限制到 App 支持的语言集合；最终阈值应按完整翻译后的实际大小核定，不能只删除限制。
- 文案切换不会实时自动下载：当前远程 fetch 最小间隔为 43,200 秒（12 小时），仅 REMOTE_PUSH_ENABLED=true 且 Firebase 已初始化时启用远程读取。
- 当前没有动态占位符替换。容量等实时数值若以后加入，需另行定义白名单变量和替换规则，不能把占位符当成现成能力。

## 文案与路由的一致性

当前部分 actionType=1 的文案写了重复/相似照片，但实际进入垃圾扫描，当前扫描并不包含照片分析。多语言整理时应先把原文改为与现有功能一致，再翻译；不能靠改 title/desc 改变实际业务行为。
