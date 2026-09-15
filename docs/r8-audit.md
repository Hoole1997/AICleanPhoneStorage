# 三模块 R8 审计与维护说明

审计日期：2026-09-15。范围为 `settings.gradle.kts` 注册的 app、notification、metrics。

## 规则归属

| 模块 | 最终规则文件 | 装配方式 | 本次处理 |
| --- | --- | --- | --- |
| app | `app/src/main/keepRules/rules.keep` | AGP 9.3 的 keepRules source set；local/google release 使用 `optimization.enable = true` | 补上 Activity 包名前缀判断的类名契约，替换空模板，记录系统/依赖自动规则的归属 |
| notification | `notification/consumer-rules.pro` | `defaultConfig.consumerProguardFiles`；随 local/google × debug/release AAR 导出 | 补足匿名 TypeToken 泛型载体规则；9 个 Gson DTO 只保留可实例化性与注解字段；Worker 只保留稳定类名和二参构造；移除全局组件全成员 keep 和 Content.Companion keep |
| metrics | `metrics/consumer-rules.pro` | `defaultConfig.consumerProguardFiles`；随全部 AAR 导出 | RevenueConfigItem 缩小到 Gson 反射字段与可实例化性；普通 Kotlin 方法仍可移除 |

库原来的 `proguard-rules.pro` 改名为 `consumer-rules.pro`，明确其用途是提供给宿主最终混淆，库本身没有另开一次 R8。通知模块以前只在 release 导出规则，现在 debug AAR 被混淆宿主消费时也有同样保护。

本项目没有添加全局 `-keep class **`、`-dontwarn **`、`-ignorewarnings`、`-dontoptimize` 或 `-dontobfuscate`。

## 逐文件排查范围

混淆整理时扫描了 1,011 个模块文件；后续功能改动的最新范围见 [完整文件清单](r8-file-inventory.md)，可用 `python3 tools/r8/audit_files.py` 重建。清单记录每个文件的路径、大小、类别以及特殊入口所在行。

- 生产代码：app 200 个 Kotlin 文件，notification 40 个，metrics 8 个，共 248 个。
- 测试代码：91 个文件；测试类不导出到生产规则。
- XML：329 个文件，逐个解析，包括 Manifest、布局、动画、主题、字符串与多语言资源。
- 其他：JSON、Gradle/渠道配置、混淆文件，以及图片/字体等二进制资源。文本完整读取，二进制只核对类型和大小。
- 构建目录、Git 忽略的本机凭据、IDE/缓存不属于生产文件清单。实际生成的配置、mapping、AAPT 规则及 AAR 在产物校验中单独检查。

自动扫描并不等价于逐行人工语义审查。对命中的模型、反射/框架构造、类名、资源入口另行核查调用方式和依赖规则；未命中文件按静态调用链处理，不为普通业务类添加全包保留规则。

## 关键边界与判断

### app

- `HotStartAdCoordinator.isBusinessActivity` 读取 `activity.javaClass.name` 并比较 `com.example.aicleanphonestorage.` 前缀。显式保留本应用 Activity 类名，字段和方法仍允许混淆/优化。Manifest 目前也保护这些入口；不依赖广告 SDK 恰好保留所有 Activity 的副作用。
- Application、Activity、NotificationCleanerService 和 FileProvider 由 Manifest/AAPT 处理。清理文件不会按字符串反射构造业务 DTO。
- 自定义 View 通过 XML 或代码构造；检查 AAPT 生成的 `(Context, AttributeSet)` 入口。无需所有 View 全成员 keep。
- DialogFragment/BottomSheetDialogFragment 的默认构造由 Fragment 1.9.0 自带条件规则保护。默认 ViewModelProvider 的无参/SavedStateHandle 构造由 Lifecycle 2.11.0 自带规则保护；其余 ViewModel 由显式 Factory 构造。
- ViewBinding 的 `inflate/bind` 为直接调用。广告 renderer 直接实例化并通过 SDK 接口回调，不是按类名反射加载。
- 动画只用 ValueAnimator、系统 `View.ROTATION` 以及 XML 的 `scaleX/scaleY`，没有自定义字符串 setter 需要补规则。
- SQLite、DataStore、Bundle/Intent 使用显式字段键、基本类型及枚举 name；未发现应用自定义 Parcelable、Java Serializable、JNI、WebView JS bridge、Class.forName/getDeclared* 或动态 getIdentifier 入口。
- `javaClass.simpleName` 其余用法为日志；不为日志保留整包。普通 Kotlin lambda 的 `invoke()` 和自定义 `newInstance()` 工厂不是 Java 反射。

### notification

