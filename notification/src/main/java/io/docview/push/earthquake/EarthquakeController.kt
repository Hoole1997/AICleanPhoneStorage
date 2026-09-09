package io.docview.push.earthquake

import com.google.gson.Gson
import io.docview.push.check.CheckCtrl
import io.docview.push.controller.TriggerCtrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.docview.push.host.PushLanguage.Companion.ARABIC
import io.docview.push.host.PushLanguage.Companion.CHINESE_CN
import io.docview.push.host.PushLanguage.Companion.CHINESE_HK
import io.docview.push.host.PushLanguage.Companion.CHINESE_MO
import io.docview.push.host.PushLanguage.Companion.CHINESE_TW
import io.docview.push.host.PushLanguage.Companion.DANISH
import io.docview.push.host.PushLanguage.Companion.ENGLISH
import io.docview.push.host.PushLanguage.Companion.FRENCH
import io.docview.push.host.PushLanguage.Companion.GERMAN
import io.docview.push.host.PushLanguage.Companion.HINDI
import io.docview.push.host.PushLanguage.Companion.INDONESIAN
import io.docview.push.host.PushLanguage.Companion.ITALIAN
import io.docview.push.host.PushLanguage.Companion.JAPANESE
import io.docview.push.host.PushLanguage.Companion.KOREAN
import io.docview.push.host.PushLanguage.Companion.PERSIAN
import io.docview.push.host.PushLanguage.Companion.PORTUGUESE
import io.docview.push.host.PushLanguage.Companion.RUSSIAN
import io.docview.push.host.PushLanguage.Companion.SPANISH
import io.docview.push.host.PushLanguage.Companion.SWEDISH
import io.docview.push.host.PushLanguage.Companion.THAI
import io.docview.push.host.PushLanguage.Companion.TURKISH
import io.docview.push.host.PushLanguage.Companion.VIETNAMESE
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.Utils
import io.docview.push.host.canSendNotification
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 地震信息控制器
 * 用于获取USGS地震数据
 */
object EarthquakeController {

    private const val BASE_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query"
    private const val DATE_FORMAT = "yyyy-MM-dd"
    private const val TIME_FORMAT = "yyyy/MM/dd HH:mm:ss"
    private const val SHORT_TIME_FORMAT = "h:mm a"

    // 缓存时间：30分钟（USGS数据通常在5-20分钟内更新）
    private const val CACHE_DURATION_MS = 30 * 60 * 1000L

    // 推送间隔配置
    private const val MINOR_EARTHQUAKE_INTERVAL_MS = 32 * 60 * 60 * 1000L // 32小时（毫秒）
    private const val PREFS_NAME = "earthquake_push_prefs"
    private const val KEY_LAST_PUSH_TIME = "last_push_time"

    // 定时推送时间段配置（持久化存储键名）
    private const val KEY_MORNING_TIME_RANGE = "morning_time_range"
    private const val KEY_EVENING_TIME_RANGE = "evening_time_range"

    // 默认时间段配置（格式：HH:mm-HH:mm）
    private const val DEFAULT_MORNING_TIME_RANGE = "8:00-8:30"
    private const val DEFAULT_EVENING_TIME_RANGE = "17:00-17:30"

    // 时间段执行记录键名
    private const val KEY_MORNING_EXECUTED_DATE = "morning_executed_date"
    private const val KEY_EVENING_EXECUTED_DATE = "evening_executed_date"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // 缓存数据和时间戳
    @Volatile
    private var cachedData: List<EarthquakeFeature>? = null

    @Volatile
    private var cacheTimestamp: Long = 0

    @Volatile
    private var cacheKey: String? = null

