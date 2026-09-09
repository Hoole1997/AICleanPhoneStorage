package com.example.aicleanphonestorage.feature.home.data

/** 首页只持有常量大小的摘要；文件、图片及扫描明细以后由分页数据源管理。null 表示尚未知。 */
data class HomeOverview(
    val storage: StorageSummary? = null,
    val scan: ScanSummary = ScanSummary.NotScanned,
    val tools: HomeToolMetrics = HomeToolMetrics(),
    /** 与 Network Traffic 默认本月视图相同的 Wi-Fi 收发总量；无访问权/不可用时为 null。 */
    val wifiBytes: Long? = null,
)

data class StorageSummary(val totalBytes: Long, val usedBytes: Long) {
    init {
        require(totalBytes > 0)
        require(usedBytes in 0..totalBytes)
    }

    val availableBytes: Long
        get() = totalBytes - usedBytes

    val usedFraction: Double
        get() = usedBytes.toDouble() / totalBytes
}

/** 不能用 junkBytes == 0 判断是否扫描过：扫描完成且没有垃圾是有效结果。 */
sealed interface ScanSummary {
    data object NotScanned : ScanSummary

    data class Completed(val junkBytes: Long, val completedAtEpochMillis: Long) : ScanSummary {
        init {
            require(junkBytes >= 0)
            require(completedAtEpochMillis > 0)
        }
    }
}
