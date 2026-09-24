package io.docview.push.config

import android.content.Context
import io.docview.push.host.PushEnvironment
import io.docview.push.host.PushRemoteConfig
import io.docview.push.host.PushUserChannel
import io.docview.push.utils.Logger
import java.time.LocalDate

/** 普通通知文案池：IO 初始化与轮播，发送条件/频次仍由原有 CheckCtrl、TimingCtrl 管理。 */
object ContentController {
    private var storage: DayContentStorage? = null
    private var rotation: DayContentRotation? = null
    private var pools: Map<Int, DayContentPool> = emptyMap()
    @Volatile private var catalog: DayContentCatalog? = null

    /** 日龄仍从首次启动计算；这里只保留日期/游标元数据，不加载或启用分池。 */
    @Synchronized fun recordFirstLaunch(context: Context, firstObservedEpochDay: Long) {
        try {
            if (storage == null) storage = DayContentStorage(context.applicationContext, firstObservedEpochDay)
        } catch (error: Exception) {
            // 日池存储不可用不能阻断自然用户的常驻通知初始化。
            Logger.e("[推送日池] 首启日保存失败：${error.javaClass.simpleName}")
        }
    }

    /** 由 NotificationRuntime 的同一 IO 初始化任务调用；远程激活后复用游标、更新池快照。 */
    @Synchronized fun initialize(context: Context, firstObservedEpochDay: Long = LocalDate.now().toEpochDay()): Boolean {
        if (!PushUserChannel.isConfirmedPaidUser()) return false
        return try {
            val disk = storage ?: DayContentStorage(context.applicationContext, firstObservedEpochDay).also { storage = it }
            if (rotation == null) rotation = DayContentRotation(disk.firstLaunchEpochDay, disk::readIndex, disk::writeIndex, log = { Logger.d(it) })
            val today = LocalDate.now().toEpochDay()
            Logger.d("[推送日池] 日龄基准：首次启动日期=${LocalDate.ofEpochDay(disk.firstLaunchEpochDay)}，" +
                "当前日期=${LocalDate.ofEpochDay(today)}，实际日龄=${(today - disk.firstLaunchEpochDay).coerceAtLeast(0) + 1}，按自然日计算")
            val next = DayContentPool.days.associateWith { day ->
                resolveDayContentPool(
                    day = day,
                    remoteJson = PushRemoteConfig.getString(DayContentPool.key(day), "", MAX_DAY_POOL_CHARS),
                    current = pools[day],
                    cached = { disk.cached(day) },
                    local = { context.assets.open("${DayContentPool.key(day)}.json").bufferedReader().use { it.readText() } },
                    cacheRemote = { json ->
                        runCatching { disk.cache(day, json) }.onFailure { Logger.e("[推送日池] 缓存写入失败：日池=D$day，异常类型=${it.javaClass.simpleName}，本次仍使用有效远程文案") }
                    },
                    log = { Logger.d(it) },
                )
            }
            // IO 初始化期间归因可能改变；自然用户不能因为迟到的初始化结果重新启用分池。
            if (!PushUserChannel.isConfirmedPaidUser()) return false
            pools = next
            catalog = DayContentCatalog(next)
            Logger.d("[推送日池] 初始化完成：D1–D5 已就绪，D6+ 按 D1→D5 合并，共 ${next.values.sumOf { it.contents.size }} 条")
            true
        } catch (error: Exception) {
            Logger.e("[推送日池] 初始化失败：异常类型=${error.javaClass.simpleName}，${if (catalog != null) "保留已有有效文案池" else "文案池尚不可用"}")
            catalog != null
        }
    }

    /** 每次取用重新检查自然日和 APP 语言；语言切换不清空游标或新增轮询。 */
    @Synchronized fun getNextContent(): Content? {
        // 最终取用也检查，拦截直接调用并确保自然用户不会推进轮播游标。
        if (!PushUserChannel.isConfirmedPaidUser()) return null
        val current = catalog ?: run {
            Logger.w("[推送日池] 暂不取用文案：文案池尚未初始化完成")
            return null
        }
        return rotation?.next(current, LocalDate.now().toEpochDay(), PushEnvironment.host.contentLanguageTag)
    }

    fun isInitialized(): Boolean = PushUserChannel.isConfirmedPaidUser() && catalog != null

}
