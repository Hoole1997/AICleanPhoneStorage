package com.example.aicleanphonestorage.feature.home.ui

/** UI 只输出语义动作；由页面协调器接入权限、导航和扫描，不在 View 中执行业务。 */
interface HomeUiActions {
    fun onSettings()
    fun onSmartClean()
    fun onToolSelected(tool: HomeTool)

    object None : HomeUiActions {
        override fun onSettings() = Unit
        override fun onSmartClean() = Unit
        override fun onToolSelected(tool: HomeTool) = Unit
    }
}
