# 新用户流程 Boolean 配置验证

2026-10-22，用户纠正需求文档：`new_user_flow_config` 本身是 Boolean 值，不是包含 enabled 的 JSON 对象。

- 核对项目当前 core SDK 1.0.15 的公开接口，实际方法名是 `ConfigRemoteManager.getBoolean`，没有 `getBool`。
- NewUserFlowConfig 删除 Gson / JSON 解析及旧 parse 方法，直接使用 SDK Boolean 读取。
- 保留未配置默认 true：SDK 对不存在的参数可返回静态 false，而非调用参数指定的默认值。因此与首页 Boolean 开关保持一致，先检查参数存在；这一步不解析 Boolean 或 JSON，真正的开关值只来自 getBoolean。
- 显式 true/false 原样生效；未初始化、未下发、异常或 null 默认 true。取消向上传播，不能把退出启动页当作读取失败并继续导航。
- 读取继续在 IO dispatcher，仍只在开屏结束后走现有路由判定，没有新轮询、UI 或广告调用变化。

验证：`:app:testLocalDebugUnitTest :app:lintLocalDebug` 成功，377 项单元测试通过，0 失败/跳过，Lint 0 Error。替换旧 JSON 测试为 Boolean、null/失败默认值及取消传播回归；已有开屏后路由/阶段恢复测试保留。此次没有 UI 变化，不新增截图测试。仅 localDebug，未改线上配置。