    /**
     * 地震推送消息映射，key为语言代码，value为推送消息模板（%s为震级占位符）
     */
    val earthquakePushMessageMap: Map<String, String> = mapOf(
        ENGLISH to "M%s EARTHQUAKE CONFIRMED",
        SPANISH to "TERREMOTO M%s CONFIRMADO",
        PORTUGUESE to "TERREMOTO M%s CONFIRMADO",
        KOREAN to "규모 %s 지진 발생",
        JAPANESE to "M%sの地震が発生しました",
        FRENCH to "SÉISME M%s CONFIRMÉ",
        GERMAN to "ERDBEBEN M%s BESTÄTIGT",
        TURKISH to "M%s DEPREM ONAYLANDI",
        RUSSIAN to "ЗЕМЛЕТРЯСЕНИЕ M%s ПОДТВЕРЖДЕНО",
        CHINESE_TW to "%s級地震確認發生",
        CHINESE_HK to "%s級地震確認發生",
        CHINESE_MO to "%s級地震確認發生",
        CHINESE_CN to "%s级地震确认发生",
        THAI to "เกิดแผ่นดินไหวขนาด %s แมกนิจูด",
        VIETNAMESE to "ĐỘNG ĐẤT M%s ĐÃ ĐƯỢC XÁC NHẬN",
        ARABIC to "زلزال M%s تم تأكيده",
        HINDI to "M%s भूकंप की पुष्टि की गई",
        INDONESIAN to "GEMPA BUMI M%s TERKONFIRMASI",
        ITALIAN to "TERREMOTO M%s CONFERMATO",
        DANISH to "JORDSKÆLV M%s BEKRÆFTET",
        PERSIAN to "زمین‌لرزه M%s تأیید شد",
        SWEDISH to "JORDSKÄLV M%s BEKRÄFTAT"
    )

    /**
     * 获取开始时间（昨天）
     */
    private fun getStartTime(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(calendar.time)
    }

    /**
     * 获取结束时间（今天）
     */
    private fun getEndTime(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(calendar.time)
    }

