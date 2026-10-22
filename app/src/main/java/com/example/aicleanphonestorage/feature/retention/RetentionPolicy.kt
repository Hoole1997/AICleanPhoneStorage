package com.example.aicleanphonestorage.feature.retention

import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.utils.ConfigRemoteManager

/** 默认 paid=true/organic=false；自然和未确认归因始终不启用，不把本地默认 paid 当成买量事实。 */
internal object RetentionPolicy {
    const val KEY = "uninstall_retention_config"
    fun enabled(raw: String?, confirmedPaid: Boolean): Boolean {
        if (!confirmedPaid) return false
        return try {
            JsonReader(StringReader(raw.orEmpty())).use { reader ->
                reader.strictness = Strictness.STRICT
                val root = JsonParser.parseReader(reader).asJsonObject
                check(reader.peek() == JsonToken.END_DOCUMENT)
                val value = root.getAsJsonObject("paid")?.get("enabled")
                if (value?.isJsonPrimitive == true && value.asJsonPrimitive.isBoolean) value.asBoolean else true
            }
        } catch (_: Exception) { true }
    }

    suspend fun read(): Boolean = withContext(Dispatchers.IO) {
        val confirmed = try { ChannelUserController.isChannelSetOnce() && ChannelUserController.isPaidChannel() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        if (!confirmed) return@withContext false
        val raw = try {
            if (ConfigRemoteManager.isInitialized()) ConfigRemoteManager.getString(KEY, "") else null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        enabled(raw, confirmedPaid = true)
    }
}
