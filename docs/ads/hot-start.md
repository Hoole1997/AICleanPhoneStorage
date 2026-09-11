# 热启动开屏

热启动沿用 `splash` Key。与冷启动/通知入口不同，广告结束后仅关闭启动 Activity，由 Android 恢复下层原 Activity，不启动首页、不重建业务页面、不清理任务栈。该返回规则以用户本次明确要求为准。

## 进入条件

- Application 注册 `HotStartAdCoordinator`，使用 [AndroidX ProcessLifecycleOwner](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner) 的 ON_STOP / ON_START 判定进程前后台切换。它的延迟分发会过滤普通 Activity 切换和配置重建；首次前台是冷启动，不触发。
- 返回的是本应用业务 Activity，页面仍处于 RESUMED，且没有结束/销毁。通知已经进入 StartupActivity、SDK 自有 Activity 返回时，不再额外打开热启动页。
- SDK 初始化状态为 READY，且 `splash` 开关允许；`isAdSlotEnabled()` 当前仍按用户要求固定返回 true。
- 单独调用 `getTotalFrequencyStatus(APP_OPEN)`，再遍历 `AdPlatform.entries`（当前 ADMOB、GAM、TOPON、PANGLE），逐个调用 `getPlatformFrequencyStatus(APP_OPEN, platform)`。总控 `canShow()` 且任意一个平台 `canBid()` 就允许进入；总控拒绝或没有平台通过时跳过。单个平台查询异常只跳过该平台。没有使用汇总 `getFrequencyStatus()` API。

限频查询在 Default 调度器执行，不在页面内等待初始化，不轮询或自动补弹。每次回前台只消费一次资格；等待期间页面暂停、再次退后台或跳到其他页面，会取消检查。查询完成后再次校验当前页面和交接状态，满足条件才打开启动页。进入后的实际广告请求仍由现有 `loadSplash` 与 SDK 管理填充、竞价和展示规则。

## 返回与去重

启动页新增可保存恢复的 `hotStart` 模式，内部 Intent 不携带 NEW_TASK / CLEAR_TOP。热启动跳过重复推送权限申请，沿用循环进度、SDK 回调放行和最短 3 秒停留，结束后淡出返回原页面。

通知到达正在展示的热启动页时，通知目的地优先，切换为正常的“启动页 → 首页 → 对应业务”路径。旋转、状态恢复保留热启动模式；不会把热启动出口误判为首页出口。

`ForegroundTransitionGuard` 只记录主线程上的交接数量，不持有 Activity。共享权限协调器打开系统设置/运行时权限/目录选择器时，以及全屏插屏/开屏请求期间持有交接标记，返回/回调/销毁时释放。前后台监听在离开 Activity 时保存资格，避免标记先释放、系统页随后返回而误触发热启动。

Application 层仅弱引用当前 Activity；异步任务取消时不继续拉起页面。没有后台服务、持续计时器或保活申请。

## 验证

localDebug / googleDebug Kotlin 编译通过，132 项单元测试通过，localDebug Lint 0 错误、139 条警告。16 项相关模拟器测试通过（含 4 项热启动测试）。测试包括真实 ProcessLifecycleOwner 前后台事件、旋转过滤、限频拒绝、异步检查取消、交接标记提前释放后的返回过滤，以及热启动广告回调后恢复同一个原 Activity、无首页创建。冷启动广告协调器、通知导航和清理广告时机同时回归。

模拟器使用注入的限频结果和假广告回调。SDK APP_OPEN 总控与逐平台查询接入由实际依赖编译验证，真实云端限频/广告填充仍需测试渠道联调。启动页与返回原页面截图用于 UI 核验，未改动原有图标和布局资源。

## 日志排查

统一 Tag：`HotStartAds`（INFO/WARN）。Android Studio Logcat 过滤 `tag:HotStartAds`，或使用 `adb logcat -s HotStartAds`。

按顺序查看 `process_background` → `process_foreground` → `activity_resumed` → `entry_check_begin` → `frequency_decision` → `startup_open_requested` → `startup_created` → `splash_request` → `splash_callback` → `startup_finished`。`cycle` 标识前台轮次，限频明细的 `check` 标识一轮总控与逐平台查询。

- `entry_skipped reason=no_hot_start_candidate`：本轮没有热启动资格；结合前后台事件、`hotCandidate`、`eligibleDeparture` 查看是否发生真正退后台或离开时已处于授权/广告交接。
- `externalFlow`：交接标记来源及数量，如 `permission:USAGE:1`、`interstitial:back_home_junk:1`。
- `frequency_decision reason=sdk_not_ready` / `slot_disabled` / `total_frequency_blocked` / `no_platform_allowed` / `total_status_unavailable`：分别表示初始化未就绪、广告位开关关闭、总控拒绝、没有平台可竞价、总控查询异常。
- `scope=TOTAL` 及 ADMOB/GAM/TOPON/PANGLE 每项打印 `isAllowed`、`canShow`、`canBid`、`blockReasonKey`，当日展示/点击计数、上限、剩余额度，以及上次展示间隔、最小间隔和剩余等待秒数。
- `remainingIntervalSeconds` 是对应限制还需等待的秒数；`maxRemainingIntervalSeconds` 只汇总间隔等待，不代表等完后展示/点击日上限或配置阻断也会解除。`lastShowIntervalSeconds=-1` 表示没有上次展示记录。
- `bidAllowedPlatforms` 是逐平台 canBid 通过的列表；`allowedPlatforms` 再叠加总控结果，`blockedPlatforms` 为其余平台。总控拒绝时有效列表为空；GAM 被限制而其他平台允许时仍可进入。`maxRemainingIntervalSeconds` 仅为诊断值，不用作进入条件，不要求等所有平台间隔结束。
- `entry_check_cancelled`、`activity_changed`、`activity_not_resumed` 等表示异步检查过程中页面状态变化；`startup_open_dispatched` 只表示 startActivity 已调用，实际到达须看到 `startup_created`。

诊断仅在事件/一次检查时输出，没有轮询；SDK 未 READY 时也进行总控和逐平台只读查询用于排查，但进入条件保持不变。日志不记录广告 ID、通知正文或任意 Intent 内容。此次日志改动经双渠道编译、132 项单元测试和 localDebug Lint（0 错误、139 条警告）验证；未用本机结果推断测试设备无法触发的原因。

最新条件回归：任意单个平台通过可进入、GAM 被限但其他平台通过可进入、总控拒绝/全部平台拒绝仍拦截。此次条件修改已通过 local/google Kotlin 编译、134 项单元测试及 localDebug Lint。
