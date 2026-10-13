# Collect / XM 分发与联调记录

记录日期：2026-10-12。来源是用户为本产品提供的 SDK 下载文件；不记录下载链接的签名参数、Token 或后台密钥。正式接入用于 `google`，分发声明包名为 `com.ai.clean.phone.storage.junk.cleaner`。普通 `localDebug/localRelease` 不合入 SDK；用户另行授权的 `localRiskDebug` 使用真实联调 applicationId 和测试地址，可在本机编译、执行。Google 渠道仍只允许 GitHub Actions 构建。

## 原始文件与完整性

`app/libs` 保存 SDK 方的原始字节；`scripts/prepare_product_risk_sdks.py` 生成构建副本，`gradle/product-risk-sdk.gradle.kts` 仅将副本合入 `google` 和 `localRiskDebug`，不覆盖源文件。

| 仓库文件 | 分发文件 / 版本 | 原始 SHA-256 |
|---|---|---|
| `app/libs/collect-1.3.2.aar` | 用户提供 `collect-1.3.2.aar` | `f368cfdc16a9654733798ca9b2b23051e9526693aa06c96ca24fe5fb6fbc8c16` |
| `app/libs/xm-0.0.5.aar` | `AICleanPhoneStorage_005.zip` 内 `AICleanPhoneStorage_005_202610120837.aar`，分发工程版本 `0.0.5` | `67f057f15130d3e5f538b786cd3a4388e4e45b2c122d6473b2463b5fcde1fd77` |
| `app/libs/xm-0.0.5-replacement_mapping.txt` | 同一 ZIP 的原版映射文件 | `74385466e32da532b5ef68e1dfe3ce9c693c2b911f3962e7ef84b795178b86e7` |

分发 ZIP 的 SHA-256：`91a80aca1ecdca033dbe897977321695d1c6e1a0cb796ce98564661bc42f47cc`。没有复制 ZIP 中的内部字符串表、加固构建配置或其他产品资源。

## 公开入口与产品绑定

Collect 的真实入口由发布类的公开签名核对：

- `com.ai.clean.phone.storage.junk.cleaner.dckx.SafeObserverBuilder.postMessage(Context, Consumer<String>)`：获取指纹。
- `SafeObserverBuilder.invokeService(Context)`：显式设备信息上传，只由宿主确认买量归因后调用一次。

XM 入口包为 `com.ai.clean.phone.storage.junk.cleaner.ccefecb`，官方映射对应类 `aeacefbcddb`：

| 官方名称 | 本次包入口 |
|---|---|
| `initRiskDetection` | `eadddaedeaaebbdce` |
| `refreshRiskDetection` | `fdbfceeededfd` |
| `clearCacheRiskDetection` | `eddfbffdcceeceb` |
| `setEventCallbackRiskDetection` | `ebfcebaefcbabbf` |
| `setDebugEnabledRiskDetection` | `afbbcecfa` |
| `getResultRiskDetection` | `becebfbdedbf` |

SDK 入口均为 native。宿主读取当前 variant 的 `XM_RISK_BASE_URL`，空地址明确不可用，不套用 EasyLoanCalc 的服务地址。

| 构建 | 实际 applicationId | 风控配置 |
|---|---|---|
| `google` | `com.ai.clean.phone.storage.junk.cleaner` | `https://aiclean.gravitonlumina.com/`，启用 |
| 普通 `localDebug/localRelease` | `com.leafmotivation.quizguessoncolor` | 地址为空，关闭，不打包 SDK |
| `localRiskDebug` | `com.hotpotgames.happysave.global` | `http://test-happysave.movieapi.novhop.com/`，启用 |

联调参数独立放在 `app/src/localRiskDebug/config.properties`，不改普通 local/google 配置。HTTP 仅通过该 variant 的 network security config 放行精确测试域名，不放行子域名或其他明文地址。SDK 的 `com.ai.clean…` namespace 是入口类路径，不等于 APK 的 applicationId；随包 `IntegrityReportServiceTest` 的协议测试以运行时包名派生请求密钥，支持按真实 applicationId 联调。但 Collect/native 是否还有分发包绑定须由真机结果确认，不能仅凭类路径或协议测试保证跨包可用。

