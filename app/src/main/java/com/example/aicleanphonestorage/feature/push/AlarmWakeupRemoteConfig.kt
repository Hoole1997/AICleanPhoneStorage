package com.example.aicleanphonestorage.feature.push

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.corekit.core.utils.ConfigRemoteManager

/** 宿主提供 Remax 的解密边界，notification 模块只接收独立配置的小型 JSON。 */
internal object AlarmWakeupRemoteConfig {
    const val KEY = "alarm_wakeup_config"
    private const val MAX_CONFIG_LENGTH = 16_384

    /**
     * null = Remax 尚未激活；空串 = 已就绪但参数缺失/不可用，均交给模块回退缓存/本地默认 JSON。
     * 先检查就绪状态，避免 getString 在冷启动内部等待 30 秒。读取和解密离开主线程。
     */
    suspend fun read(): String? {
        if (!ConfigRemoteManager.isInitialized()) return null
        return withContext(Dispatchers.IO) {
            try {
                // Remax 负责包名作用域的 key/value 解密，不直接绕过它读取 Firebase 原始参数。
                val json: String? = ConfigRemoteManager.getString(KEY, "")
                json?.takeIf { it.length <= MAX_CONFIG_LENGTH }.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AlarmWakeupConfig", "Alarm wakeup configuration unavailable", error)
                ""
            }
        }
    }
}
