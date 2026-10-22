package io.docview.push.scene

import android.content.Context
import io.docview.push.R
import java.util.Date

/** 只格式化通知事件当时的明确快照，不用估计时长或当前电量冒充充电结束记录。 */
object SceneLandingPresentation {
    fun batterySummary(context: Context, event: SceneLandingData?): String? {
        if (event?.code != 1102 || event.occurredAt <= 0 || event.batteryPercent == null) return null
        val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(event.occurredAt))
        val summary = context.getString(R.string.scene_charge_summary, time, event.batteryPercent)
        return event.chargeDurationMs?.let { duration ->
            summary + "\n" + context.getString(R.string.scene_charge_duration, android.text.format.DateUtils.formatElapsedTime(duration / 1000))
        } ?: summary
    }
}
