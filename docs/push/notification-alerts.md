# 普通通知的首次提醒与后续静音

- 首条非常驻通知使用 `general_notification_v2`，默认高重要性、系统通知铃声和震动；声明普通 `VIBRATE` 权限。
- 同一个通知 ID 的卡片仍显示时，所有更新设置 `setOnlyAlertOnce(true)`；连续重复任务另外使用静音通道及 `setSilent(true)`。用户清除/点击移除卡片后，下一条新卡片可以再次提醒。
- 常驻通知保留原静音逻辑。重复任务仍受宿主 `repeatNotificationsEnabled` 与远程重复策略共同控制；当前宿主默认关闭，本次没有修改频次或开启循环。
- FCM 系统托管通知的默认通道同步到新提醒通道；FCM data 消息仍走客户端已有的买量判断与普通通知流程。客户端的同 ID 更新策略不等于服务端独立通知 ID 的每日/永久限次。

## 旧安装与用户设置

旧 `general_notification` 通道由应用默认关闭震动；Android 不允许应用直接修改已创建通道的提醒行为，因此创建版本化提醒通道，保留旧通道而不删除重建。旧默认行为且没有可识别用户定制时，新通道启用声音和震动；已知静音、屏蔽、定制铃声/震动、锁屏可见性、角标与灯光设置被保留。已创建的新通道以后直接复用。Android 公开 API 无法区分所有仅修改震动但仍与旧默认值一致的用户选择；不会用隐藏 API 读取用户锁定字段。全局通知开关、音量及勿扰始终由系统控制，不手动播放铃声或调用振动器绕过系统。

参考：[Android 通知通道](https://developer.android.com/develop/ui/views/notifications/channels)、[OnlyAlertOnce](https://developer.android.com/reference/android/app/Notification.Builder#setOnlyAlertOnce(boolean))。

验证使用 local 渠道：编译、Lint、通知模块单测，以及设备上首条/重复/常驻策略、旧通道迁移、用户偏好、Manifest 与买量准入回归。测试检查真实通道和 Notification 属性，不以配置断言代替物理响铃/震动的测量。
