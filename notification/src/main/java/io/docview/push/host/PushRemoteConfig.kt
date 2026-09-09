package io.docview.push.host

import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import io.docview.push.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 保留来源 pushConfigJson/pushContentJson 协议，只有真实渠道启动一次网络获取。 */
internal object PushRemoteConfig {
    private val available get() = BuildConfig.REMOTE_PUSH_ENABLED && FirebaseApp.getApps(PushEnvironment.context).isNotEmpty()
    suspend fun initialize() {
        if (!available) return
        val remote = FirebaseRemoteConfig.getInstance()
        suspendCancellableCoroutine { continuation ->
            remote.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder()
                .setFetchTimeoutInSeconds(10).setMinimumFetchIntervalInSeconds(43_200).build())
                .continueWithTask { remote.fetchAndActivate() }
                .addOnCompleteListener { if (continuation.isActive) continuation.resume(Unit) }
        }
    }
    fun getString(key: String, default: String): String = if (!available) default else
        FirebaseRemoteConfig.getInstance().getString(key).takeIf { it.isNotEmpty() && it.length <= 65_536 } ?: default
    fun getLong(key: String, default: Long): Long = if (!available) default else
        FirebaseRemoteConfig.getInstance().getValue(key).let {
            if (it.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) default else it.asLong()
        }
}
