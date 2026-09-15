package com.example.aicleanphonestorage.feature.rating

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.core.analytics.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class RatingPhase { WAITING, CLAIMING, READY, SHOWING, REVIEW_PENDING, REVIEW_RUNNING, FINISHED }

/** 不持有页面、ReviewManager 或 ReviewInfo；星级结果只代表应用内选择，不代表 Play 实际评价。 */
internal class RatingPromptViewModel(
    private val store: RatingPromptStore,
    private val saved: SavedStateHandle,
    private val events: EventSink = BusinessTelemetry,
) : ViewModel() {
    private val restored = RatingPhase.entries.firstOrNull { it.name == saved.get<String>(KEY) }
    private val current = MutableStateFlow(when (restored) {
        RatingPhase.CLAIMING -> RatingPhase.WAITING
        RatingPhase.REVIEW_RUNNING -> RatingPhase.FINISHED // Play token 单次有效，不重放已发起的原生评价。
        else -> restored ?: RatingPhase.WAITING
    })
    val phase = current.asStateFlow()

    fun claim() {
        if (current.value != RatingPhase.WAITING) return
        update(RatingPhase.CLAIMING)
        viewModelScope.launch {
            try { update(if (store.claim()) RatingPhase.READY else RatingPhase.FINISHED) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { update(RatingPhase.FINISHED) }
            catch (_: SecurityException) { update(RatingPhase.FINISHED) }
        }
    }

    fun shown() {
        if (current.value != RatingPhase.READY && current.value != RatingPhase.SHOWING) return
        update(RatingPhase.SHOWING)
        if (saved.get<Boolean>(SHOWN_EVENT) != true) {
            saved[SHOWN_EVENT] = true
            events.send(MetricEvent.RATE_SHOW, emptyMap())
        }
    }

    fun decide(stars: Int, submit: Boolean) {
        if (stars !in 1..5 || current.value != RatingPhase.SHOWING) return
        val openStore = submit && stars >= 4
        update(if (openStore) RatingPhase.REVIEW_PENDING else RatingPhase.FINISHED)
        // 低分仅关闭，埋点必须与实际跳转行为一致。
        events.send(MetricEvent.RATE_CLICK, mapOf("stars" to stars, "action" to if (openStore) "store" else "close"))
    }

    fun beginReview(): Boolean {
        if (current.value != RatingPhase.REVIEW_PENDING) return false
        update(RatingPhase.REVIEW_RUNNING)
        return true
    }

    fun finish() = update(RatingPhase.FINISHED)

    private fun update(value: RatingPhase) {
        saved[KEY] = value.name
        current.value = value
    }
    private companion object {
        const val KEY = "rating.phase"
        const val SHOWN_EVENT = "rating.shown_event"
    }
}
