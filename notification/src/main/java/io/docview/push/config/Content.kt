package io.docview.push.config

import com.google.gson.annotations.SerializedName
import io.docview.push.NotificationDestination

/** 线上 JSON 保持 iconType/actionType 整数字段；编号只在此定义，业务层使用强类型目标。 */
data class Content(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("desc") val desc: String,
    @SerializedName("buttonText") val buttonText: String,
    @SerializedName("iconType") val iconType: Int,
    @SerializedName("actionType") val actionType: Int,
) {
    val destination: NotificationDestination get() = NotificationDestination.fromContentType(actionType)
    val iconDestination: NotificationDestination get() =
        NotificationDestination.findContentType(iconType) ?: destination

    companion object {
        // 当前清理 App 的业务协议；同时用于 actionType 和 iconType，不依赖 enum ordinal。
        const val TYPE_CLEAN = 1
        const val TYPE_NETWORK = 2
        const val TYPE_PHOTO_COMPRESS = 3
        const val TYPE_UNUSED_FILES = 4
        const val TYPE_SCREENSHOTS = 5
        const val TYPE_HOME = 6
        // 新业务不复用旧浏览器已废弃的动作编号，避免旧远程缓存串到新的清理入口。
        const val TYPE_LARGE_FILES = 1001
        const val TYPE_NOTIFICATION_CLEANER = 1002
        const val TYPE_APP_MANAGER = 1003
    }
}

// 本地批量文案超过 64 条；仍限制上限，避免异常远程配置常驻过大的列表。
internal const val MAX_PUSH_CONTENTS = 256

/** 不支持的动作整条过滤；未知图标回退为该动作自己的图标，避免回到任意清理图标。 */
internal fun parsePushContents(json: String): List<Content> {
    val type = object : com.google.gson.reflect.TypeToken<List<Content?>>() {}.type
    val parsed: List<Content?> = com.google.gson.Gson().fromJson(json, type)
        ?: throw com.google.gson.JsonSyntaxException("Missing push content")
    return parsed.take(MAX_PUSH_CONTENTS).mapNotNull { item ->
        item ?: return@mapNotNull null
        val destination = NotificationDestination.findContentType(item.actionType) ?: return@mapNotNull null
        if (item.id.isNullOrBlank() || item.title.isNullOrBlank() || item.desc.isNullOrBlank()) return@mapNotNull null
        item.copy(id = item.id.take(100), title = item.title.take(120), desc = item.desc.take(500),
            buttonText = item.buttonText.orEmpty().take(40), iconType = item.iconDestination.contentType,
            actionType = destination.contentType)
    }.ifEmpty { throw com.google.gson.JsonSyntaxException("No supported cleaning content") }
}
