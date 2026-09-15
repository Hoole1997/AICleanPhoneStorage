package com.example.aicleanphonestorage.feature.home.data

/** 每个入口都有明确的数据含义，未知、未扫描、未授权不能伪装成 0B。 */
sealed interface HomeToolMetric {
    data object Reading : HomeToolMetric

    data object NotScanned : HomeToolMetric

    data object AccessRequired : HomeToolMetric

    data object Unavailable : HomeToolMetric

    data class Bytes(val value: Long, val partial: Boolean = false) : HomeToolMetric {
        init {
            require(value >= 0)
        }
    }

    data class AppCount(val value: Int, val selected: Boolean = false) : HomeToolMetric {
        init {
            require(value >= 0)
        }
    }
}

data class HomeToolMetrics(
    val network: HomeToolMetric = HomeToolMetric.Reading,
    val notifications: HomeToolMetric = HomeToolMetric.Reading,
    val apps: HomeToolMetric = HomeToolMetric.Reading,
    val compress: HomeToolMetric = HomeToolMetric.NotScanned,
    val largeFiles: HomeToolMetric = HomeToolMetric.NotScanned,
    val unusedFiles: HomeToolMetric = HomeToolMetric.NotScanned,
    val screenshots: HomeToolMetric = HomeToolMetric.NotScanned,
)

/** 数量模式使用共享结果；已授权时的容量模式保持原样，未知数值不填成 0。 */
internal fun HomeToolMetric.withSharedAppCount(count: Int?): HomeToolMetric =
    if (this is HomeToolMetric.AppCount) count?.let { copy(value = it) } ?: HomeToolMetric.Unavailable
    else this
