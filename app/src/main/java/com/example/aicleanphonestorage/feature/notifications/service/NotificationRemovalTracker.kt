package com.example.aicleanphonestorage.feature.notifications.service

/** 只记录取消请求的 key/来源，收到系统移除回调后才计数；不读取通知正文。 */
internal class NotificationRemovalTracker(private val limit: Int = 1024) {
    private val requested = LinkedHashMap<String, String>()
    private val sources = mutableSetOf<String>()

    @Synchronized fun requested(key: String, packageName: String) {
        if (requested.size >= limit) requested.remove(requested.keys.first())
        requested[key] = packageName
    }

    @Synchronized fun removed(key: String, byListener: Boolean): Boolean {
        val source = requested.remove(key) ?: return false
        return byListener && sources.add(source)
    }

    @Synchronized fun drainCount(): Int = sources.size.also { sources.clear() }
    @Synchronized fun clear() { requested.clear(); sources.clear() }
}
