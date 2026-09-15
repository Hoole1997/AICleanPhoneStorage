package io.docview.push.check

import io.docview.push.config.ConfigCtrl
import io.docview.push.utils.Logger
import io.docview.push.utils.ResetCtrl
import com.blankj.utilcode.util.TimeUtils
import io.docview.push.timing.TimingCtrl
import io.docview.push.host.PushBooleanPreference
import io.docview.push.host.canSendNotification

/**
 * 通知检查控制器
 * 统一处理通知前的各种检查逻辑
 */
class CheckCtrl private constructor() {

    companion object {
        private const val TAG = "CheckCtrl"

        @Volatile
        private var INSTANCE: CheckCtrl? = null

        fun getInstance(): CheckCtrl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CheckCtrl().also { INSTANCE = it }
            }
        }

        var debugZeroInterval by PushBooleanPreference("sddnn442323", false)
    }

    // 使用0点重置控制器管理通知时间和计数
    private val resetController = ResetCtrl.getInstance()

    // 通知时间相关的键名常量
    private object Keys {
        const val LAST_UNLOCK_NOTIFICATION_TIME = "last_unlock_notification_time"
        const val LAST_BACKGROUND_NOTIFICATION_TIME = "last_background_notification_time"
        const val CURRENT_NOTIFICATION_COUNT = "current_notification_count"
    }

    // 通知计数相关
    private var context: android.content.Context? = null
    private var cachedInstallTime: Long = 0L

    /**
     * 通知类型枚举
     */
    enum class NotificationType(val string: String) {
        UNLOCK("local_push"),      // 解锁通知
        BACKGROUND("local_push"),  // 后台通知
        KEEPALIVE("local_push"),   // 保活通知（无间隔限制）
        FCM("firebase_push"),          // FCM 推送通知（无间隔限制）
        RESIDENT("top_notification"),          // 常驻（无间隔限制）
        EARTHQUAKE("earthquake"),          // 地震通知（无间隔限制）
    }

    /**
     * 初始化通知检查控制器
     * @param context 上下文
     */
    fun initialize(context: android.content.Context) {
        if (this.context != null) {
            Logger.d("通知检查控制器已经初始化")
            return
        }

        this.context = context.applicationContext
        resetController.initialize(context)
        Logger.d("通知检查控制器初始化完成")
    }

    /**
     * 检查是否可以触发指定类型的通知，并返回拦截原因
     * @param type 通知类型
     * @return 检查结果，包含是否可以触发和拦截原因
     */
    fun canTriggerNotificationWithReason(type: NotificationType): Pair<Boolean, BlockReason?> {
        // 1. 检查基础条件（通知权限、开关、免打扰、前台状态、总次数限制、新用户冷却时间）
        val basicConditionResult = checkAllConditionsWithReason()
        if (!basicConditionResult.first) {
            return Pair(false, basicConditionResult.second)
        }

        // 2. 检查触发间隔
        val intervalResult = checkTriggerIntervalWithReason(type)
        if (!intervalResult.first) {
            return Pair(false, intervalResult.second)
        }

        return Pair(true, null)
    }

    /**
     * 检查所有触发条件，并返回拦截原因
     * @return 检查结果，包含是否满足所有条件和拦截原因
     */
    private fun checkAllConditionsWithReason(): Pair<Boolean, BlockReason?> {

        // 检查通知次数限制
        val totalCount = ConfigCtrl.getTotalPushCount()
        val currentCount = getCurrentNotificationCount()
        if (currentCount >= totalCount) {
            Logger.d("已达到通知次数限制: $currentCount/$totalCount")
            return Pair(false, BlockReason.DAILY_LIMIT_REACHED)
        }

        // 检查通知权限
        if (!canSendNotification()) {
            Logger.d("无通知权限")
            return Pair(false, BlockReason.NO_PERMISSION)
        }

        // 检查通知开关
        if (!ConfigCtrl.isNotificationEnabled()) {
            Logger.d("通知开关已关闭")
            return Pair(false, BlockReason.NOTIFICATION_DISABLED)
        }

        // 检查免打扰时间段
        if (ConfigCtrl.isInDoNotDisturbTime()) {
            val startTime = ConfigCtrl.getDoNotDisturbStartTime()
            val endTime = ConfigCtrl.getDoNotDisturbEndTime()
            Logger.d("当前在免打扰时间段内 (${startTime}-${endTime})")
            return Pair(false, BlockReason.DO_NOT_DISTURB)
        }

        // 检查应用前台状态
        if (TimingCtrl.getInstance().isAppInForeground()) {
            Logger.d("通知检查未通过：应用在前台，不发送通知")
            return Pair(false, BlockReason.APP_IN_FOREGROUND)
        }

        // 检查新用户冷却时间
        val cooldownMinutes = ConfigCtrl.getNewUserCooldownMin()
        if (cooldownMinutes > 0) {
            val currentTime = System.currentTimeMillis()
            val cooldownMs = cooldownMinutes * 60 * 1000L
            val appInstallTime = getAppInstallTime()

            if (currentTime - appInstallTime < cooldownMs) {
                // 判断仍以毫秒为准；分钟取整会把尚未到期的最后 59 秒误写成“等待 0 分钟”。
                val remainingMs = cooldownMs - (currentTime - appInstallTime)
                Logger.d("新用户冷却时间未满足，还需等待 ${remainingMs / 1000.0} 秒 (remaining_ms=$remainingMs, 冷却时长: ${cooldownMinutes} 分钟)")
                return Pair(false, BlockReason.NEW_USER_COOLDOWN)
            }
        }

        return Pair(true, null)
    }

    /**
     * 检查是否有通知权限
     * @return 是否有通知权限
     */
    private fun canSendNotification(): Boolean {
        val context = context ?: return false

        return try {
            context.canSendNotification()
        } catch (e: Exception) {
            Logger.e("检查通知权限失败", e)
            false
        }
    }

    /**
     * 检查触发间隔，并返回拦截原因
     * @param type 通知类型
     * @return 检查结果，包含是否满足触发间隔要求和拦截原因
     */
    private fun checkTriggerIntervalWithReason(type: NotificationType): Pair<Boolean, BlockReason?> {
        val currentTime = System.currentTimeMillis()
        val lastTime = if(debugZeroInterval) 0L else getLastNotificationTimeInternal(type)
        val intervalMs = if(debugZeroInterval) 0L else  getNotificationInterval(type)

        // 如果是第一次触发，或者距离上次触发已经超过间隔时间
        val canTrigger = lastTime == 0L || (currentTime - lastTime) >= intervalMs

        if (!canTrigger) {
            val remainingMs = lastTime + intervalMs - currentTime
            val intervalMinutes = (intervalMs / (60 * 1000)).toInt()
            Logger.d("${type.name}通知间隔未满足，还需等待 ${remainingMs / 1000.0} 秒 (remaining_ms=$remainingMs, 间隔时长: ${intervalMinutes} 分钟)")
            return Pair(false, BlockReason.TRIGGER_INTERVAL_NOT_MET)
        }

        return Pair(true, null)
    }

    /**
     * 获取应用安装时间（通过包信息获取，带缓存）
     * @return 应用安装时间戳
     */
    private fun getAppInstallTime(): Long {
        // 如果已经缓存了安装时间，直接返回
        if (cachedInstallTime > 0) {
            return cachedInstallTime
        }

        return try {
            val packageInfo = context?.packageManager?.getPackageInfo(
                context?.packageName ?: "",
                0
            )

            // firstInstallTime 是应用首次安装的时间（Android P+）
            val installTime = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo?.firstInstallTime ?: System.currentTimeMillis()
            } else {
                // Android P 以下版本使用 lastUpdateTime 作为近似值
                packageInfo?.lastUpdateTime ?: System.currentTimeMillis()
            }

            // 缓存安装时间
            cachedInstallTime = installTime

            Logger.d("应用安装时间: ${TimeUtils.millis2String(installTime)}")
            installTime
        } catch (e: Exception) {
            Logger.e("获取应用安装时间失败", e)
            // 如果获取失败，返回24小时前的时间（表示已经过了冷却期）
            val fallbackTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            cachedInstallTime = fallbackTime
            fallbackTime
        }
    }

    /**
     * 获取指定类型通知的最后触发时间（内部使用）
     * @param type 通知类型
     * @return 最后触发时间
     */
    private fun getLastNotificationTimeInternal(type: NotificationType): Long {
        return when (type) {
            NotificationType.UNLOCK -> resetController.getLongValue(Keys.LAST_UNLOCK_NOTIFICATION_TIME)
            NotificationType.BACKGROUND -> resetController.getLongValue(Keys.LAST_BACKGROUND_NOTIFICATION_TIME)
            else -> 0L
        }
    }

    /**
     * 获取指定类型通知的间隔时间（毫秒）
     * @param type 通知类型
     * @return 间隔时间（毫秒）
     */
    private fun getNotificationInterval(type: NotificationType): Long {
        return when (type) {
            NotificationType.UNLOCK -> ConfigCtrl.getUnlockPushIntervalMin() * 60 * 1000L
            NotificationType.BACKGROUND -> ConfigCtrl.getBackgroundPushIntervalMin() * 60 * 1000L
            else -> 0L
        }
    }

    /**
     * 记录通知触发时间
     * @param type 通知类型
     */
    fun recordNotificationTrigger(type: NotificationType) {
        val currentTime = System.currentTimeMillis()
        when (type) {
            NotificationType.UNLOCK -> resetController.setLongValue(Keys.LAST_UNLOCK_NOTIFICATION_TIME, currentTime)
            NotificationType.BACKGROUND -> resetController.setLongValue(Keys.LAST_BACKGROUND_NOTIFICATION_TIME, currentTime)
            else -> {
                // 其它，因为无间隔限制
                Logger.d("${type.name}通知触发，不记录时间")
            }
        }
        Logger.d("${type.name}通知触发时间: ${TimeUtils.millis2String(currentTime, "yyyy-MM-dd HH:mm:ss")}")
    }

    /**
     * 获取当前通知次数（不进行0点重置）
     * @return 当前通知次数
     */
    private fun getCurrentNotificationCount(): Int {
        return resetController.getIntValue(Keys.CURRENT_NOTIFICATION_COUNT, enableMidnightReset = false)
    }

    /**
     * 增加通知计数（外部调用）
     */
    fun incrementNotificationCount() {
        val newCount = resetController.incrementIntValue(Keys.CURRENT_NOTIFICATION_COUNT, enableMidnightReset = false)
        Logger.d("通知计数增加: $newCount")
    }

    /**
     * 重置所有通知时间记录
     */
    fun resetAllNotificationTimes() {
        resetController.resetValue(Keys.LAST_UNLOCK_NOTIFICATION_TIME)
        resetController.resetValue(Keys.LAST_BACKGROUND_NOTIFICATION_TIME)
        Logger.d("重置所有通知时间记录")
    }

    /**
     * 重置指定类型的通知时间记录
     * @param type 通知类型
     */
    fun resetNotificationTime(type: NotificationType) {
        when (type) {
            NotificationType.UNLOCK -> resetController.resetValue(Keys.LAST_UNLOCK_NOTIFICATION_TIME)
            NotificationType.BACKGROUND -> resetController.resetValue(Keys.LAST_BACKGROUND_NOTIFICATION_TIME)
            else -> {
                // 其它，无需重置
                Logger.d("${type.name}通知无时间记录，无需重置")
            }
        }
        Logger.d("重置${type.name}通知时间记录")
    }

    /**
     * 获取指定类型通知的最后触发时间（外部接口）
     * @param type 通知类型
     * @return 最后触发时间
     */
    fun getLastNotificationTime(type: NotificationType): Long {
        return getLastNotificationTimeInternal(type)
    }

    /**
     * 获取指定类型通知的剩余等待时间（分钟）
     * @param type 通知类型
     * @return 剩余等待时间（分钟）
     */
    fun getRemainingWaitTime(type: NotificationType): Int {
        val currentTime = System.currentTimeMillis()
        val lastTime = getLastNotificationTimeInternal(type)
        val intervalMs = getNotificationInterval(type)

        if (lastTime == 0L) return 0

        val remainingMs = lastTime + intervalMs - currentTime
        return if (remainingMs > 0) (remainingMs / (60 * 1000)).toInt() else 0
    }

        /**
     * 重置今日通知计数
     */
    fun resetTodayNotificationCount() {
        resetController.resetValue(Keys.CURRENT_NOTIFICATION_COUNT, enableMidnightReset = false)
        Logger.d("重置通知计数")
    }

    /**
     * 获取今日通知计数
     * @return 今日已发送的通知数量
     */
    fun getTodayNotificationCount(): Int {
        return resetController.getIntValue(Keys.CURRENT_NOTIFICATION_COUNT, enableMidnightReset = false)
    }

    /**
     * 获取剩余可发送通知数量
     * @return 剩余可发送数量
     */
    fun getRemainingNotificationCount(): Int {
        val totalCount = ConfigCtrl.getTotalPushCount()
        val currentCount = getTodayNotificationCount()
        return maxOf(0, totalCount - currentCount)
    }

    /**
     * 检查是否还有剩余通知次数
     * @return 是否还有剩余次数
     */
    fun hasRemainingNotificationCount(): Boolean {
        return getRemainingNotificationCount() > 0
    }

    /**
     * 获取通知统计信息
     * @return 通知统计信息字符串
     */
    fun getNotificationStats(): String {
        val totalCount = ConfigCtrl.getTotalPushCount()
        val currentCount = getTodayNotificationCount()
        val remainingCount = getRemainingNotificationCount()
        val installTime = getAppInstallTime()
        val installDate = java.time.Instant.ofEpochMilli(installTime)

        return "今日已发送: $currentCount, 总限制: $totalCount, 剩余: $remainingCount, 安装时间: $installDate"
    }
}
