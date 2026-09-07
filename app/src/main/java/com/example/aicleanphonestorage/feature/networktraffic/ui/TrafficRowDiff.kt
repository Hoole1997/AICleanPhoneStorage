package com.example.aicleanphonestorage.feature.networktraffic.ui

import androidx.recyclerview.widget.DiffUtil

/** Payload 描述实际展示字段的变化；总量变化不必重新加载每个应用的名称和图标。 */
internal object TrafficRowDiff : DiffUtil.ItemCallback<TrafficRow>() {
    const val IDENTITY = 1
    const val BYTES = 2
    const val SHARE = 4
    const val PERIOD = 8
    const val MOBILE = 16
    const val WIFI = 32
    const val ALL = 63

    override fun areItemsTheSame(oldItem: TrafficRow, newItem: TrafficRow) = oldItem.key == newItem.key
    override fun areContentsTheSame(oldItem: TrafficRow, newItem: TrafficRow) = changes(oldItem, newItem) == 0
    override fun getChangePayload(oldItem: TrafficRow, newItem: TrafficRow): Any = changes(oldItem, newItem)

    fun changes(old: TrafficRow, new: TrafficRow): Int = when {
        old is TrafficRow.App && new is TrafficRow.App -> {
            (if (old.app.uid != new.app.uid || old.app.packages != new.app.packages) IDENTITY else 0) or
                (if (old.app.bytes != new.app.bytes) BYTES else 0) or
                (if (share(old) != share(new)) SHARE else 0)
        }
        old is TrafficRow.Header && new is TrafficRow.Header -> {
            (if (old.period != new.period) PERIOD else 0) or
                (if (old.mobile != new.mobile) MOBILE else 0) or
                (if (old.wifi != new.wifi) WIFI else 0)
        }
        old == new -> 0
        else -> ALL
    }
    fun share(row: TrafficRow.App): Int = if (row.total > 0)
        (row.app.bytes.toDouble() / row.total * 10000).toInt().coerceIn(0, 10000) else 0
}