测试包未在当前 Firebase 项目注册，`localRiskDebug` 关闭 Google Services 资源生成及远程推送，保留普通 local/google 原有 JSON 和包名。不能手改 `google-services.json` 冒充注册；Firebase、FCM、Remote Config 联调需要在 Firebase 中注册匹配的测试应用并取得其真实配置。

## Manifest 和资源审计

| 项目 | Collect | XM |
|---|---|---|
| minSdk | 26 | 24 |
| 归档 classfile 版本 | Java 8 / 52 | Java 8 / 52 |
| ABI | arm64-v8a、armeabi-v7a、x86、x86_64 | 相同四 ABI |
| 权限声明 | ACCESS_NETWORK_STATE、READ_PHONE_STATE | INTERNET、ACCESS_NETWORK_STATE |
| 自动组件 | `com.ai.clean.phone.storage.junk.cleaner.logic.FastStoreBuilder` provider，authority `${applicationId}.p4038c9`，exported=false | 无 provider |
| 包可见性 | 无额外查询 | 查询 `com.android.vending` |

宿主没有新增 READ_PHONE_STATE 的运行时申请。Collect 自带 provider 会由 Manifest 合并；“仅买量用户调用显式上传”不等于已经证明 provider 的内部自动行为不会采集数据，仍以 SDK 方披露为准。

## 重复注解及 R8

两 AAR 均内嵌 `com/android/tools/r8/annotations/LambdaMethod.class`。它是有三个 String 成员（holder/method/proto）的 RUNTIME/TYPE 注解；两个定义的公共签名一致，字节因各自加固 Keep 注解而不同：

- Collect 定义 SHA-256：`a83c0d3100e3d39f647ceb130870055256f71eeb4c0affc851c7778a9aa46ec9`
- XM 定义 SHA-256：`14f27f33eb281a74141c77cbcad3fba44ca68e76b73fb506f4ad42a1dd0691d3`

公开 lambda 外壳分别有 65 / 23 个类引用该注解描述符，native 内部是否反射访问不可见。构建副本因此只删除 XM 中的同名定义，保留 Collect 的单一定义并保留注解，不将两份同时删掉。其余 AAR 条目保持原字节。

Collect consumer 中重复且过宽的 android/androidx/com.google keep 需要限定；`src/xmRiskSdk/java` 与 `src/xmRiskSdk/keepRules/vendor-risk.keep` 由 google 和 localRiskDebug 共用，保留产品 SDK 包、加固所需 Kotlin/Kotlinx、Play Integrity/GMS Tasks/Gson，以及 SDK 网络所需 OkHttp/Okio。普通 local 使用 `src/riskDisabled/java` 工厂，不合入 SDK 和这些规则。不能用 dontwarn 掩盖未声明依赖。

## 冷启动和失败边界

分发 ZIP 附带的 SDK 单测源码明确验证：`DetectionSessionCoordinator.init_keepsUsingCachedResultRegardlessOfAge` 会无限期复用缓存，且 `init_usesCachedResultWithoutStartingRemoteTask` 不启动远端任务。为了每次冷启动取得本次环境结果，宿主每进程唯一检测依次调用事件注册、官方 clearCache、结果回调注册、init；没有 refresh、定时复测或 VPN 变化复测。

结果回调在 init 前可注册，见分发测试 `RiskDetectionUtilsTest.becebfbdedbf_waitsWhenCalledBeforeInitRiskDetection`；不依赖 `XM_SDK_Init` 事件是否发出来取得结果。原生入口在单线程有界 worker 执行，宿主指纹等待最多 5 秒，并由应用级协调器限制全链路总等待。SDK 没有公开取消入口：取消只撤销宿主等待、清除 continuation、移除未执行任务并忽略迟到回调，不能承诺 SDK 内部网络任务同时停止。显式 retryCount=0，不扩大为长时间远端重试。

SDK 公开结果没有 success 字段，远端状态只能由 `deviceId/errorMessage/rawResponse` 与九个布尔字段判定。分发测试记录：HTTP 200 有效响应且 ID 缺失可返回 `deviceId 缺失`；HTTP 502 返回 `HTTP 502`；HTTP 200 解析失败返回 `响应解析失败`。宿主必须用公共、可单测 mapper 严格区分有效拒绝与失败，网络、解析、超时及未知状态都失败放行，不能直接将 `isRemoteAbnormal=true` 当成可限制的远端风险。

