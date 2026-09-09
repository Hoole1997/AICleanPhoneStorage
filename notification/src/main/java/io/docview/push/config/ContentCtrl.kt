package io.docview.push.config

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import io.docview.push.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.docview.push.host.PushStringPreference
import io.docview.push.host.PushRemoteConfig
import java.io.IOException

/**
 * 推送通知内容控制器
 */
object ContentController {

    private const val CONTENT_CONFIG_FILE_NAME = "pvvvvush_content_config.json"
    private const val SP_NAME = "push_content_prefs"
    private const val KEY_CURRENT_INDEX = "current_index"

    @Volatile private var pushContents: List<Content>? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var contentJsonFromRemote by PushStringPreference("n12121otificationContenwewetJsonRemote", "")

    /**
     * 初始化内容配置
     * @param context 上下文
     * @return 是否初始化成功
     */
    fun initialize(context: Context): Boolean {
        return try {
            sharedPreferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            val jsonString = contentJsonFromRemote.orEmpty().takeIf { it.isNotEmpty() }?:loadContentConfigFromAssets(context)
            pushContents = runCatching { parseContentConfig(jsonString) }
                .getOrElse { parseContentConfig(loadContentConfigFromAssets(context)) }
            Logger.d("推送内容配置初始化成功，共 ${pushContents?.size} 条")

            // 异步获取远程配置
            fetchRemoteContent()

            true
        } catch (e: Exception) {
            Logger.e("推送内容配置初始化失败", e)
            false
        }
    }

    /**
     * 获取下一个推送内容（顺序获取）
     * @return 推送内容，首次调用返回第一条，之后按顺序返回
     */
    @Synchronized fun getNextContent(): Content? {
        val contents = pushContents ?: return null
        if (contents.isEmpty()) return null

        val currentIndex = getCurrentIndex()
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % contents.size

        // 保存下一个索引
        saveCurrentIndex(nextIndex)

        val content = contents[nextIndex]
        Logger.d("获取推送内容: ${content.id}, 索引: $nextIndex")

        return content
    }

    /**
     * 检查是否已初始化
     * @return 是否已初始化
     */
    fun isInitialized(): Boolean {
        return pushContents != null
    }

    /**
     * 异步获取远程推送内容配置
     */
    private fun fetchRemoteContent() {
        io.docview.push.host.PushEnvironment.scope.launch {
            try {
                Logger.d("开始获取远程推送内容配置")
                val remoteJsonString = PushRemoteConfig.getString("pushContentJson", "")

                if (remoteJsonString != null && remoteJsonString.isNotEmpty()) {
                    Logger.d("成功获取远程推送内容配置")
                    val remoteContents = parseContentConfig(remoteJsonString)

                    // 更新本地配置
                    pushContents = remoteContents
                    contentJsonFromRemote = remoteJsonString
                    Logger.d("远程推送内容配置更新成功，共 ${remoteContents.size} 条")
                } else {
                    Logger.w("远程推送内容配置为空或获取超时，使用本地配置")
                }

            } catch (e: Exception) {
                Logger.e("获取远程推送内容配置异常", e)
            }
        }
    }

    /**
     * 从 assets 加载内容配置文件
     * @param context 上下文
     * @return JSON 字符串
     */
    private fun loadContentConfigFromAssets(context: Context): String {
        return try {
            context.assets.open(CONTENT_CONFIG_FILE_NAME).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Logger.e("加载推送内容配置文件失败", e)
            throw e
        }
    }

    /**
     * 解析内容配置 JSON
     * @param jsonString JSON 字符串
     * @return 内容列表
     */
    private fun parseContentConfig(jsonString: String): List<Content> = parsePushContents(jsonString)

    /**
     * 获取当前索引
     * @return 当前索引，首次调用返回-1
     */
    private fun getCurrentIndex(): Int {
        return sharedPreferences.getInt(KEY_CURRENT_INDEX, -1)
    }

    /**
     * 保存当前索引到 SharedPreferences
     * @param index 索引
     */
    private fun saveCurrentIndex(index: Int) {
        sharedPreferences.edit().putInt(KEY_CURRENT_INDEX, index).apply()
    }
}
