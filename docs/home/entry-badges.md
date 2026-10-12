# 首页入口角标

Figma：`dVsTL6ggoDPXVPcEKg856G`，帧 [6562:1177](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G?node-id=6562-1177)（首页-提示）。

## 样式与位置

本版本只有两种角标，均为 Figma 原版矢量导出的透明 WebP（`design/figma/home/badges/`，`tools/convert_home_badge_assets.py`）：

| 入口 | 样式 | 资源 | 位置 |
| --- | --- | --- | --- |
| Smart Clean Now | 感叹号 24dp | `ic_home_badge_alert_large`（6562:1434） | 右缘对齐按钮，顶部高出按钮 10dp |
| Malware Scan | 感叹号 20dp | `ic_home_badge_alert`（6562:1437） | 右缘对齐 Scan 胶囊，顶部高出胶囊可见顶部 10dp |
| Clear Notifications、Battery Info | 感叹号 20dp | `ic_home_badge_alert` | 距卡片顶部、尾部各 10dp |
| 其余工具卡片 | 红点（不显示数字） | `ic_home_badge_dot`（6562:1440） | 同上 |

Smart Clean 角标显示时，按钮文字、图标（原版红色 `ic_home_clean_alert`，6562:1230）与边框流光统一切为红色 `#F55D46`；角标隐藏后恢复蓝色。流光 Shader 只在颜色变化时重建，绘制循环不分配对象。

设计稿中的 SpeedTest 入口当前应用未提供，不新增功能。角标为装饰性 ImageView，不改变按钮/卡片的测量、点击区域和无障碍朗读。应用未开启全局 RTL，仅 Malware 入口在 RTL 语言下手动镜像，此时角标随按钮移到外侧左上角。

## 显示规则

显示 = 远程默认开关为开 且 当天未真正进入该功能。

- **当日已进入**：入口协调器真正打开功能页时（`startActivity` 之后，含推送经首页路由进入、Malware 结果的 APK 清理进入 Smart Clean）调用 `HomeViewModel.onFeatureEntered`。授权拒绝、取消扫描、失败均不算进入。记录写入 DataStore `home_badges`（不参与备份），杀进程重进仍保持隐藏；内存镜像让首页返回时立即生效，不等待写盘。
- **每日 0 点恢复**：日期为设备本地时区的 `epochDay`。不设定时器或轮询；`MainActivity.onResume` 调用 `refreshBadges()`，跨天后旧记录失效，全部恢复为默认状态。App 停留在前台跨过 0 点时，下一次回到首页即恢复。
- **远程默认开关**：Remote Config 参数 `home_badge_switch`，通过 `ConfigRemoteManager.getString` 读取 JSON 字符串，字段为各入口 key，值为 Boolean。本地策略为全部显示：参数未下发、字段缺失、非 Boolean、JSON 无效或超过 4096 字符均按显示处理。

```json
{
  "smart_clean": true,
  "malware_scan": true,
  "network_traffic": true,
  "clear_notifications": true,
  "app_manager": true,
  "photo_compress": true,
  "large_files": true,
  "unused_files": true,
  "screenshots": true,
  "videos": true,
  "similar_photos": true,
  "battery_info": true
}
```

只需下发要关闭的字段，例如 `{"videos":false}`。字段名同时是本地记录值，发布后不得改名。

Remote Config 已就绪时直接读取，避免先显示再隐藏的闪烁；未就绪（首装弱网、拉取失败）时先按本地策略显示，`ConfigRemoteManager` 初始化成功后再覆盖。读取只在首页可见期间进行，离开首页随订阅取消。

## 验证

- 单元测试：`HomeBadgeTest`（JSON 回落、跨天恢复、杀进程保持、远程未就绪）、`HomeContentTest`（入口与样式映射）。
- 设备离屏渲染：`HomeBadgeRenderingTest` 断言 375dp、320dp 与 2 倍字号下的角标尺寸、偏移、样式及不遮挡标题，截图输出到应用缓存 `home-badges/`。
