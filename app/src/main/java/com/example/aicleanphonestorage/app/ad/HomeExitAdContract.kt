package com.example.aicleanphonestorage.app.ad

import android.content.Context
import android.content.Intent
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import java.util.UUID

internal data class HomeExitAdRequest(val token: String, val placement: String)

/** placement 同时标记来源功能和 feature/complete 退出场景，首页不接收任意广告位。 */
internal object HomeExitAdContract {
    private const val TOKEN = "home.exit_ad.token"
    private const val PLACEMENT = "home.exit_ad.placement"
    private val placements = buildSet {
        (CleanupFeature.entries + listOf(null)).forEach {
            add(InterstitialPlacements.exit(it))
            add(InterstitialPlacements.completionExit(it))
        }
        add(InterstitialPlacements.NOTIFICATIONS_EXIT)
        add(InterstitialPlacements.NOTIFICATIONS_COMPLETE_EXIT)
        add(InterstitialPlacements.APPS_EXIT)
        add(InterstitialPlacements.NETWORK_EXIT)
    }

    fun intent(context: Context, placement: String): Intent {
        require(placement in placements)
        return Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(TOKEN, UUID.randomUUID().toString())
            .putExtra(PLACEMENT, placement)
    }

    fun take(intent: Intent): HomeExitAdRequest? {
        val token = intent.getStringExtra(TOKEN)
        val placement = intent.getStringExtra(PLACEMENT)
        intent.removeExtra(TOKEN)
        intent.removeExtra(PLACEMENT)
        if (token.isNullOrBlank() || token.length > 64 || placement !in placements) return null
        return HomeExitAdRequest(token, requireNotNull(placement))
    }
}