SDK debug 始终关闭，不输出指纹、Token、deviceId 或原始远端响应；XM 事件通过宿主已有数数 Reporter 定向转发，分析上报失败不阻断检测结果。首次策略截止不关闭遥测出口，SDK 后续 Play Integrity 补报事件仍转发，但迟到检测结果不再施加限制。

## 宿主编排与广告边界

- 统计注册先于显式 XM 检测和广告 SDK 初始化；注册等待最多 5 秒，统计不可用时不启动 XM，广告流程仍继续。
- 指纹、XM 结果及初次策略同步统一在 15 秒总期限内；异常/超时整体撤销本桥接拥有的 PROCESS 限制，结果不得伪装成成功。
- 同一平台的多个风险采用 OR 聚合；最新在线配置重新计算已完成快照，全部平台通过 `AdPlatform.entries` 覆盖 MAX。平台 API 失败最多尝试 5 次，旧版本重试不能覆盖新版本。
- 业务原生、插屏及开屏的统一请求入口等待首次决策后，重新核验页面前台与业务条件。启动页的通知权限及 `gatherConsent` 顺序不变，仍不读取 `allowed`。
- 保留 SDK 内置 `ad_show_fail(reason=risk_restricted)`。当前版本没有公有单项平台排除观察 API，宿主请求入口不足以证明实际跳过某个平台，故不推断补报 `Ad_Block_*`。
- `BillConfig.onAdRevenue/onAdClick` 组合已有回调，只发送 Collect 协议广播；原有 Adjust/Firebase 收益 Reporter 不重复注册或重复上报。
- 已确认买量时才调用 Collect 显式设备上传，归因等待最多 30 秒。Core 以非线程安全列表同步派发归因监听：每进程最多保留一个不捕获 Context 的信号门闩，等待结束清除 Deferred 引用，后续无工作，避免并发移除破坏其他监听。

## 验证范围

本次本地复核：`assembleLocalRiskDebug`、`lintLocalRiskDebug`、`testLocalDebugUnitTest` 和 `assembleLocalDebug` 通过；共 294 项 app 单测（其中风控 32 项）无失败。联调 APK 为 `app/build/outputs/apk/local/riskDebug/app-local-riskDebug.apk`，产物元数据确认实际包名为 `com.hotpotgames.happysave.global`，Google Services 资源生成任务跳过，普通 local 仍为原包名。联调构建使用 SDK 公有 `skipRuntimeAdIdConfig`，不等待不存在的 Firebase 在线广告 ID；该设置不作用于普通 local 或 Google。

在无网络的 Android 37 / 16KB 页模拟器运行联调 APK，观察到 `FAILED_OPEN / SDK_ERROR` 后原 consent 调用开始、完成，随后到达首页；未发现应用崩溃。模拟器首次启动有系统原生库兼容提示，需要确认后才恢复窗口焦点。该提示包含多项广告库和加固库，不属于风控等待；本次没有屏蔽提示。两个 SDK arm64 的 `PT_LOAD` 对齐值均为 65536，但系统仍列为 Unknown error，因此不能仅凭该字段宣称完整 16KB 兼容；需要后续真实设备/SDK 方验收。离线运行不验证测试服务端成功结果，15 秒超时与迟到回调边界由已有单测覆盖。

ZIP/AAR 清单、Manifest、SHA-256、公开 JVM 方法签名和随包 SDK 测试源码已静态核对，未执行随包 SDK 测试。普通 local 已通过编译、Lint、32 项宿主风控单测及 2 项 metrics 单测；localRiskDebug 的编译与真实 SDK 联调另行记录。宿主单测不覆盖加固 JNI 的实际阻塞或网络回调时序，不能代替真机验收。

未在本机编译、打包、安装或执行 Google 渠道，Google 尚未通过 Actions 验证；其 release 的依赖合入、Manifest 合并及 R8 必须由 GitHub Actions 确认。
