package com.example.aicleanphonestorage.feature.onboarding

import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.corekit.core.utils.ConfigRemoteManager

/** 只在开屏结束后的路由边界读取一次。缺失、拉取失败和类型错误均开启，严格保留显式 false。 */
internal object NewUserFlowConfig {
    const val KEY = "new_user_flow_config"

    suspend fun enabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!ConfigRemoteManager.isInitialized()) true
            else parse(ConfigRemoteManager.getString(KEY, ""))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { true }
    }

    fun parse(raw: String?): Boolean = try {
        val reader = JsonReader(StringReader(raw.orEmpty())).apply { strictness = Strictness.STRICT }
        val value = reader.use {
            val root = JsonParser.parseReader(it).asJsonObject
            check(it.peek() == JsonToken.END_DOCUMENT)
            root.get("enabled")
        }
        if (value?.isJsonPrimitive == true && value.asJsonPrimitive.isBoolean) value.asBoolean else true
    } catch (_: Exception) { true }
}
