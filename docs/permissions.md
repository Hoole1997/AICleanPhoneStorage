# 统一权限入口

权限说明使用 `PermissionDialogFragment`：白色圆角卡片、现有蓝色按钮和 Figma 原版 WebP 图标。覆盖使用情况访问、通知监听、所有文件、照片、旧版手机状态及目录授权。普通运行时权限、目录选择器和 MediaStore 删除确认仍由 Android 提供，应用不能替换真正的系统授权界面。

## 职责与接入

- `PermissionAccess` 负责真实授权检查和目录访问持久化；Android 实现在受限 IO 调度器执行，不持有 Activity。
- `PermissionFlowViewModel` 保存申请标识、业务路由及流程状态，负责取消、限时检测、系统回调隔离、配置变更恢复和单次结果消费。
- `PermissionSettingsNavigator` 封装公开系统路由及回到本应用的 Intent。优先进入本应用的授权详情，设备不支持时回退至对应列表。
- `PermissionCoordinator` 随申请 Activity 生存，统一注册 Activity Result、展示说明、发起授权和分发结果。业务协调器通过 `register(route, before, result)` 接入，不各自启动设置页或轮询。

目前网络流量、通知清理和全部文件清理入口共用 MainActivity 的协调器。列表页面权限失效时回到入口；成功后在申请页 RESUMED 时继续原流程。应用管理的应用详情跳转属于管理功能，不属于权限申请。

## 设置页返回与生命周期

Android 没有 `launcherTask` 启动模式，本项目申请页配置为 `singleTask`。打开本次设置页添加 `NO_HISTORY | EXCLUDE_FROM_RECENTS`；返回申请页使用 `NEW_TASK | CLEAR_TOP | SINGLE_TOP`。不使用 `CLEAR_TASK`，不枚举或结束其他应用任务。系统/OEM 可以自行转发到其他任务，因此不能保证移除设置应用的所有内部页面。

只有用户确认打开授权设置之后才检测：前 15 秒间隔 750ms，之后 1500ms，总时限 120 秒。检测期间允许协程跨越 onStop；授权成功、用户返回、取消、超时或 ViewModel 销毁后结束，不使用 Service、WakeLock、全局协程或保活权限。运行时授权和目录选择依赖 Activity Result，不循环检测。

检测到授权且申请页仍在后台时，最多尝试回到申请页一次。后台启动限制可能由 Android 或 OEM 拦截；此时保留结果，用户手动返回后继续。不能把 `startActivity` 未抛异常视为成功回到前台。配置变更不重复拉起；请求标识用于隔离已取消请求的晚到回调。

## 验证

`PermissionFlowViewModelTest` 覆盖授权后停止、超时、取消、旧回调隔离、目录结果、状态恢复和单次消费。`PermissionUiDeviceTest` 覆盖各类弹框正常/双倍字号测量、目录替代选择、Manifest 与 Intent 标记，不自动授予或撤销设备权限。

真实授权开关、不同 OEM 的直达详情支持和后台自动返回仍需人工验证：从首页分别触发授权，打开开关，观察是否返回并继续；再验证拒绝、返回、超时后返回及旋转屏幕。系统拦截自动返回时，手动返回也应只续接一次。
