# 电池信息

首页 Similar 后加入 Battery Info / Battery Health。点击复用 TaskLoadingDialogFragment 和 TimedEntryLoader，进度条始终循环且隐藏百分比（包括未知占位符），支持取消、后台取消、生命周期恢复，读取成功后打开竖屏电池信息页。无需新增权限。

## 数据与生命周期

- BatteryRepository 读取系统电池广播、BatteryManager 与亮度设置，TaskExecutor 执行 I/O；广播和 ContentObserver 只发送合并事件，无轮询。
- BatteryInfoViewModel 仅保存小型不可变快照，WhileSubscribed(0) 配合 Activity RESUMED 收集，页面暂停后释放监听。入口经 OneShotTransfer 传递初始快照，重建时重新读取系统数据。
- 电量、充电、温度、电压、技术类型及健康状态来自系统。健康状态不是电池寿命百分比。
- CHARGE_COUNTER 为剩余电荷量，µAh 转 mAh。无法可靠取得额定满容量，右侧保留“—”；不反射隐藏 API，也不使用设计稿示例值。设备没有上报的其他指标同样显示“—”。
- 手动亮度展示系统设置百分比；自动亮度展示 Auto，避免把保存的手动值当成实时面板亮度。

公开 API 依据：[BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)、[Settings.System](https://developer.android.com/reference/android/provider/Settings.System)。

## 视觉与资源

Figma 文件 dVsTL6ggoDPXVPcEKg856G：首页 6194:1503，状态页 6192:1225、6192:1308、6192:1390。

BatteryGaugeView 按原始 SVG 路径绘制外弧、电池轮廓与闪电，按实际电量填充，20% 及以下使用红色。外弧与三份设计一致，保持完整。原生 TextView 自然测量百分比，大字体移到弧线下方。没有持续动画，状态不变时不重复重绘。

图标原图、导出图及来源见 design/figma/battery/source 与 assets.json；tools/convert_battery_assets.py 在开发时转换五档密度的 WebP。内侧虚线原导出带 #F6F6F6 底色，转换时按原蓝色覆盖度恢复透明通道，保留原始虚线位置。APK 只包含缩放后的资源，不包含设计源图。

信息卡片默认两列，大字体切换单列；数值自然高度，容量数字及单位通过同一 Spannable 文本保持基线。页面只有一个内容滚动区域，按钮与系统 inset 独立布局。

## 验证

- `:app:compileLocalDebugKotlin`、`:app:assembleLocalDebug`、`:app:lintLocalDebug` 成功；`git diff --check` 通过。没有本机构建 google 渠道。
- 遵照用户要求，未编写或运行单元测试。
- API 37、Pixel_10a_Offline 模拟器实际安装 local debug，检查首页入口、通用 loading、取消后不跳转、Got it 返回首页。
- 通过模拟器系统电源控制分别验证充电 100%、放电 50% / Bad、放电 20% / Good，页面直接响应系统变化。截图是模拟器注入状态，不是实体设备电池测量。
- 320dp 宽、200% 字体实际检查顶部仪表及滚动至底部的卡片、容量单位和按钮。默认屏幕与字号状态亦已截图核验。
- crash 日志缓冲未发现崩溃。未进行实体设备功耗、内存峰值、帧率或 OEM 字体测量，因此不据此宣称性能实测通过。
- 截图见 `docs/verification/battery/`。测试后恢复模拟器电源、亮度、字号和尺寸并关闭本次启动的模拟器，未操作已连接的实体设备。

### 扫描进度展示调整

电池入口固定传入 percent=null、showPercentage=false；共用组件默认 showPercentage=true，保存/恢复该标志，其他入口行为保持不变。此轮运行 local Kotlin 编译和 Lint，未新增或运行单元测试；之前 loading.png 为调整前截图，本轮未重新截取。

### 已充电时进入页面的初始状态

充电标记改为每次 read() 优先调用 BatteryManager.isCharging()，系统服务异常时才回退电池广播的 status/plugged；缺少 plugged 保持未知。入口扫描、页面首次订阅、返回前台都走同一读取流程。前台监听同时接收 ACTION_CHARGING / ACTION_DISCHARGING，与该 API 配套更新，不增加轮询或后台常驻监听。此前仅使用广播快照；未在用户实体设备上捕获其首次快照，因此不将广播陈旧认定为已实测的唯一原因。

### 实体设备连接电源但暂停充电

在用户连接的 OPLUS / API 36 设备上复现：USB powered=true、电量80%、status=4 (NOT_CHARGING)，页面无障碍状态为“未充电”且没有闪电，系统状态栏仍有闪电。因此此次问题并非仅补读 isCharging 就能解决：图标此前错误地等同于实际充电状态。

新增 powerConnected 摘要字段读取 EXTRA_PLUGGED，和 charging 分开。闪电在连接电源或明确充电时显示；接电但暂停充电时无障碍说明为“已连接电源”，不声称电量正在增加。View 的相等性判断同时覆盖电量、充电、连接电源三项，变化时刷新。

本次修复 local assemble / Lint 已通过，未新增或运行单元测试。已在实体设备复现修复前问题，并安装修复后的 local debug；修复后首次进入页面的截图核验被用户中断，尚未完成。
