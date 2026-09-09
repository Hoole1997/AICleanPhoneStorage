package com.example.aicleanphonestorage.app.ad

import android.app.Application
import android.util.Log
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.bidding.AppOpenBiddingInitializer
import com.example.aicleanphonestorage.BuildConfig
import com.example.aicleanphonestorage.R
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.utils.ConfigRemoteManager
import java.util.concurrent.atomic.AtomicBoolean

internal enum class AdInitializationState { IDLE, INITIALIZING, READY, DISABLED, FAILED, TIMED_OUT }

/** 迁入 StreetHtml 的 AppOpenBiddingInitializer 入口；仅保留 Application，广告展示仍由业务页面按需接入。 */
internal object AdSdkInitializer {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val current = MutableStateFlow(AdInitializationState.IDLE)
    val state = current.asStateFlow()

    fun initialize(application: Application) {
        if (started.getAndSet(true)) return
        AdConfiguration.install()
        ChannelUserController.setDefaultChannel(BuildConfig.DEFAULT_USER_CHANNEL)
        if (FirebaseApp.getApps(application).isNotEmpty()) ConfigRemoteManager.initialize()
        current.value = AdInitializationState.INITIALIZING
        scope.launch {
            try {
                AdAnalytics.initialize(application)
                if (!AdConfiguration.hasConfiguredAds()) {
                    current.value = AdInitializationState.DISABLED
                    return@launch
                }
                val result = withTimeoutOrNull(15_000) {
                    AppOpenBiddingInitializer.initialize(application, R.mipmap.ic_launcher) { }
                }
                current.value = when (result) {
                    is AdResult.Success -> AdInitializationState.READY
                    is AdResult.Failure -> AdInitializationState.FAILED
                    null -> AdInitializationState.TIMED_OUT
                }
                Log.i("CleanAds", "SDK initialization: ${current.value}")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                current.value = AdInitializationState.FAILED
                Log.e("CleanAds", "SDK initialization failed", error)
            }
        }
    }
}
