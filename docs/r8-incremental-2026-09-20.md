# 64dff000 之后的增量混淆审计

日期：2026-09-20。对比 `64dff000` → `18216b7`（包含视频、相似照片、电池、权限底部弹层、病毒扫描、新广告位、推送日池/多语言/中文日志）。

结论：需要的新增保留规则已同步，未发现需要追加的生产 keep 规则。本次补齐了旧验证工具遗漏的新日池、翻译模型与 Trustlook SDK 核验。

## 功能对应

| 新功能/变更 | 混淆边界 | 结论 |
| --- | --- | --- |
| Trustlook 病毒扫描 | 本地 AAR 未附带 consumer rules；包含 SDK/原生加载边界 | `app/src/main/keepRules/trustlook.keep` 已保留 `com.trustlook.**`；最终 mapping 中 71 个 SDK 类全部存在且名称不变 |
| 普通推送翻译 | Gson 反射解析 `Content.translations: Map<String, ContentTranslation>` | `notification/consumer-rules.pro` 已新增 `ContentTranslation` 类可实例化性及三个 `SerializedName` 字段规则；原 Content 字段规则覆盖 translations，原 Signature/TypeToken 契约继续生效 |
| D1–D5 与 D6+、中文诊断 | JsonParser 树解析、显式构造、普通 Kotlin 调用 | 不新增 DTO 反射；不需要保留日池/轮播/日志类名；日志异常 simpleName 允许变化 |
| 电池页面及 BatteryGaugeView | Activity Manifest 入口、XML 自定义 View 构造 | Manifest/AAPT 自动规则及已有 Activity 类名契约覆盖；实际 AAPT 文件包含 BatteryGaugeView 的 `(Context, AttributeSet)` 构造 |
| 权限底部弹层、病毒声明弹窗 | DialogFragment 恢复、ViewBinding、显式 ViewModel 工厂 | 已有 AndroidX consumer rules/静态调用覆盖；无需整包保留业务 UI |
| 视频、相似照片、APK 风险交接 | MediaStore/SQLite、显式列名、会话 ID、枚举 name/valueOf | 未新增业务 JSON 反射模型或自定义 JNI；无需扩展 keep |
| 新广告位/原生容器/阶段切换 | 显式 slot 字符串、现有 SDK 接口、原生 View 属性 | 广告依赖和原有规则覆盖；系统 alpha/rotation 属性不属于需保留的业务 setter |

`ContentTranslation` 在本次完整 Release mapping 中改名为 `rx0`，三个字段也被改名；这是规则允许的正常混淆，JSON wire key 由 `SerializedName` 保持。没有以禁止混淆整个通知模块代替字段契约。

## 验证结果

- `:app:assembleLocalRelease` 成功，实际执行 `minifyLocalReleaseWithR8`。
- notification/metrics 的 local debug、local release 四份 AAR consumer rules 导出校验通过。
- 最终 localRelease 校验：4 份项目规则来源、23 个组件/Worker、5 个当前入口 DTO、71 个 Trustlook 类、8 个 XML View 均符合检查项。
- 独立 R8 classfile 混淆后运行回归：Gson 2.10.1、2.13.2 均通过。读取五份真实本地 JSON，共 141 条、每条七组翻译，验证泛型 Map 元素及 title/desc/buttonText 的序列化键；同时保留原配置/收益/地震模型回归。
- 静态文件清单已更新至 1,335 个模块文件；自动命中清单不替代上述语义核查。

本次没有编译、打包或安装 google 渠道，也没有安装本次 unsigned local Release。完整设备上的 SDK 实际网络/扫描/广告行为不属于这次 R8 回归的证明范围。

## 验证工具同步

- `tools/r8/ModelProbe.java` 和 `verify_models.py`：加入生产日池解析函数及 `Map<String, ContentTranslation>`，避免只验证旧英文数组。
- `tools/r8/verify_outputs.py`：默认只核验 local，支持显式 `--channels local google` 读取 CI 产物；动态收集所有 `.keep`，验证 Trustlook 和新翻译 DTO，不再硬编码三份规则/四个 DTO。
- `docs/r8-file-inventory.md`：重新生成新功能清单。

```sh
./gradlew :app:assembleLocalRelease --max-workers=2 -Dorg.gradle.jvmargs='-Xmx4096m -Dfile.encoding=UTF-8'
./gradlew :notification:assembleLocalRelease :notification:assembleLocalDebug :metrics:assembleLocalRelease :metrics:assembleLocalDebug
python3 tools/r8/verify_models.py --gson 2.10.1
python3 tools/r8/verify_models.py --gson 2.13.2
python3 tools/r8/verify_outputs.py --channels local
```

模型脚本需要 `JAVA_HOME` 指向 JDK，以及已解析的本地 Gradle 依赖缓存。

## 已有 SDK 警告

与 `64dff000` 中的原审计记录一致，本次仍出现 ThinkingAnalytics 的 `cn.thinkingdata.ta_apt.TRoute`、Kuaishou 的 `com.kwad.sdk.datacollection.KsSafetyPrivateDataController` 缺类警告，以及 Kwai Companion 元数据警告。Pangle 自带 `-ignorewarnings`，因此构建成功不代表缺失依赖问题已经消失。

这些是既有依赖问题；新增业务 keep 不能补出缺失的 SDK 类。本次没有添加 `dontwarn`、`ignorewarnings` 或全应用 keep 去屏蔽它们。
