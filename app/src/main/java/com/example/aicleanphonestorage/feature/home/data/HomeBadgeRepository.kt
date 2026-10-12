package com.example.aicleanphonestorage.feature.home.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.corekit.core.utils.ConfigRemoteManager

private val Context.homeBadgeDataStore by preferencesDataStore("home_badges")

/** 远程默认开关来源；生产实现只经 ConfigRemoteManager 读取，测试注入假实现。 */
interface HomeBadgeRemoteSource {
    /** 本进程 Remote Config 是否已拉取并激活成功。 */
    fun ready(): Boolean

    /** 读取 `home_badge_switch` 原始 JSON；可能挂起等待初始化（库内上限 30s），失败返回 null。 */
    suspend fun read(): String?
}

object FirebaseHomeBadgeRemoteSource : HomeBadgeRemoteSource {
    override fun ready(): Boolean = ConfigRemoteManager.isInitialized()

    override suspend fun read(): String? =
        // 首次 getValue 可能同步读取 Remote Config 磁盘缓存，必须离开主线程。
        withContext(Dispatchers.IO) {
            try {
                ConfigRemoteManager.getString(HomeBadgeSwitch.REMOTE_KEY, "")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Remote badge switch unavailable; using local defaults", error)
                null
            }
        }

    private const val TAG = "HomeBadges"
}

/**
 * 首页入口角标状态 = 远程默认开关 - 当日已真正进入的功能。
 *
 * - 日期使用设备本地时区的 epochDay；不设定时器，由首页 onResume 调用 [refreshDay]，
 *   跨过 0 点后旧记录自然失效，全部角标恢复到远程/本地默认值。
 * - “当日已进入”持久化到 DataStore，杀进程后仍保持隐藏；同时保留内存镜像，
 *   打开功能页时首页立即更新，不依赖异步写盘完成。
 * - 远程参数只在首页可见期间读取（随订阅取消），不在后台等待或轮询。
 */
class HomeBadgeRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val remote: HomeBadgeRemoteSource,
    // 应用级作用域只用于短小写盘：功能页打开后首页可能被系统回收，写入不能随页面取消。
    private val writeScope: CoroutineScope,
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
) {
    internal constructor(context: Context, writeScope: CoroutineScope) :
        this(context.applicationContext.homeBadgeDataStore, FirebaseHomeBadgeRemoteSource, writeScope)

    private val day = MutableStateFlow(today())
    private val recent = MutableStateFlow(HomeBadgeVisits())

    /** 首页 onResume 调用；日期未变化时 StateFlow 去重，不触发重绘。 */
    fun refreshDay() {
        day.value = today()
    }

    /** 功能页真正打开时调用（包括推送进入）。只记录当天，跨天写入会覆盖旧日期。 */
    fun markEntered(badge: HomeBadge) {
        val date = today()
        day.value = date
        recent.update { HomeBadgeVisits(date, it.enteredOn(date) + badge) }
        writeScope.launch {
            try {
                store.edit { values ->
                    val entered = if (values[DAY] == date) values[ENTERED].orEmpty() else emptySet()
                    values[DAY] = date
                    values[ENTERED] = entered + badge.key
                }
            } catch (error: IOException) {
                // 写盘失败仍保留内存镜像：本进程内保持隐藏，重启后最多重新显示当日角标。
                Log.w(TAG, "Unable to persist entered badge ${badge.key}", error)
            }
        }
    }

    fun observeVisible(): Flow<Set<HomeBadge>> =
        combine(day, recent, storedVisits(), remoteDisabled()) { date, memory, stored, disabled ->
            visibleHomeBadges(date, disabled, memory, stored)
        }.distinctUntilChanged()

    private fun storedVisits(): Flow<HomeBadgeVisits> =
        store.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { values ->
                HomeBadgeVisits(
                    values[DAY] ?: HomeBadgeVisits.NO_DAY,
                    values[ENTERED].orEmpty().mapNotNullTo(mutableSetOf(), HomeBadge::fromKey),
                )
            }

    private fun remoteDisabled(): Flow<Set<HomeBadge>> = flow {
        // 已就绪时直接读取（毫秒级），避免先按本地策略显示再被远程关闭造成闪烁；
        // 未就绪（首装弱网/拉取失败）先按本地策略全部显示，就绪后再以远程值覆盖。
        val ready = remote.ready()
        if (!ready) emit(emptySet())
        val raw = remote.read()
        if (raw != null) emit(HomeBadgeSwitch.disabled(raw)) else if (ready) emit(emptySet())
    }

    private companion object {
        const val TAG = "HomeBadges"
        val DAY = longPreferencesKey("entered_day")
        val ENTERED = stringSetPreferencesKey("entered_badges")
    }
}
