# 相似图片清理

需求：[飞书第 2 节](https://pic6ktmsyi.feishu.cn/wiki/T4vOwfFKviXZBYkwCgFcH6xmnAc)。
设计：[Figma 5986:996](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb?node-id=5986-996)。

## 当前行为

- 首页在 Videos 后新增 Similar，使用 Figma 原始透明图标转换的多密度 WebP。
- 按用户最新要求，Android 11+ 与视频清理共用 MANAGE_EXTERNAL_STORAGE 门槛。已授权直接进入原有扫描 loading；旧系统沿用照片存储权限。
- 权限弹框独立使用：Allow access to your photos / Duplicate Photo Cleaner needs access to your photos to find duplicate and similar images. / Allow / Not now。实际授权仍进入系统所有文件访问设置。
- 仅索引 MediaStore.Images，默认排除截图、缩略图目录及应用私有目录。解码、哈希、特征分析和分组都在本地执行，不上传图片或特征。
- 完全重复用 SHA-256 确认；视觉相似沿用本地 dHash、比例、对比度、亮度和颜色指标，并按时间/大小筛选候选。固定参考图避免相似关系无限传递。极端哈希碰撞采用有界候选比较，并披露未完整分析的数量。
- 每组至少两张；默认保护一张 Original，其余候选默认选中。推荐依次考虑分辨率、清晰度、合理文件大小、较早拍摄/修改时间与稳定顺序。
- 单选、组选择、全选共享持久索引。Original 不能勾选；取消另一张照片的选择后点击该照片，可更换 Original。点击当前 Original 直接预览，不弹说明 Toast。
- 卡片按预计可释放空间及修改时间排序。正常字号三列，大字号两列。以最多三张的小行分页，整个页面只有一个 RecyclerView，不将完整大组放入 ViewModel 或嵌套列表。
- 页面顶部不显示刷新进度条，选择过程中清理按钮不因数据库写入变灰闪烁；入口扫描仍显示共享 loading 弹框。
- 确认弹框复用原组件，展示 Selected: N Pictures、Total size、The selected photos will be deleted from your device. Please confirm.，以及 Cancel / Confirm。没有功能内确认插屏。
- 有文件管理权限时直接通过 MediaStore 删除用户确认的副本；权限撤销或 Provider 拒绝时退回系统确认。操作前检查可用性、Original 和文件状态，完全重复组还会重新校验内容哈希。
- 复用完成页，显示实际删除的 Photo / Photos 数量；Continue 重新查询并计算分组，无组时复用空布局。
- 广告位：native_feature_duplicate、native_result_duplicate、back_home_duplicate；扫描原生位继续使用 native_scanning。

## 验证

仅 local 渠道构建，没有构建、打包或安装 google 渠道。

- local 编译、Lint、194 项单元测试通过。
- 模拟器 API 37 上的 8 项设备回归通过，覆盖分组、620 张照片分页、Original 更换与保护、损坏图片、哈希变化拒绝删除、真实测试媒体直接删除、权限文案及按钮状态。
- 另在 320dp 宽、fontScale=2 的模拟器配置上完成页面、选择、重建和确认弹框测试，并恢复设备尺寸及字体设置。
- [页面](verification/similar-cleaner/similar-tests/similar-groups.png)、[确认弹框](verification/similar-cleaner/similar-tests/similar-confirm.png)、[权限弹框](verification/similar-cleaner/permission.png)、[大字号页面](verification/similar-cleaner/similar-groups-large.png)、[大字号确认](verification/similar-cleaner/similar-confirm-large.png)。截图图片为测试自行生成的素材，未作为生产示例数据。

尚未实测各 OEM 字体、全部旧 Android 版本或长期大图库耗电；内存和并发上限是设计约束，不是无 ANR/OOM 的性能保证。
