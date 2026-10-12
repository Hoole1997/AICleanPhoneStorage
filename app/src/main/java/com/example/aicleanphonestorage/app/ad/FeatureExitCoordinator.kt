package com.example.aicleanphonestorage.app.ad

import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

/** 功能页仅携带来源回首页；广告由首页恢复并绘制后展示。内部分类返回父页不触发首页广告。 */
internal class FeatureExitCoordinator(
    private val activity: AppCompatActivity,
    private val position: () -> String?,
    private val isBusy: () -> Boolean = { false },
    private val returnsToParent: () -> Boolean = { false },
    private val onExit: () -> Unit = {},
) {
    init {
        activity.onBackPressedDispatcher.addCallback(activity) { exit() }
    }

    fun exit() {
        if (isBusy() || activity.isFinishing) return
        // 顶部/系统返回共用实际离开回调；广告忙碌或重复返回不计为一次用户离开。
        onExit()
        if (!returnsToParent()) activity.startActivity(HomeExitAdContract.intent(activity, position()))
        activity.finish()
    }
}