- `Config.kt`：NotificationConfig → Config，所有持久化字段均有 SerializedName。
- `Content.kt`：实际 `parsePushContents` 通过匿名 `TypeToken<List<Content?>>` 解析；需要 Signature 与泛型载体存活。Content 的 destination/iconDestination 是计算属性，Companion 是静态协议常量，没有反射保留需求。
- `Earthquake.kt`：EarthquakeResponse → Metadata、List<EarthquakeFeature> → EarthquakeProperties/EarthquakeGeometry → List<Double>；嵌套泛型字段也需要保护。EarthquakeInfo 由 MsgBuilder 序列化，保留其 10 个注解字段。
- Kotlin DTO 没有无参构造；Gson Unsafe 分配实例对 R8 不可见。仅保留字段不足以阻止类被优化为不可实例化类型，因此对这些具体模型保留类与注解字段，允许改名但不放开这些反射字段的优化。
- KeepAliveWorker 的类名写入 WorkManager 数据库，必须稳定；保留 `(Context, WorkerParameters)` 构造。其余方法按继承/静态调用追踪。WorkManager 2.9.0 本身也导出此契约，本模块规则仅限定自己的 Worker。
- Provider、MessageService、CoreService、DeleteReceiver 是 Manifest 入口，删除针对宿主所有同类组件的 `{ *; }` 规则。动态 Receiver 由直接构造追踪。
- PushPreferences 委托虽然接收 KProperty，但只用构造传入的显式 key，未读取 property.name；无需保留属性名称。
- TokenUploadCtrl 使用 JsonParser 树解析；RemoteViews 使用系统 View API 和 R 资源，未发现自定义反射 setter。

### metrics

- AdjustRevenueReporter 和 FirebaseRevenueReporter 均解析 `Array<RevenueConfigItem>`；共用一个模型，两个注解字段 name/rate 需要保护。
- FirebaseReporter/ThinkingReporter 用 Gson 序列化 Map，不反射本模块额外 DTO。
- MetricsModuleProvider 是 Manifest 入口；统计上报器、收益上报器及 Adjust listener 通过显式构造/接口调用安装。
- SharedParamsManager 委托传入稳定的显式 key，无业务属性名协议。

## 依赖核查

以实际解析结果和 `app/build/outputs/mapping/<variant>/configuration.txt` 为准，不能只看版本目录声明。

| 依赖 | 实际核查结果 |
| --- | --- |
| Gson | 声明 2.10.1；app 经 bill → ads-mobile-sdk → Tink 冲突解析为 2.13.2，后者带 TypeToken/SerializedName consumer rules。库规则独立保护自己的模型，不能依赖广告链抬升版本 |
| Fragment / Lifecycle / WorkManager | 合并配置含反射构造规则，避免手工重复全成员保留 |
| Glide、XXPermissions、UtilCode、Firebase | 合并配置包含依赖自带规则；应用没有自定义 GlideModule 或 JS bridge |
| Adjust 5.4.3 / ThinkingAnalyticsSDK 3.0.2 | AAR 自带规则已合入，包括 SDK 对外 API、反射/原生入口；不再复制到本库 |
| bill 1.0.51 | AAR 包含广告平台、适配器等规则，其中很多是 SDK 整包 keep；本次没有继续扩大 |
| core 1.0.15 | AAR 未携带 proguard.txt。检查 classes.jar 的特殊引用及 AdSlotSwitchController 字节码：广告配置用 JsonParser 树解析；本项目调用为显式构造/接口，未发现需补的 DTO 反射契约 |

### SDK 侧遗留问题

实际合并配置仍有第三方宽泛规则；本项目文件中的规则不能抵消第三方已经要求保留的类。

- Pangle 的 `com.pangle.global:pag-sdk-ad:unfat-8168-20260722151722-release` 自带 `-ignorewarnings`。这意味着 Release 构建成功不能用来声称依赖缺失检查完全通过。
- R8 报告缺失 `cn.thinkingdata.ta_apt.TRoute`（ThinkingAnalyticsPlugin 引用）和 `com.kwad.sdk.datacollection.KsSafetyPrivateDataController`（Kuaishou weapon 引用）。本次不添加静默规则；未在设备上验证这些 SDK 路径。
- `adImpl:1.2.21` 内若干 Kwai 类的 Kotlin Companion 元数据不完整，R8 仍有对应警告。
- Lint 的 3 条 GlobalOptionInConsumerRules 均来自外部依赖：上述 Pangle ignorewarnings，以及 `adapter-tpn-chartboost:9.12.0.1.1`、`chartboost-mediation-sdk:5.3.0` 的 renamesourcefileattribute；本次项目 consumer 文件没有此类警告。

这些问题归属发布的 SDK 依赖，不能靠保留本项目不存在的类修复。后续升级/替换相关 SDK 后需要重新构建、检查合并规则并做广告设备回归。

## 验证方法

### 1. 独立 R8 模型执行回归

```sh
./gradlew :notification:assembleLocalRelease :metrics:assembleLocalRelease
python3 tools/r8/verify_models.py --gson 2.10.1
python3 tools/r8/verify_models.py --gson 2.13.2
```

使用 JAVA_HOME 指定 JDK。脚本从 Gradle 缓存读取项目版本的 AGP/R8、Kotlin 与指定 Gson，不下载依赖。直接取项目编译的生产模型及纯 Kotlin 解析函数，用 R8 release/classfile 模式混淆后运行：

