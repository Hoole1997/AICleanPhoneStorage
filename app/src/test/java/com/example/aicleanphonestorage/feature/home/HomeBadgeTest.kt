package com.example.aicleanphonestorage.feature.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.home.data.HomeBadge
import com.example.aicleanphonestorage.feature.home.data.FirebaseHomeBadgeRemoteSource
import com.example.aicleanphonestorage.feature.home.data.HomeBadgeRemoteSource
import com.example.aicleanphonestorage.feature.home.data.HomeBadgeVisits
import com.example.aicleanphonestorage.feature.home.data.visibleHomeBadges
import com.example.aicleanphonestorage.feature.home.data.HomeBadgeRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeBadgeTest {
    private val folder: File = Files.createTempDirectory("home-badges").toFile()
    private val all = HomeBadge.entries.toSet()

    @After fun cleanUp() {
        folder.deleteRecursively()
    }

    /** [value] 为 null 表示参数未下发/读取失败，按默认值 true 处理。 */
    private class FakeRemote(private val ready: Boolean = true, private val value: Boolean? = null) : HomeBadgeRemoteSource {
        override fun ready() = ready
        override suspend fun enabled(): Boolean? = value
    }

    /** 一个实例代表一次进程生命周期；同一文件上先 [kill] 再新建，模拟杀进程后重新进入。 */
    private inner class AppProcess(
        scope: CoroutineScope,
        remote: HomeBadgeRemoteSource = FakeRemote(),
        file: String = "home_badges.preferences_pb",
        today: () -> Long,
    ) {
        private val storeJob = Job()
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(scope.coroutineContext + storeJob)) { File(folder, file) }
        val badges = HomeBadgeRepository(store, remote, scope, today)

        /** 等待异步写盘落地，再结束本“进程”。 */
        suspend fun killAfterPersisting(badge: HomeBadge) {
            store.data.first { values -> values.asMap().values.any { (it as? Set<*>)?.contains(badge.key) == true } }
            storeJob.cancel()
        }
    }

    @Test
    fun `unified switch defaults to showing and false hides every badge`() {
        val entered = HomeBadgeVisits(5, setOf(HomeBadge.Videos))
        assertEquals(all - HomeBadge.Videos, visibleHomeBadges(5, true, entered))
        assertEquals(emptySet<HomeBadge>(), visibleHomeBadges(5, false, entered))
        // 跨天后记录失效，开关开启时全部恢复。
        assertEquals(all, visibleHomeBadges(6, true, entered))
        assertEquals("home_badge_switch", FirebaseHomeBadgeRemoteSource.REMOTE_KEY)
        assertTrue(FirebaseHomeBadgeRemoteSource.DEFAULT_ENABLED)
    }

    @Test
    fun `remote keys are unique and every cleanup feature maps to its own badge`() {
        assertEquals(HomeBadge.entries.size, HomeBadge.entries.map { it.key }.toSet().size)
        HomeBadge.entries.forEach { assertEquals(it, HomeBadge.fromKey(it.key)) }
        assertEquals(CleanupFeature.entries.size, CleanupFeature.entries.map(HomeBadge::of).toSet().size)
        assertEquals(HomeBadge.SmartClean, HomeBadge.of(CleanupFeature.SMART_CLEAN))
    }

    @Test
    fun `entered badge hides for the rest of the day and survives process restart`() = runTest(UnconfinedTestDispatcher()) {
        var day = 20_000L
        val first = AppProcess(backgroundScope) { day }
        assertEquals(all, first.badges.observeVisible().first())

        first.badges.markEntered(HomeBadge.LargeFiles)
        // 内存镜像立即生效，不等待写盘。
        assertFalse(HomeBadge.LargeFiles in first.badges.observeVisible().first())
        first.killAfterPersisting(HomeBadge.LargeFiles)

        val restarted = AppProcess(backgroundScope) { day }
        assertEquals(all - HomeBadge.LargeFiles, restarted.badges.observeVisible().first())

        day++
        restarted.badges.refreshDay()
        assertEquals(all, restarted.badges.observeVisible().first())

        // 跨天后的写入覆盖旧日期，只保留当天进入的功能。
        restarted.badges.markEntered(HomeBadge.MalwareScan)
        restarted.killAfterPersisting(HomeBadge.MalwareScan)
        assertEquals(all - HomeBadge.MalwareScan, AppProcess(backgroundScope) { day }.badges.observeVisible().first())
    }

    @Test
    fun `foreground midnight is applied by refreshDay without polling`() = runTest(UnconfinedTestDispatcher()) {
        var day = 30_000L
        val repo = AppProcess(backgroundScope) { day }.badges
        repo.markEntered(HomeBadge.SmartClean)
        repo.markEntered(HomeBadge.BatteryInfo)
        val states = mutableListOf<Set<HomeBadge>>()
        val job = launch { repo.observeVisible().collect { states += it } }
        assertEquals(all - HomeBadge.SmartClean - HomeBadge.BatteryInfo, states.last())

        day++ // 时间已过 0 点但首页尚未 onResume：不轮询，状态保持。
        assertEquals(all - HomeBadge.SmartClean - HomeBadge.BatteryInfo, states.last())
        repo.refreshDay()
        assertEquals(all, states.last())
        job.cancel()
    }

    @Test
    fun `remote switch off hides all badges and on keeps the entered rule`() = runTest(UnconfinedTestDispatcher()) {
        val off = AppProcess(backgroundScope, FakeRemote(value = false), "off.preferences_pb") { 1L }.badges
        assertEquals(emptySet<HomeBadge>(), off.observeVisible().first())
        val on = AppProcess(backgroundScope, FakeRemote(value = true), "on.preferences_pb") { 1L }.badges
        on.markEntered(HomeBadge.SimilarPhotos)
        assertEquals(all - HomeBadge.SimilarPhotos, on.observeVisible().first())
    }

    @Test
    fun `remote not ready shows local defaults first then applies remote value`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeRemote(ready = false, value = false)
        val pending = CompletableDeferred<Unit>()
        val gated = object : HomeBadgeRemoteSource by remote {
            override suspend fun enabled(): Boolean? { pending.await(); return remote.enabled() }
        }
        val repo = AppProcess(backgroundScope, gated) { 1L }.badges
        val states = mutableListOf<Set<HomeBadge>>()
        val job = launch { repo.observeVisible().take(2).toList(states) }
        assertEquals(listOf(all), states)
        pending.complete(Unit)
        job.join()
        assertEquals(listOf(all, emptySet()), states)
    }

    @Test
    fun `remote initialization failure keeps local defaults`() = runTest(UnconfinedTestDispatcher()) {
        val unavailable = AppProcess(backgroundScope, FakeRemote(ready = false, value = null), "a.preferences_pb") { 1L }
        assertEquals(all, unavailable.badges.observeVisible().first())
        val failedRead = AppProcess(backgroundScope, FakeRemote(ready = true, value = null), "b.preferences_pb") { 1L }
        assertEquals(all, failedRead.badges.observeVisible().first())
    }
}
