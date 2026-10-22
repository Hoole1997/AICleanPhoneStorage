# 新场景推送中文日志

新场景统一通过 notification 模块已有 `io.docview.push.utils.Logger` 输出，使用既有 `NotificationModule` Tag。此前新场景独立的 `ScenePush` Tag 已移除；不改变旧推送日志。

Android Studio Logcat 过滤：`tag:NotificationModule message:场景推送`。沿用原有 Logger 总开关及 debug/release 默认行为。

日志覆盖：监听注册/释放、配置来源/开关、触发候选、延迟与独立间隔、事件入队/出队/合并/清空、发布前校验、首次通知提交、频控/轮播记录、循环序号/声音状态/结束、划除回调及旧轮次回调忽略。保留中文场景名/编号、通知 ID、有界计数、等待时长和原因；不记录路径、URI、包名、通知正文、完整远程参数或会话 token。高频电池快照及动画帧不逐次输出。

2026-10-22 验证：local 编译与 notification Lint 通过；134 项单元测试、API 37 的 9 项场景设备测试通过。实际捕获 104 条新场景中文日志，均来自 `NotificationModule`，已确认入队→出队→首次提交→重复提交→划除→释放展示位日志链路。

实录：[中文 Logcat](chinese-logcat-api37.txt)，[设备测试结果](chinese-log-device-tests.txt)。本次没有修改界面布局，不重新截图；没有构建 google 渠道、提交 Git 或更改远程参数。
