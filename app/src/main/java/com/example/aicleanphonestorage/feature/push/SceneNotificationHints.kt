package com.example.aicleanphonestorage.feature.push

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.docview.push.NotificationDestination
import io.docview.push.scene.SceneLandingData
import io.docview.push.scene.PushScene

/** 首页权限/扫描/广告等待期间只保留有界路由提示；取消入口或真正打开功能时即消费。 */
internal class SceneNotificationHints(private val saved: SavedStateHandle) : ViewModel() {
    fun replace(value: SceneLandingData?) {
        saved[SceneLandingData.CODE] = value?.code
        saved[SceneLandingData.REFERENCE] = value?.reference
        saved[SceneLandingData.TIME] = value?.occurredAt
        saved[SceneLandingData.PERCENT] = value?.batteryPercent
        saved[SceneLandingData.DURATION] = value?.chargeDurationMs
    }
    fun transfer(destination: NotificationDestination, intent: Intent) {
        val code = saved.get<Int>(SceneLandingData.CODE) ?: return
        if (PushScene.fromCode(code)?.destination != destination) return
        SceneLandingData(code, saved.get<String>(SceneLandingData.REFERENCE).orEmpty(), saved[SceneLandingData.TIME] ?: 0,
            saved[SceneLandingData.PERCENT], saved[SceneLandingData.DURATION]).write(intent)
        replace(null)
    }
}