    /**
     * 格式化时间戳为指定格式
     */
    private fun formatTime(timestamp: Long, pattern: String): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return SimpleDateFormat(pattern, Locale.ENGLISH).format(calendar.time)
    }

    /**
     * 根据经纬度获取时区
     * @param longitude 经度
     * @param latitude 纬度
     * @return 时区对象，如果无法确定则返回UTC时区
     */
    private fun getTimeZoneByLocation(longitude: Double, latitude: Double): TimeZone {
        // 常见地区的时区映射（基于经纬度范围）
        // 这是一个简化的实现，更精确的实现需要使用时区数据库或API

        // 日本地区 (JST)
        if (longitude >= 125.0 && longitude <= 146.0 && latitude >= 24.0 && latitude <= 46.0) {
            return TimeZone.getTimeZone("Asia/Tokyo")
        }

        // 中国地区 (CST)
        if (longitude >= 73.0 && longitude <= 135.0 && latitude >= 18.0 && latitude <= 54.0) {
            return TimeZone.getTimeZone("Asia/Shanghai")
        }

        // 美国西海岸 (PST/PDT)
        if (longitude >= -125.0 && longitude <= -114.0 && latitude >= 32.0 && latitude <= 49.0) {
            return TimeZone.getTimeZone("America/Los_Angeles")
        }

        // 美国东海岸 (EST/EDT)
        if (longitude >= -84.0 && longitude <= -66.0 && latitude >= 24.0 && latitude <= 50.0) {
            return TimeZone.getTimeZone("America/New_York")
        }

        // 欧洲中部 (CET/CEST)
        if (longitude >= -10.0 && longitude <= 40.0 && latitude >= 35.0 && latitude <= 72.0) {
            return TimeZone.getTimeZone("Europe/Paris")
        }

        // 印度 (IST)
        if (longitude >= 68.0 && longitude <= 97.0 && latitude >= 6.0 && latitude <= 37.0) {
            return TimeZone.getTimeZone("Asia/Kolkata")
        }

        // 澳大利亚东部 (AEST/AEDT)
        if (longitude >= 113.0 && longitude <= 154.0 && latitude >= -44.0 && latitude <= -10.0) {
            return TimeZone.getTimeZone("Australia/Sydney")
        }

        // 新西兰 (NZST/NZDT)
        if (longitude >= 166.0 && longitude <= 179.0 && latitude >= -47.0 && latitude <= -34.0) {
            return TimeZone.getTimeZone("Pacific/Auckland")
        }

        // 其他地区：根据经度估算UTC偏移量（粗略估算）
        val offsetHours = (longitude / 15.0).toInt()

        // 创建自定义时区ID
        val offsetId = if (offsetHours >= 0) {
            "GMT+$offsetHours"
        } else {
            "GMT$offsetHours"
        }

        return TimeZone.getTimeZone(offsetId)
    }

    /**
     * 格式化时间为地震发生地时区的短时间格式（格式：JST 7:30 PM）
     * @param timestamp 时间戳（UTC时间）
     * @param longitude 经度
     * @param latitude 纬度
     */
    private fun formatShortTime(timestamp: Long, longitude: Double, latitude: Double): String {
        val timeZone = getTimeZoneByLocation(longitude, latitude)
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = timestamp

        val dateFormat = SimpleDateFormat(SHORT_TIME_FORMAT, Locale.ENGLISH)
        dateFormat.timeZone = timeZone
        val timeText = dateFormat.format(calendar.time)

        // 获取时区短名称（如：JST、PST、GMT等），不包含偏移量
        var timeZoneText = timeZone.getDisplayName(timeZone.inDaylightTime(calendar.time), TimeZone.SHORT, Locale.ENGLISH)
        // 移除偏移量部分（如：GMT+08:00 -> GMT, GMT-05:00 -> GMT）
        timeZoneText = timeZoneText.replace(Regex("[+-]\\d{1,2}:\\d{2}"), "")

        return "$timeZoneText $timeText"
    }


    /**
     * 执行网络请求获取地震数据（在IO线程执行）
     * 包含缓存机制，缓存有效期为30分钟
     */
    private suspend fun fetchEarthquakeData(): List<EarthquakeFeature> = withContext(Dispatchers.IO) {
        val startTime = getStartTime()
        val endTime = getEndTime()
        val currentCacheKey = "${startTime}_$endTime"

        // 检查缓存是否有效
        val currentTime = System.currentTimeMillis()
        if (cachedData != null &&
            cacheKey == currentCacheKey &&
            (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
            return@withContext cachedData!!
        }

        // 缓存无效或不存在，请求新数据
        val url = "$BASE_URL?format=geojson&starttime=$startTime&endtime=$endTime&eventtype=earthquake&orderby=magnitude&limit=10"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            val result = if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                val earthquakeResponse = gson.fromJson(responseBody, EarthquakeResponse::class.java)
                earthquakeResponse.features
            } else {
                emptyList()
            }

            // 更新缓存
            cachedData = result
            cacheTimestamp = currentTime
            cacheKey = currentCacheKey

            result
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果请求失败，返回缓存数据（如果有）
            cachedData ?: emptyList()
        }
    }

    /**
     * 将 EarthquakeFeature 转换为 EarthquakeInfo
     */
    private fun convertToEarthquakeInfo(feature: EarthquakeFeature): EarthquakeInfo? {
        val properties = feature.properties
        val geometry = feature.geometry

        // 必须有震级数据才能转换
        val magnitude = properties.mag ?: return null
        val timestamp = properties.time ?: return null

        // 格式化时间为指定格式（2025/03/03 03:03:33）
        val formattedTime = formatTime(timestamp, TIME_FORMAT)

        // 获取震源深度（coordinates[2]是深度，单位：公里），保留1位小数
        val depth = if (geometry.coordinates.size >= 3) {
            String.format(Locale.ENGLISH, "%.1f", geometry.coordinates[2]).toDouble()
        } else {
            0.0
        }

        // 是否有海啸威胁（tsunami: 0=无, 1=有）
        val hasTsunami = properties.tsunami == 1

        // 震级保留1位小数
        val formattedMagnitude = String.format(Locale.ENGLISH, "%.1f", magnitude).toDouble()

        // 获取经纬度
        val longitude = if (geometry.coordinates.size >= 1) geometry.coordinates[0] else 0.0
        val latitude = if (geometry.coordinates.size >= 2) geometry.coordinates[1] else 0.0

        // 生成短时间格式（使用地震发生地的时区）
        val shortTime = formatShortTime(timestamp, longitude, latitude)

        // 生成短震级类型（取magType第一个字母转大写，如果不存在就为"M"）
        val shortMagType = if (properties.magType.isNullOrEmpty()) {
            "M"
        } else {
            properties.magType.first().uppercase()
        }

        // 处理警报级别，如果没有值则使用默认值"green"（低风险）
        val alert = properties.alert ?: "green"

        return EarthquakeInfo(
            magnitude = formattedMagnitude,
            time = formattedTime,
            depth = depth,
            hasTsunami = hasTsunami,
            alert = alert,
            magType = properties.magType,
            status = properties.status,
            place = properties.place,
            shortTime = shortTime,
            shortMagType = shortMagType
        )
    }

    /**
     * 获取震级大于等于5的地震
     */
    private suspend fun getMajorEarthquakes(): List<EarthquakeInfo> {
        val allEarthquakes = fetchEarthquakeData()
        return allEarthquakes
            .filter { it.properties.mag != null && it.properties.mag >= 5.0 }
            .mapNotNull { convertToEarthquakeInfo(it) }
    }

    /**
     * 获取震级小于5的地震
     */
    private suspend fun getMinorEarthquakes(): List<EarthquakeInfo> {
        val allEarthquakes = fetchEarthquakeData()
        return allEarthquakes
            .filter { it.properties.mag != null && it.properties.mag < 5.0 }
            .mapNotNull { convertToEarthquakeInfo(it) }
    }

    /**
     * 优先获取震级大于等于5的地震的第一个元素，如果不存在则获取震级小于5的地震的第一个元素
     * @return 地震信息，如果都不存在则返回null
     */
    private suspend fun getFirstEarthquake(): EarthquakeInfo? {
        val majorEarthquakes = getMajorEarthquakes()
        return if (majorEarthquakes.isNotEmpty()) {
            majorEarthquakes.first()
        } else {
            getMinorEarthquakes().firstOrNull()
        }
    }

    fun start(){
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            getFirstEarthquake()?.let { earthquake ->
                val magnitude = earthquake.magnitude
                val currentTime = System.currentTimeMillis()

                // 获取上次推送时间
                val lastPushTime = try {
                    SPUtils.getInstance(PREFS_NAME).getLong(KEY_LAST_PUSH_TIME, 0L)
                } catch (e: Exception) {
                    0L
                }

                // 判断是否需要触发推送
                val shouldTriggerPush = if (magnitude >= 5.0) {
                    // 震级 >= 5，直接触发推送
                    true
                } else {
                    // 震级 < 5，检查是否超过32小时间隔
                    if (lastPushTime == 0L) {
                        // 首次推送，直接触发
                        true
                    } else {
                        // 检查间隔是否超过32小时
                        (currentTime - lastPushTime) >= MINOR_EARTHQUAKE_INTERVAL_MS
                    }
                }

                if (shouldTriggerPush) {
                    // 触发推送
                    TriggerCtrl.triggerEarthquakeNotification(
                        type = CheckCtrl.NotificationType.EARTHQUAKE,
                        earthquake = earthquake
                    )

                    // 保存推送时间
                    try {
                        SPUtils.getInstance(PREFS_NAME).put(KEY_LAST_PUSH_TIME, currentTime)
                    } catch (e: Exception) {
                        // 保存失败不影响推送逻辑
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * 获取当前日期字符串（yyyy-MM-dd格式）
     */
    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(calendar.time)
    }

    /**
     * 检查当前时间是否在指定时间段内
     * @param startHour 开始小时
     * @param startMinute 开始分钟
     * @param endHour 结束小时
     * @param endMinute 结束分钟
     * @return true 如果当前时间在时间段内
     */
    private fun isInTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val startTimeInMinutes = startHour * 60 + startMinute
        val endTimeInMinutes = endHour * 60 + endMinute
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        return currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes <= endTimeInMinutes
    }

    /**
     * 检查今天是否已经在指定时间段执行过
     * @param key 存储键名
     * @return true 如果今天已经执行过
     */
    private fun isTodayExecuted(key: String): Boolean {
        val today = getCurrentDateString()
        val executedDate = try {
            SPUtils.getInstance(PREFS_NAME).getString(key, "")
        } catch (e: Exception) {
            ""
        }
        return executedDate == today
    }

    /**
     * 标记今天在指定时间段已执行
     * @param key 存储键名
     */
    private fun markTodayExecuted(key: String) {
        val today = getCurrentDateString()
        try {
            SPUtils.getInstance(PREFS_NAME).put(key, today)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 解析时间段字符串（格式：HH:mm-HH:mm）
     * @param timeRangeStr 时间段字符串，如 "8:00-8:30"
     * @return Pair<Pair<开始小时, 开始分钟>, Pair<结束小时, 结束分钟>>
     */
    private fun parseTimeRange(timeRangeStr: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        try {
            val parts = timeRangeStr.split("-")
            if (parts.size != 2) {
                throw IllegalArgumentException("时间段格式错误")
            }

            val startParts = parts[0].split(":")
            val endParts = parts[1].split(":")

            if (startParts.size != 2 || endParts.size != 2) {
                throw IllegalArgumentException("时间段格式错误")
            }

            val startHour = startParts[0].toInt()
            val startMinute = startParts[1].toInt()
            val endHour = endParts[0].toInt()
            val endMinute = endParts[1].toInt()

            return Pair(Pair(startHour, startMinute), Pair(endHour, endMinute))
        } catch (e: Exception) {
            e.printStackTrace()
            // 解析失败返回默认值
            return Pair(Pair(8, 0), Pair(8, 30))
        }
    }

    /**
     * 格式化时间段为字符串（格式：HH:mm-HH:mm）
     */
    private fun formatTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d-%02d:%02d", startHour, startMinute, endHour, endMinute)
    }

    /**
     * 获取时间段配置（从持久化存储读取，如果不存在则使用默认值）
     * @param key 存储键名
     * @param defaultValue 默认值
     * @return Pair<Pair<开始小时, 开始分钟>, Pair<结束小时, 结束分钟>>
     */
    private fun getTimeRangeConfig(key: String, defaultValue: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val timeRangeStr = try {
            SPUtils.getInstance(PREFS_NAME).getString(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
        return parseTimeRange(timeRangeStr)
    }

    /**
     * 设置早上时间段配置（持久化存储）
     */
    private fun setMorningTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        try {
            val timeRangeStr = formatTimeRange(startHour, startMinute, endHour, endMinute)
            SPUtils.getInstance(PREFS_NAME).put(KEY_MORNING_TIME_RANGE, timeRangeStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 设置下午时间段配置（持久化存储）
     */
    private fun setEveningTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        try {
            val timeRangeStr = formatTimeRange(startHour, startMinute, endHour, endMinute)
            SPUtils.getInstance(PREFS_NAME).put(KEY_EVENING_TIME_RANGE, timeRangeStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 检查当前时间是否在早上8:00-8:30或下午17:00-17:30时间段内
     * 如果在时间段内且今天还未执行，则调用start()函数
     * 时间段配置从持久化存储读取，如果不存在则使用默认值
     */
    fun checkAndTriggerScheduledPush() {
        if (Utils.getApp()?.canSendNotification() == false) return

        // 获取早上时间段配置
        val morningRange = getTimeRangeConfig(KEY_MORNING_TIME_RANGE, DEFAULT_MORNING_TIME_RANGE)

        // 获取下午时间段配置
        val eveningRange = getTimeRangeConfig(KEY_EVENING_TIME_RANGE, DEFAULT_EVENING_TIME_RANGE)

        // 检查是否在早上时间段
        val isInMorningRange = isInTimeRange(
            morningRange.first.first, morningRange.first.second,
            morningRange.second.first, morningRange.second.second
        )

        // 检查是否在下午时间段
        val isInEveningRange = isInTimeRange(
            eveningRange.first.first, eveningRange.first.second,
            eveningRange.second.first, eveningRange.second.second
        )

        // 如果在早上时间段且今天还未执行
        if (isInMorningRange && !isTodayExecuted(KEY_MORNING_EXECUTED_DATE)) {
            start()
            markTodayExecuted(KEY_MORNING_EXECUTED_DATE)
            return
        }

        // 如果在下午时间段且今天还未执行
        if (isInEveningRange && !isTodayExecuted(KEY_EVENING_EXECUTED_DATE)) {
            start()
            markTodayExecuted(KEY_EVENING_EXECUTED_DATE)
            return
        }
    }
}
