# 启动页 consent 调用验证

2026-10-12，当前 `bill:support_risk_1.0.10`。

## 接入

- 语言准备、原有通知权限流程完成且 StartupActivity 前台有焦点后，在 Activity lifecycleScope 顺序调用 `getInstance(applicationContext)` 和挂起的 `gatherConsent(this@StartupActivity)`。
- 不读取、不记录、不判断 Boolean 返回值。`consentCompleted` 只表示本次调用结束；广告和导航状态都受此完成标记约束。
- 调用不放在 repeatOnLifecycle 中，SDK 弹层失焦不会重复启动。已完成的配置重建复用 ViewModel 标记；未完成的 Activity 销毁会取消其协程，新实例重新调用，完成标记不写 SavedState。
- 当前 SDK 会捕获内部取消异常并返回，因此调用结束后显式 ensureActive；销毁或取消不能借迟到返回放行广告。SDK 的表单关闭和底层 HTTP 取消由其实现决定，应用不承诺强制关闭 SDK UI。
- 其余 SDK 异常记录日志后继续原流程；返回值和异常均不被当成用户已同意。Application 仍只初始化广告 SDK，首个显式启动广告请求经 StartupAdCoordinator 发起。

## 验证

- 仅 local：编译、APK/测试 APK、Lint 通过。262 项单元测试，0 failures/errors/skips；Lint 0 errors、211 warnings。
- OPPO CPH2723 / Android 16：StartupAdCoordinatorTest、HotStartAdDeviceTest、StartupRenderingTest 共 11 项通过，见 [device-tests.txt](device-tests.txt)。覆盖等待 consent 不请求广告、重复完成不重复请求、原通知路由、配置/进程恢复、权限前置、断网等待、迟到回调、热启动及原页面布局。
- 新增 2 项单元用例与 1 项真实协调器用例；设备用例注入调用完成状态及假广告请求，不伪造 SDK 同意结果或发送测试曝光。
- 安装最终 localDebug 后真实冷启动：17:53:47.990 `Startup consent: started`，17:53:47.996 `completed`，17:53:48.022 `Startup splash: requested id=1`，随后进入首页。连续日志采集证明顺序，见 [client-stage.txt](client-stage.txt)。没有查看或记录 allowed 的值。
- 本次 SDK 调用直接返回，没有代用户操作同意表单；不据此证明所有地区的 UMP 表单交互或广告平台填充。
- [正常布局](startup-normal-fixture.png) 和 [320×568dp / 2x 字号](startup-small-font2-fixture.png) 来自真机原生视图渲染测试，已实际查看；本次未改变启动页视觉资源。

未在本机构建、安装 google 渠道，未自动提交 Git 或推送。
