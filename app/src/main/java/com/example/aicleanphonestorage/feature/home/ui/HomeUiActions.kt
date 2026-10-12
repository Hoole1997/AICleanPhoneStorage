package com.example.aicleanphonestorage.feature.home.ui

/** UI 只输出语义动作；由页面协调器接入权限、导航和扫描，不在 View 中执行业务。 */
interface HomeUiActions {
    fun onSettings()
    fun onMalwareScan() {}
    fun onSmartClean()
    fun onToolSelected(tool: HomeTool)

    // 首页点击携带最后一次绑定到卡片的角标快照；异步列表 Diff 和进入后隐藏角标不能改写这次点击。
    // 原动作仍供通知入口和业务协调器使用，它们没有首页上真实显示的角标。
    fun onMalwareScan(homeBadgeVisible: Boolean) = onMalwareScan()
    fun onSmartClean(homeBadgeVisible: Boolean) = onSmartClean()
    fun onToolSelected(tool: HomeTool, homeBadgeVisible: Boolean) = onToolSelected(tool)

    object None : HomeUiActions {
        override fun onSettings() = Unit
        override fun onSmartClean() = Unit
        override fun onToolSelected(tool: HomeTool) = Unit
    }
}
