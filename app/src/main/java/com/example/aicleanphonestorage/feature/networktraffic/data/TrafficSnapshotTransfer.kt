package com.example.aicleanphonestorage.feature.networktraffic.data

/** 首页查询与结果页之间的一次性交接。只缓存一份小型摘要，Intent 不携带应用列表/位图。 */
class TrafficSnapshotTransfer(private val now: () -> Long = System::currentTimeMillis) {
    private data class Entry(val token: Long, val createdAt: Long, val snapshot: TrafficSnapshot)
    private var entry: Entry? = null
    private var sequence = 0L
    @Synchronized fun put(snapshot: TrafficSnapshot): Long {
        val token = ++sequence
        entry = Entry(token, now(), snapshot)
        return token
    }
    @Synchronized fun take(token: Long): TrafficSnapshot? {
        val current = entry ?: return null
        if (now() - current.createdAt !in 0..60_000) { entry = null; return null }
        if (current.token != token) return null
        entry = null
        return current.snapshot
    }
}