- 实际通知配置资产与文案列表，包括匿名 TypeToken。
- 两份实际收益配置资产。
- 嵌套地震对象、泛型模型元素、泛型数值元素。
- Content、RevenueConfigItem、EarthquakeInfo 的原始 JSON 键。
- mapping 中确认模型确实发生改名。

Gson/Kotlin 是宿主外部库，不导入广告 SDK 或其宽泛规则，也不导入 Gson 自带规则来替代本模块契约。这是模型契约测试，不是完整 Android DEX/设备测试。试验性去掉两个模块 consumer 规则后，负向对照在解析通知配置时出现 `Abstract classes can't be instantiated`，确认测试能捕获 R8 可实例化性被破坏。

### 2. Release / AAR / 测试 / Lint

本机 2 GB Gradle 堆在一次批量执行 R8 和 Lint 时 GC thrashing，改用单 worker、临时 6 GB 构建堆分阶段运行；未修改仓库的 gradle.properties，也未改应用堆设置。

```sh
./gradlew :app:assembleLocalRelease :app:assembleGoogleRelease \
  --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8'
# 完成库的 local/google × debug/release assemble 后：
python3 tools/r8/verify_outputs.py
```

产物校验读取两渠道最终 configuration/mapping、AAPT XML View 规则以及八个库 AAR 的 proguard.txt，防止规则文件存在却未接入构建。

### 执行结果

- 独立 R8 模型回归：Gson 2.10.1、2.13.2 均通过；移除模型规则的负向对照按预期失败。
- 单元测试：app 每渠道 173 例，notification 每渠道 21 例，共 388 例，0 失败、0 错误、0 跳过。metrics 无独立 JVM 测试源，其模型已纳入独立 R8 回归。
- 八个库 AAR：全部生成，并逐个核验 consumer 规则内容。
- 三模块双渠道完整 Release Lint：0 Error；app 每渠道 174 Warning，notification 每渠道 48 Warning，metrics 每渠道 9 Warning。主要为已有资源/样式/API、版本提示和第三方规则问题；本次未修改业务源码、资源或屏幕方向以消除这些提示。
- 双渠道最终 Release 构建通过；最终 configuration/mapping/AAPT 校验通过。每渠道确认 3 份项目规则已合入、20 个组件/Worker 名称稳定、4 个当前入口 DTO 存活，以及 6 个 XML 自定义 View 构造入口有 AAPT 规则保护。

设备限制：本次 `adb devices -l` 无设备，未执行安装启动、截图或完整广告/通知运行时回归。此次未改页面布局；不把 JVM 回归或构建成功表述为设备验证通过。

## 后续修改约定

新增反射 JSON 模型时，规则写入模型所属库；优先使用 SerializedName，保护具体反射字段与可实例化性。新增动态类名/原生回调/XML 字符串 setter 时记录实际调用位置，并增加对应最小规则和混淆后回归。依赖升级后检查实际 consumer rules，尤其是 SDK 整包 keep 与 ignorewarnings。

AGP 9.3 的 keepRules source set 与默认 Android 规则行为参考 [Android 官方优化配置](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)。库 consumer 规则的职责参考 [Android 库优化说明](https://developer.android.com/topic/performance/app-optimization/library-optimization)。Gson 反射/泛型排查参考 [Gson 官方 Troubleshooting](https://google.github.io/gson/Troubleshooting.html)。

## Release 测试包的签名与安装

后续安装排障：`app-local-release-unsigned.apk` 没有签名，直接安装出现
`INSTALL_PARSE_FAILED_NO_CERTIFICATES`。已使用本机 `~/.android/debug.keystore`
签出 `app-local-release-debug-signed.apk`，v2/v3 签名验证通过，并已在连接的
CPH2723 设备上执行 `adb install -r`，结果为 Success。安装保留现有应用数据。
签名前后 12 个 DEX/Manifest/resources.arsc 条目内容一致；仍是 Release 混淆代码。
这次只确认安装成功，未据此宣称页面、广告或通知的设备运行回归已通过。

下次重新构建 unsigned APK 后，需要重新签名。本机复现命令（先配置 JDK 的 JAVA_HOME）：

```sh
/Users/apple/Library/Android/sdk/build-tools/36.1.0/apksigner sign \
  --ks /Users/apple/.android/debug.keystore \
  --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
  --out app/build/outputs/apk/local/release/app-local-release-debug-signed.apk \
  app/build/outputs/apk/local/release/app-local-release-unsigned.apk
/Users/apple/Library/Android/sdk/build-tools/36.1.0/apksigner verify --verbose \
  app/build/outputs/apk/local/release/app-local-release-debug-signed.apk
adb install -r app/build/outputs/apk/local/release/app-local-release-debug-signed.apk
```

上述路径相对于项目根目录。调试证书仅用于本机测试，正式发布应使用正式签名配置。
签名命令及验证方式参考 [Android 官方 apksigner 文档](https://developer.android.com/tools/apksigner)。
