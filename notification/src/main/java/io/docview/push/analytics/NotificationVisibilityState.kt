package io.docview.push.analytics

/** 不存 Activity，只记录可见页面计数及这次回前台是否尚未 resume，供点击入口立即取样。 */
internal class NotificationVisibilityState {
    private var started = 0
    private var resumingFromBackground = true

    @Synchronized fun started() {
        if (started == 0) resumingFromBackground = true
        started++
    }
    @Synchronized fun resumed() { resumingFromBackground = false }
    @Synchronized fun stopped() {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0) resumingFromBackground = true
    }
    @Synchronized fun backgroundForDisplay() = started == 0
    @Synchronized fun backgroundForClick() = started == 0 || resumingFromBackground
}
