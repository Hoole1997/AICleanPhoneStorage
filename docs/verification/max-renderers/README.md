# MAX 资源迁移验证

- 仅 local 构建：`assembleLocalDebug`、`testLocalDebugUnitTest`、`lintLocalDebug`、`assembleLocalDebugAndroidTest` 通过；未在本机构建 google。
- 260 项单元测试通过，0 failures/errors/skips。最终 Lint 0 errors，211 warnings；全屏黑色背景保留来源样式，存在 Overdraw 提示。
- OPPO CPH2723 / Android 16：`MaxNativeRenderingDeviceTest` 最终 3 项全部通过，输出见 [device-tests.txt](device-tests.txt)。
- 使用实际 MAX SDK View、Binder 及公开资产 getter，验证普通/大卡样式独立选择、标题/正文/图标/CTA/隐私选项绑定，全屏另验证媒体。
- 320dp 宽，1x/2x 字号；额外覆盖 320×600dp、2x 字号全屏。检查文字自然测量、CTA 至少 48dp、资产边界、媒体不盖正文/CTA、隐私选项不盖 CTA/全屏标题。
- 全屏补标签后重新通过上述 3 项真机用例。新增检查实际 SDK AdLabelView 类型、可见/非零尺寸、无障碍描述、非点击与未注册 MAX 点击资产，以及与图标、标题、正文、隐私图标、媒体、CTA 均无重叠。最新全屏 3 张渲染图已查看，左上广告标识清晰保留，右上隐私选项独立。
- 7 张原生视图渲染 PNG 位于 [screenshots](screenshots)。它们在真机测量布局后，直接绘制实际 SDK View；样例只存在于测试代码，不请求真实广告、不调用广告 render/ad 点击或曝光上报。已查看全部变体，不能据此确认广告平台填充或媒体播放。
- 来源普通卡的运行时示例已移到 tools:text；小按钮改为自然测量及合规触控高度。真机像素检查发现原隐私选项与 CTA/标题冲突，已分别预留独立空间，最终断言及截图通过。
- 单行 Button 的内部 scrollX 不能当成物理边界偏移；测试用 getDrawingRect 后再转换祖先坐标。截图直接取 SDK View 像素，避免系统窗口截图取到 About 测试宿主旧帧。
- 源资源及 Renderer 对应见 [迁移说明](../../ads/max-renderers.md)。未修改来源项目，未自动提交或推送当前迁移。
