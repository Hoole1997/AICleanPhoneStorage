package com.example.aicleanphonestorage.feature.home.data

import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 仅用于首页/常驻通知展示的虚拟量，与真实文件索引、删除和压缩结果隔离。单位为十分之一 MB。 */
data class HomeCleaningSnapshot(
    val paidUser: Boolean = false,
    val junkTenthsMb: Int? = null,
    val lastCompletedAt: Long? = null,
) {
    val dirty: Boolean get() = paidUser && junkTenthsMb != null

    fun junkNumber(locale: Locale): String? = if (!dirty) null else
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            isGroupingUsed = false
        }.format(requireNotNull(junkTenthsMb) / 10.0)

    fun cleanBadge(locale: Locale): String? = junkNumber(locale)?.let { "${it}MB" }
}

/** 进程内唯一数据源。没有定时器或持久化；只有首页进入/通知构建时检查三分钟边界。 */
internal class HomeCleaningState(
    initialPaidUser: Boolean,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val generate: () -> Int = { Random.nextInt(320, 680) * 10 + Random.nextInt(1, 10) },
) {
    private val current = MutableStateFlow(HomeCleaningSnapshot(paidUser = initialPaidUser))
    val state = current.asStateFlow()
    private var completionToken: String? = null

    @Synchronized fun check(): HomeCleaningSnapshot {
        val value = current.value
        if (!value.paidUser) return value
        val cleanedRecently = value.lastCompletedAt?.let { clock() - it < CLEAN_WINDOW_MS } == true
        if (cleanedRecently || value.dirty) return value
        // 同一轮未清理只生成一次；compact/expanded 通知连续构建或并发检查也复用同一数值。
        val amount = generate()
        require(amount in 3201..6799 && amount % 10 != 0)
        return value.copy(junkTenthsMb = amount).also { current.value = it }
    }

    @Synchronized fun completed(visitToken: String) {
        if (visitToken == completionToken) return
        completionToken = visitToken
        current.value = current.value.copy(lastCompletedAt = clock(), junkTenthsMb = null)
    }

    @Synchronized fun setPaidUser(paid: Boolean) {
        val value = current.value
        if (value.paidUser != paid) current.value = value.copy(paidUser = paid)
    }

    companion object { const val CLEAN_WINDOW_MS = 180_000L }
}
