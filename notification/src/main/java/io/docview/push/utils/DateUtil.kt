package io.docview.push.utils

import android.os.Build
import android.util.Log
import java.util.*

/**
 * 日期时间工具类
 */
object DateUtil {

    /**
     * 获取时区偏移小时数
     * @return 时区偏移小时数
     */
    fun getTimeZoneOffsetHours(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val offsetSeconds = java.time.ZoneId.systemDefault().rules
                .getOffset(java.time.Instant.now()).totalSeconds
            offsetSeconds / 3600
        } else {
            val calendar = Calendar.getInstance()
            val offsetMillis = TimeZone.getDefault().getOffset(calendar.timeInMillis)
            offsetMillis / (1000 * 3600)
        }
    }

    /**
     * 获取带时区的 Firebase 主题名称
     * @param topic 基础主题名称
     * @return 带时区的主题名称
     */
    fun getFirebaseTopicWithTimezone(topic: String): String {
        return "${topic}_${getTimeZoneOffsetHours() + 24}"
    }
}
