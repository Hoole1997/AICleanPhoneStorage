package com.example.aicleanphonestorage.app.ad

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** 仅保存小型来源标记；先消费再请求，旋转、返回广告或恢复旧 Intent 都不会重放同一退出广告。 */
internal class HomeExitAdState(private val saved: SavedStateHandle) : ViewModel() {
    val pending: HomeExitAdRequest?
        get() {
            val token = saved.get<String>(TOKEN) ?: return null
            // 升级或状态恢复时丢弃旧版/未知 Key，避免旧待展示状态继续上报已废弃广告位。
            val placement = saved.get<String>(PLACEMENT)
                ?.takeIf { it in InterstitialPlacements.homeExits } ?: return null
            return HomeExitAdRequest(token, placement)
        }

    fun accept(request: HomeExitAdRequest) {
        if (request.placement !in InterstitialPlacements.homeExits ||
            request.token == saved.get<String>(CONSUMED)) return
        saved[TOKEN] = request.token
        saved[PLACEMENT] = request.placement
    }

    fun consume(): HomeExitAdRequest? {
        val request = pending ?: return null
        saved[CONSUMED] = request.token
        saved.remove<String>(TOKEN)
        saved.remove<String>(PLACEMENT)
        return request
    }

    private companion object {
        const val TOKEN = "exit_ad.pending.token"
        const val PLACEMENT = "exit_ad.pending.placement"
        const val CONSUMED = "exit_ad.consumed.token"
    }
}
