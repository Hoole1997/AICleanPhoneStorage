package io.docview.push.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.docview.push.service.CoreService
import io.docview.push.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.docview.push.host.PushUserChannel
import io.docview.push.host.PushStringPreference
import io.docview.push.host.PushRemoteConfig
import java.io.IOException
import kotlin.math.max

/**
 * 推送通知配置控制器
 */
object ConfigCtrl {

    private const val TAG = "ConfigCtrl"
    private const val CONFIG_FILE_NAME = "pvvvush_config.json"

    @Volatile private var config: NotificationConfig? = null
    private var channelListenerInstalled = false
    private var configJsonFromRemote by PushStringPreference("121212notifica232tionConfigJsonRemote", "")

    /**
     * 初始化配置
     * @param context 上下文
     * @return 是否初始化成功
     */
    fun initialize(context: Context): Boolean {
        return try {
            val jsonString = configJsonFromRemote.orEmpty().takeIf { it.isNotEmpty() }?:loadConfigFromAssets(context)
            config = runCatching { parseConfig(jsonString) }
                .getOrElse { parseConfig(loadConfigFromAssets(context)) }
            Logger.d("配置初始化成功，当前使用渠道: ${PushUserChannel.getCurrentChannel().value}")
            setKeepAliveInterval()

            // 异步获取远程配置
            fetchRemoteConfig()

            // 监听用户渠道变化
            setupChannelListener()

            true
        } catch (e: Exception) {
            Logger.e("配置初始化失败", e)
            false
        }
    }

    /**
     * 设置渠道监听器
     */
    private fun setupChannelListener() {
        if (channelListenerInstalled) return
        channelListenerInstalled = true
        PushUserChannel.addChannelChangeListener(object : PushUserChannel.ChannelChangeListener {
            override fun onChannelChanged(oldChannel: PushUserChannel.UserChannelType, newChannel: PushUserChannel.UserChannelType) {
                Logger.d("通知渠道变化: ${oldChannel.value} -> ${newChannel.value}")
                // 渠道变化时，可以在这里做一些额外的处理
                // 比如重新加载配置、清理缓存等
            }
        })
    }

    /**
     * 异步获取远程推送配置
     */
    private fun fetchRemoteConfig() {
        io.docview.push.host.PushEnvironment.scope.launch {
            try {
                Logger.d("开始获取远程推送配置")
                val remoteJsonString = PushRemoteConfig.getString("pushConfigJson", "")

                if (remoteJsonString != null && remoteJsonString.isNotEmpty()) {
                    Logger.d("成功获取远程推送配置")
                    val remoteConfig = parseConfig(remoteJsonString)

                    // 更新本地配置
                    config = remoteConfig
                    configJsonFromRemote = remoteJsonString
                    setKeepAliveInterval()
                    Logger.d("远程推送配置更新成功")
                } else {
                    Logger.w("远程推送配置为空或获取超时，使用本地配置")
                }

            } catch (e: Exception) {
                Logger.e("获取远程推送配置异常", e)
            }
        }
    }

    fun setKeepAliveInterval(){
        runCatching {
            val seconds = max(
                getKeepalivePollingIntervalMinutes() * 60L,
                CoreService.defaultIntervalSeconds
            )
            CoreService.setDefaultIntervalSeconds1(seconds)
        }
    }

    /**
     * 获取推送总次数
     * @return 推送总次数
     */
    fun getTotalPushCount(): Int {
        return getCurrentConfig()?.totalPushCount ?: 0
    }

    /**
     * 获取解锁推送间隔（分钟）
     * @return 解锁推送间隔（分钟）
     */
    fun getUnlockPushIntervalMin(): Int {
        return getCurrentConfig()?.unlockPushInterval ?: 10
    }

    /**
     * 获取压后台推送间隔（分钟）
     * @return 压后台推送间隔（分钟）
     */
    fun getBackgroundPushIntervalMin(): Int {
        return getCurrentConfig()?.backgroundPushInterval ?: 10
    }

    /**
     * 获取悬停时长策略开关
     * @return 悬停时长策略开关 (1: 开启, 0: 关闭)
     */
    fun getHoverDurationStrategySwitch(): Int {
        return getCurrentConfig()?.hoverDurationStrategySwitch ?: 0
    }

