package com.example.aicleanphonestorage.feature.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.home.data.HomeBadge
import com.example.aicleanphonestorage.feature.home.data.HomeBadgeRemoteSource
import com.example.aicleanphonestorage.feature.home.data.HomeBadgeRepository
import com.example.aicleanphonestorage.feature.home.data.HomeBadgeSwitch
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeBadgeTest {
    private val folder: File = Files.createTempDirectory("home-badges").toFile()
    private val all = HomeBadge.entries.toSet()

    @After fun cleanUp() {
        folder.deleteRecursively()
    }

    private class FakeRemote(private val ready: Boolean = true, private val json: String? = "") : HomeBadgeRemoteSource {
        val gate = CompletableDeferred(Unit)
        override fun ready() = ready
        override suspend fun read(): String? {
            gate.await()
            return json
        }
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
    fun `missing invalid or non-boolean remote fields fall back to showing`() {
        assertEquals(emptySet<HomeBadge>(), HomeBadgeSwitch.disabled(null))
        assertEquals(emptySet<HomeBadge>(), HomeBadgeSwitch.disabled(""))
        assertEquals(emptySet<HomeBadge>(), HomeBadgeSwitch.disabled("not json"))
        assertEquals(emptySet<HomeBadge>(), HomeBadgeSwitch.disabled("[false]"))
        assertEquals(emptySet<HomeBadge>(), HomeBadgeSwitch.disabled("""{"videos":0,"smart_clean":null}"""))
        assertEquals(emptySet<HomeBadge>(), HomeBadgeSwitch.disabled("{\"videos\":false" + " ".repeat(5000) + "}"))
        assertEquals(
            setOf(HomeBadge.Videos, HomeBadge.MalwareScan),
            HomeBadgeSwitch.disabled("""{"videos":false,"malware_scan":"false","smart_clean":true,"unknown":false}"""),
        )
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
    fun `remote switch hides defaults and entered state still applies`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeRemote(json = """{"network_traffic":false,"videos":false}""")
        val repo = AppProcess(backgroundScope, remote) { 1L }.badges
        assertEquals(all - HomeBadge.NetworkTraffic - HomeBadge.Videos, repo.observeVisible().first())
        repo.markEntered(HomeBadge.SimilarPhotos)
        assertEquals(
            all - HomeBadge.NetworkTraffic - HomeBadge.Videos - HomeBadge.SimilarPhotos,
            repo.observeVisible().first(),
        )
    }

    @Test
    fun `remote not ready shows local defaults first then applies remote value`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeRemote(ready = false, json = """{"smart_clean":false}""")
        val pending = CompletableDeferred<Unit>()
        val gated = object : HomeBadgeRemoteSource by remote {
            override suspend fun read(): String? { pending.await(); return remote.read() }
        }
        val repo = AppProcess(backgroundScope, gated) { 1L }.badges
        val states = mutableListOf<Set<HomeBadge>>()
        val job = launch { repo.observeVisible().take(2).toList(states) }
        assertEquals(listOf(all), states)
        pending.complete(Unit)
        job.join()
        assertEquals(listOf(all, all - HomeBadge.SmartClean), states)
    }

    @Test
    fun `remote initialization failure keeps local defaults`() = runTest(UnconfinedTestDispatcher()) {
        val unavailable = AppProcess(backgroundScope, FakeRemote(ready = false, json = null), "a.preferences_pb") { 1L }
        assertEquals(all, unavailable.badges.observeVisible().first())
        val failedRead = AppProcess(backgroundScope, FakeRemote(ready = true, json = null), "b.preferences_pb") { 1L }
        assertEquals(all, failedRead.badges.observeVisible().first())
    }
}
