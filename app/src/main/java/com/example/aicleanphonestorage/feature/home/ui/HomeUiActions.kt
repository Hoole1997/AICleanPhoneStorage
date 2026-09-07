package com.example.aicleanphonestorage.feature.home.ui

/** UI 只输出语义动作；当前阶段统一为空操作，以后由页面协调器接入权限/导航/扫描。 */
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
