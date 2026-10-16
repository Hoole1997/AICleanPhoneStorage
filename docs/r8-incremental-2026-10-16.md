# 2026-10-16 增量混淆审计

## 范围与结论

范围包含 `be09530a` 本身、之后至 `e7c85b5` 的提交，以及当前工作区的广告加载取消、大文件视频删除和文件元数据校验修复。按 `be09530a^` 对比，检查了 89 个新增/变更的生产 Kotlin/Java 文件，并核对对应 Manifest、布局、资源、Gradle、AAR 与 consumer rules。未修改渠道配置。

新增业务代码不需要增加 `keep` 规则；现有应用、AndroidX、SDK 和 AAPT 规则覆盖所需入口。尤其不能为此次改动增加整个应用/测速/通知模块的全成员保留规则。

| 改动 | 入口和数据契约 | 判断 |
| --- | --- | --- |
| 首页角标与统一开关 | DataStore 显式 key、枚举固定 `key`、Boolean 配置 | 没有反射 DTO，不需要新规则 |
| 测速与备用端点 | OkHttp 接口、显式协议文本解析、直接创建 ViewModel/状态 | 不需要业务 DTO/协程全包保留 |
| SpeedTestActivity / SpeedTestRingView | Manifest 组件、XML `(Context, AttributeSet)` 构造 | AAPT 自动规则覆盖 |
| SpeedTestFailureDialog | FragmentManager 恢复、公开无参构造、Bundle 基本类型 | AndroidX Fragment consumer rules 覆盖 |
| Alarm 唤醒 | Manifest Receiver、显式 PendingIntent、JsonReader 按字段读取 | Receiver 由 AAPT 保护；Alarm 模型不依赖 Gson 字段反射 |
| 通知来源与 Click/Enter 配对 | 固定 Intent/Bundle key、枚举显式 value、普通对象 | 无新增序列化类名契约 |
| MAX renderer / 启动同意流程 | renderer 直接实例化，经 SDK 接口回调 | 静态调用和 Bill/MAX consumer rules 覆盖 |
| XM/Collect | 加固 DEX/JNI、产品 SDK 结果模型与回调 | 已有 `vendor-risk.keep` 必须保留；local 当前开关开启，不能误认为只有 google 使用 |
| 广告加载取消 | 窗口 tag 中直接保存请求对象，显式回调，协程取消 | 不按名字反射加载，不需要 keep |
| 大文件视频删除与校验 | SQLite 显式列名、系统 MediaStore API、BasicFileAttributes | 不需要 keep；失败 reason 为固定字符串，异常 simpleName 仅诊断用途，允许混淆 |
| 广告调试工具 | debug/riskDebug source set，release 使用空入口 | 不为调试类添加 release keep |

## SDK 规则边界

- `app/src/main/keepRules/rules.keep`、Trustlook 规则，以及 notification/metrics consumer rules 保留原有契约。
- `app/src/xmRiskSdk/keepRules/vendor-risk.keep` 保留产品 SDK 包、加固加载器、回调与结果模型，以及 SDK 原生路径明确使用的运行时依赖。当前 local 通过 `xmRiskEnabled=true` 合入；google 共用该源码/规则，但未在本机编译。
- 原版 Collect/XM AAR 哈希与重复注解清单验证通过。准备脚本不改 native 内容；仅处理重复注解与已审计的过宽 consumer rules。
- Bill `support_risk_1.0.12` 和 ad-risk `1.0.8` 自带风险插件接口规则；Bill 同时带有 MAX/AppLovin 规则。没有复制这些规则到应用，也未增加 `dontwarn` 或 `ignorewarnings`。

## 工具更新

- `tools/r8/verify_outputs.py`：按渠道风控开关核验实际合入的 SDK keep、SDK 在最终 DEX 中的原名定义、测速 Fragment 恢复构造、Alarm 默认资产和 debug 工具排除情况；APK 文件名从产物元数据读取。
- `tools/r8/verify_models.py`、`ModelProbe.java`：加入 Alarm 实际流式解析器的 R8 后回归，检查本地默认、自然/买量策略与一分钟间隔；沿用 Gson 嵌套模型/字段名/泛型回归。
- 独立探针不引入广告 SDK 的宽泛 keep，避免依赖过度保留掩盖模型问题。

## 验证边界

本次仅对 local 执行构建；google 只检查共用源码、依赖装配和规则。未安装本次 unsigned release，也未将编译通过当作所有广告平台、原生风控和设备生命周期的运行时验证。

完整构建首次使用原 JVM 配置时发生堆内存不足；重试只调整本次 Gradle JVM 和 worker 数，不改应用内存配置。

Collect 1.3.2 / XM 0.0.5 出现 `Expected stack map table for method with non-linear control flow` 警告。属于供应商 classfile/加固产物兼容性问题，新增业务 keep 无法补出栈映射；未来升级 R8/JDK 前应由 SDK 方提供兼容产物并做真实 minified release 设备回归。

ThinkingAnalytics 的 `cn.thinkingdata.ta_apt.TRoute`、Kuaishou 的 `com.kwad.sdk.datacollection.KsSafetyPrivateDataController` 缺类警告仍存在，前次审计已有记录。依赖带来的 `-ignorewarnings` 会降低“构建成功”对依赖完整性的证明力，不能用增加业务 keep 来解决缺失的类。

## 本次实测结果

- `:app:assembleLocalRelease` 成功，实际执行 R8 与资源压缩；notification/metrics 的 local debug/release AAR 导出成功。
- 最终产物核验通过：5 份规则来源、25 个组件/Worker、5 个既有反射 DTO、71 个 Trustlook 类、558 个 Collect/XM 原名类、9 个 XML View、测速 Fragment 恢复构造及 Alarm 默认资产。release 未包含广告调试页面。
- native-only / 原名保留类可能不出现在 mapping 中，已用 APK 根 DEX 的 `class_defs` 验证真实定义，避免误报删除。
- 独立 R8 后运行 Gson 2.10.1、2.13.2 探针均通过，包含 Alarm 流式协议与原有 141 条日池、嵌套翻译、收益和地震模型。
- 本次没有改动生产混淆规则，仅补充核验工具及记录。

复现命令：

```sh
./gradlew :app:assembleLocalRelease :notification:assembleLocalRelease :notification:assembleLocalDebug :metrics:assembleLocalRelease :metrics:assembleLocalDebug --max-workers=2 --no-parallel -Dorg.gradle.jvmargs='-Xmx6144m -Dfile.encoding=UTF-8'
python3 tools/r8/verify_outputs.py --channels local
JAVA_HOME=$(/usr/libexec/java_home -v 17) python3 tools/r8/verify_models.py --gson 2.10.1
JAVA_HOME=$(/usr/libexec/java_home -v 17) python3 tools/r8/verify_models.py --gson 2.13.2
```
