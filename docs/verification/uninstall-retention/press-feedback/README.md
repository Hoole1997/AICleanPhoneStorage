# 卸载原因选项的系统按压阴影范围

2026-10-22。用户澄清反馈为选项的系统按压阴影/波纹显示错位，不是输入框光标。

原因选项继承 AppCompat RadioButton 样式，但已将系统 button/buttonCompat 设为 null，改用 drawableEnd 的原版勾选资源；原样式仍带系统无边界波纹。新增显式带 mask 的原生 RippleDrawable，并仅应用于 Retention.Reason，波纹裁切到当前选项行的圆角矩形内，颜色沿用主题 colorControlHighlight。没有增加 View/Activity 事件处理或自定义绘制。

输入框、焦点处理、页面切换、选中逻辑、业务埋点及原图资源均保持原样。最初基于“输入光标”理解尝试的焦点改动及对应测试已经全部撤回。

验证：

- 修复前设备测试在“必须具有显式波纹遮罩”的断言失败，见 before.txt；修复后同一测试通过。
- Android 37 模拟器注入真实触摸 Down/Up，验证选中生效、六个选项均有 mask、背景 bounds 与选项行一致；保存按压阶段截图 option-pressed.png。
- 现有 16 语言 × 1x/2x 字体 × 两页的原生布局检查通过；2 项设备测试结果见 after.txt。
- localDebug app 编译、测试 APK 构建、374 项单元测试通过，Lint 0 Error，git diff --check 通过。
- 仅使用模拟器，未操作连接的实体设备；未提交/推送，保留用户未提交的 local 配置修改。未重新导出 Downloads 中之前交付的 APK。
