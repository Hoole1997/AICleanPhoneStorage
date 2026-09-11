package com.example.aicleanphonestorage.app.ad

import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

/** 功能页仅携带来源回首页；广告由首页恢复并绘制后展示。内部分类返回父页不触发首页广告。 */
internal class FeatureExitCoordinator(
    private val activity: AppCompatActivity,
    private val position: () -> String?,
    private val isBusy: () -> Boolean = { false },
    private val returnsToParent: () -> Boolean = { false },
) {
    init {
        activity.onBackPressedDispatcher.addCallback(activity) { exit() }
    }

    fun exit() {
        if (isBusy() || activity.isFinishing) return
        if (!returnsToParent()) activity.startActivity(HomeExitAdContract.intent(activity, position()))
        activity.finish()
    }
}
