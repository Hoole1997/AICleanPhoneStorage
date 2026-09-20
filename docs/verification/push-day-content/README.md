# 日龄文案池验证

2026-09-20；实现与运维规则见 [日池接入说明](../../push/day-content-integration.md)。

- 用户提供的 D1–D5 文件与 `notification/src/main/assets/pushContentD*Json.json` 逐字节一致，条数为 29/28/28/28/28，总计 141。
- local Debug APK / AndroidTest APK 构建通过；app、notification 的 local Lint 通过。
- notification 38 项、app 206 项单元测试通过（合计 244）。新增测试覆盖自然日切池、D6 及以后连续轮播、重启游标、语言切换不重置、区域匹配、翻译整体回退、独立远程回退、错误池号/大小边界，以及 D6 合并超过 256 条时不截断。
- API 36 真机的 `NotificationBusinessRoutingTest`（2 项）、`DayPoolNotificationDeviceTest`（1 项）、`ResidentNotificationTest#independentPendingIntentsAndOneTimeRouting`（1 项）通过，共 4 项。
- 渲染用 D1 第一条原始数据，覆盖 en / pt-BR / es-MX / es-ES / id-ID / hi-IN / ja-JP / ko-KR，360dp 宽、1 倍和 2 倍字号，共 16 张截图。这里只构建 RemoteViews，不发布普通通知、不推进用户轮播、不切换用户语言。
- 真机发现旧展开按钮固定高度在 2 倍字号下裁切；基础、Android 12、Android 13+ 三套布局共用自然测量的操作区，保留各版本原最小尺寸后通过断言。正文沿用原通知两行省略规则。
- 设备首启日期记录已确认为 `no_backup/push_day_content/first_launch.txt`，不是可恢复 SharedPreferences 或安装时间。

截图是现有通知内容区在浅色宿主背景上的原生渲染结果；系统通知面板的装饰与最终高度由 Android/OEM 决定。远程覆盖/异常回退通过可控单元测试验证，没有修改线上 Firebase 参数，也不以本次测试声称真实推送送达率或性能数据。

本机仅编译、安装 local 渠道。未自动提交 Git。
