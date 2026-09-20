package io.docview.push

import io.docview.push.config.Content

/** 内容配置、常驻入口和首页路由共用的业务表；wire 编号显式绑定，调整枚举顺序不会串入口。 */
enum class NotificationDestination(val key: String, val contentType: Int) {
    HOME("home", Content.TYPE_HOME),
    CLEAN("clean", Content.TYPE_CLEAN),
    NETWORK("network", Content.TYPE_NETWORK),
    PHOTOS("photos", Content.TYPE_PHOTO_COMPRESS),
    UNUSED_FILES("unused_files", Content.TYPE_UNUSED_FILES),
    SCREENSHOTS("screenshots", Content.TYPE_SCREENSHOTS),
    LARGE_FILES("large_files", Content.TYPE_LARGE_FILES),
    NOTIFICATION_CLEANER("notification_cleaner", Content.TYPE_NOTIFICATION_CLEANER),
    APP_MANAGER("app_manager", Content.TYPE_APP_MANAGER),
    VIDEOS("videos", Content.TYPE_VIDEOS),
    DUPLICATE_PHOTOS("duplicate_photos", Content.TYPE_DUPLICATE_PHOTOS),
    MALWARE("malware", Content.TYPE_MALWARE),
    BATTERY("battery", Content.TYPE_BATTERY);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: HOME
        fun findContentType(type: Int) = entries.firstOrNull { it.contentType == type }
        fun fromContentType(type: Int) = findContentType(type) ?: HOME
    }
}
