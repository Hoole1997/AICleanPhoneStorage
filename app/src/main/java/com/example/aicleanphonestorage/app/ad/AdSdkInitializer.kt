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

internal enum class AdInitializationState { IDLE, INITIALIZING, READY, FAILED, CANCELLED }

/** 迁入 StreetHtml 的 AppOpenBiddingInitializer 入口；仅保留 Application，广告展示仍由业务页面按需接入。 */
internal object AdSdkInitializer {
    private const val TAG = "CleanAds"
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val current = MutableStateFlow(AdInitializationState.IDLE)
    val state = current.asStateFlow()

    fun initialize(application: Application) {
        if (started.getAndSet(true)) {
            Log.i(TAG, "SDK initialization: already started, state=${current.value}")
            return
        }
        current.value = AdInitializationState.INITIALIZING
        Log.i(TAG, "SDK initialization: STARTED (${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE})")
        scope.launch {
            try {
                ChannelUserController.setDefaultChannel(BuildConfig.DEFAULT_USER_CHANNEL)
                if (FirebaseApp.getApps(application).isNotEmpty()) ConfigRemoteManager.initialize()
                Log.i(TAG, "SDK initialization: invoking AppOpenBiddingInitializer")
                // 与来源项目一致，在 SDK 的配置回调中安装默认值。SDK 自行拉取云端 ID 并管理请求时限；
                // 启动页离线退出不取消应用级 SDK 初始化，google 本地 ID 为空也必须调用。
                val result = AppOpenBiddingInitializer.initialize(application, R.mipmap.ic_launcher) {
                    AdConfiguration.install()
                    Log.i(TAG, "SDK initialization: local configuration applied")
                }
                current.value = when (result) {
                    is AdResult.Success -> AdInitializationState.READY
                    is AdResult.Failure -> AdInitializationState.FAILED
                }
                Log.i(TAG, "SDK initialization: ${current.value}")
            } catch (error: CancellationException) {
                current.value = AdInitializationState.CANCELLED
                Log.w(TAG, "SDK initialization: CANCELLED", error)
                throw error
            } catch (error: Exception) {
                current.value = AdInitializationState.FAILED
                Log.e(TAG, "SDK initialization: FAILED", error)
            }
        }
        // 统计初始化独立运行，ThinkingData 的 I/O 或失败不能阻止广告 SDK 入口执行。
        scope.launch {
            try {
                Log.i(TAG, "Analytics initialization: STARTED")
                AdAnalytics.initialize(application)
                Log.i(TAG, "Analytics initialization: READY")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Analytics initialization: FAILED", error)
            }
        }
    }
}