    /**
     * 获取悬停时长循环次数
     * @return 悬停时长循环次数
     */
    fun getHoverDurationLoopCount(): Int {
        return getCurrentConfig()?.hoverDurationLoopCount ?: 0
    }

    /**
     * 获取新用户冷却时间（分钟）
     * @return 新用户冷却时间（分钟）
     */
    fun getNewUserCooldownMin(): Int {
        return getCurrentConfig()?.newUserCooldown ?: 0
    }

    /**
     * 获取免打扰开始时间
     * @return 免打扰开始时间 (格式: HH:mm)
     */
    fun getDoNotDisturbStartTime(): String {
        return getCurrentConfig()?.doNotDisturbStart ?: "02:00"
    }

    /**
     * 获取免打扰结束时间
     * @return 免打扰结束时间 (格式: HH:mm)
     */
    fun getDoNotDisturbEndTime(): String {
        return getCurrentConfig()?.doNotDisturbEnd ?: "08:00"
    }

    /**
     * 检查当前时间是否在免打扰时间段内
     * @return 是否在免打扰时间段内
     */
    fun isInDoNotDisturbTime(): Boolean {
        val startTime = getDoNotDisturbStartTime()
        val endTime = getDoNotDisturbEndTime()

        return try {
            val currentTime = java.time.LocalTime.now()
            val start = java.time.LocalTime.parse(startTime)
            val end = java.time.LocalTime.parse(endTime)

            if (start <= end) {
                // 同一天内的时间段
                currentTime >= start && currentTime <= end
            } else {
                // 跨天的时间段 (如 22:00-06:00)
                currentTime >= start || currentTime <= end
            }
        } catch (e: Exception) {
            Logger.e("解析免打扰时间失败", e)
            false
        }
    }

    /**
     * 获取通知开关状态
     * @return 通知开关状态 (1: 开启, 0: 关闭)
     */
    fun getNotificationEnabled(): Int {
        return getCurrentConfig()?.notificationEnabled ?: 1
    }

    /**
     * 检查通知是否开启
     * @return 通知是否开启
     */
    fun isNotificationEnabled(): Boolean {
        return getNotificationEnabled() == 1
    }

    /**
     * 获取保活轮询间隔（分钟）
     * @return 保活轮询间隔（分钟）
     */
    fun getKeepalivePollingIntervalMinutes(): Int {
        return getCurrentConfig()?.keepalivePollingIntervalMinutes ?: 15 // 默认15分钟
    }

    /**
     * 获取当前配置
     * @return 当前渠道的配置
     */
    private fun getCurrentConfig(): Config? {
        return when (PushUserChannel.getCurrentChannel()) {
            PushUserChannel.UserChannelType.PAID -> config?.paidChannel
            PushUserChannel.UserChannelType.NATURAL -> config?.organicChannel
        }
    }

    /**
     * 从 assets 加载配置文件
     * @param context 上下文
     * @return JSON 字符串
     */
    private fun loadConfigFromAssets(context: Context): String {
        return try {
            context.assets.open(CONFIG_FILE_NAME).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Logger.e("加载配置文件失败", e)
            throw e
        }
    }

    /**
     * 解析配置 JSON
     * @param jsonString JSON 字符串
     * @return 配置对象
     */
    private fun parseConfig(jsonString: String): NotificationConfig {
        return try {
            parseNotificationConfig(jsonString)
        } catch (e: JsonSyntaxException) {
            Logger.e("解析配置文件失败", e)
            throw e
        }
    }

    /**
     * 检查配置是否已初始化
     * @return 是否已初始化
     */
    fun isInitialized(): Boolean {
        return config != null
    }

    /**
     * 获取完整配置（用于调试）
     * @return 完整配置对象
     */
    fun getFullConfig(): NotificationConfig? {
        return config
    }

    // ===== 内部辅助方法 =====

    @Suppress("unused")
    private fun validateConfigData(data: Any?): Boolean {
        return data != null && data.toString().isNotEmpty()
    }

    @Suppress("unused")
    private fun processConfigString(input: String): String {
        return input.trim().takeIf { it.isNotEmpty() } ?: ""
    }

    @Suppress("unused")
    private fun generateCacheKey(prefix: String): String {
        return "${prefix}_${System.currentTimeMillis()}"
    }

    @Suppress("unused")
    private const val CONFIG_VERSION = "2.0.1"

    @Suppress("unused")
    private const val CACHE_EXPIRY_HOURS = 24
}
