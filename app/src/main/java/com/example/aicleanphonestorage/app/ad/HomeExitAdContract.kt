package com.example.aicleanphonestorage.app.ad

import android.content.Context
import android.content.Intent
import com.example.aicleanphonestorage.app.MainActivity
import java.util.UUID

internal data class HomeExitAdRequest(val token: String, val placement: String)

/** placement 标记来源功能，两种退出出口共用总表 Key，首页只接收已知返回广告位。 */
internal object HomeExitAdContract {
    private const val TOKEN = "home.exit_ad.token"
    private const val PLACEMENT = "home.exit_ad.placement"
    fun intent(context: Context, placement: String?): Intent {
        require(placement == null || placement in InterstitialPlacements.homeExits)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // 无法恢复功能来源时仍正常回首页，但不请求错误的广告位。
        if (placement != null) {
            intent.putExtra(TOKEN, UUID.randomUUID().toString()).putExtra(PLACEMENT, placement)
        }
        return intent
    }

    fun take(intent: Intent): HomeExitAdRequest? {
        val token = intent.getStringExtra(TOKEN)
        val placement = intent.getStringExtra(PLACEMENT)
        intent.removeExtra(TOKEN)
        intent.removeExtra(PLACEMENT)
        if (token.isNullOrBlank() || token.length > 64 || placement !in InterstitialPlacements.homeExits) return null
        return HomeExitAdRequest(token, requireNotNull(placement))
    }
}
