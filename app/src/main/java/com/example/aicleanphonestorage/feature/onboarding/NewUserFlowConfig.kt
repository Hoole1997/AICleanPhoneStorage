package com.example.aicleanphonestorage.feature.onboarding

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.corekit.core.utils.ConfigRemoteManager

/** 开屏结束后读取单个 Boolean 参数；未配置/不可用默认开启，不再接受 JSON 对象协议。 */
internal object NewUserFlowConfig {
    const val KEY = "new_user_flow_config"
    private const val DEFAULT_ENABLED = true

    // 只注入远程读取边界，默认值与取消语义仍由真实入口处理；不持有页面或启动轮询。
    suspend fun enabled(readRemote: suspend () -> Boolean? = ::remoteBoolean): Boolean = withContext(Dispatchers.IO) {
        try { readRemote() ?: DEFAULT_ENABLED }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { DEFAULT_ENABLED }
    }

    private suspend fun remoteBoolean(): Boolean? {
        if (!ConfigRemoteManager.isInitialized()) return null
        // 与首页 Boolean 开关一致：SDK 对未下发参数会返回静态 false，而非传入默认值。
        // getString 仅判断参数存在，不解析内容；真正的开关值完全由 getBoolean 读取。
        if (ConfigRemoteManager.getString(KEY, "").isNullOrEmpty()) return null
        return ConfigRemoteManager.getBoolean(KEY, DEFAULT_ENABLED)
    }
}
