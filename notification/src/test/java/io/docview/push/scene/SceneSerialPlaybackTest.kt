package io.docview.push.scene

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SceneSerialPlaybackTest {
    private class Fixture(scope: TestScope, var loops: Int = 2) {
        val queue = ScenePresentationQueue()
        var configuration = SceneConfiguration(ScenePolicy(), PushScene.entries.associateWith {
            listOf(SceneCopy("first", "First body", "View"))
        }, SceneChoices())
        val invalid = mutableSetOf<PushScene>()
        val failedPublications = mutableSetOf<PushScene>()
        val sent = mutableListOf<Triple<PushScene, Long, String>>()
        val repeated = mutableListOf<Triple<PushScene, Long, Int>>()
        val history = mutableMapOf<PushScene, SceneHistory>()
        val repeat = SceneRepeatLoop(scope) { SceneRepeatPolicy(loops > 0, loops) }
        val dispatcher = SceneDispatcher(scope, {configuration}, object: SceneHistoryStore {
            override suspend fun history(scene: PushScene) = history[scene] ?: SceneHistory()
            override suspend fun succeeded(scene: PushScene, moment: SceneMoment, copyId: String) {
                history[scene] = SceneHistory(moment.wall, moment.elapsed, moment.boot, copyId)
            }
        }, {SceneMoment(1_000_000 + scope.testScheduler.currentTime, scope.testScheduler.currentTime, 1)}, {"en"}, {true},
            validate = {it.scene !in invalid}, presentation = queue,
            publish = {event,copy,lease,guard ->
                if (!guard() || event.scene in failedPublications) false else {
                    sent += Triple(event.scene, scope.testScheduler.currentTime, copy.text.text)
                    repeat.start(event.scene.notificationId, event.identity, finished = {lease?.release()}) {completed,current ->
                        if (current()) { repeated += Triple(event.scene,scope.testScheduler.currentTime,completed+1); true } else false
                    }
                    true
                }
            })
        fun offer(scene: PushScene, reference: String = "") = dispatcher.offer(SceneEvent(scene, 1, 0, reference))
        fun close() { queue.close(); dispatcher.close(); repeat.close() }
    }

    @Test fun firstPublishWaitsForWholePreviousCycleAndQueuedItemsDoNotCount() = runTest {
        val f = Fixture(this)
        f.offer(PushScene.CHARGE_START); f.offer(PushScene.NETWORK); f.offer(PushScene.DOWNLOAD)
        runCurrent(); assertEquals(listOf(PushScene.CHARGE_START), f.sent.map {it.first})
        assertEquals(setOf(PushScene.CHARGE_START), f.history.keys)
        advanceTimeBy(11_999); runCurrent(); assertEquals(1, f.sent.size)
        advanceTimeBy(1); runCurrent(); assertEquals(12_000L, f.sent[1].second)
        advanceTimeBy(12_000); runCurrent(); assertEquals(24_000L, f.sent[2].second)
        f.close()
    }

    @Test fun everySceneFinishesAllEightRepeatsBeforeNextSceneStarts() = runTest {
        val f = Fixture(this, loops = 8)
        PushScene.entries.forEach { f.offer(it) }
        runCurrent()
        for ((index,scene) in PushScene.entries.withIndex()) {
            assertEquals(index+1,f.sent.size)
            assertEquals(Triple(scene,index*36_000L,"First body"),f.sent.last())
            // 首条已发布后，原触发条件不再用于打断该轮；只影响尚未发布的候选。
            f.invalid += scene
            f.dispatcher.cancel(scene)
            for (iteration in 1..8) {
                advanceTimeBy(4_000); runCurrent()
                assertEquals(Triple(scene,index*36_000L+iteration*4_000L,iteration),f.repeated.last())
                assertEquals(index+1,f.sent.size)
            }
            advanceTimeBy(3_999); runCurrent(); assertEquals(index+1,f.sent.size)
            advanceTimeBy(1); runCurrent()
        }
        assertEquals(8,f.sent.size); assertEquals(64,f.repeated.size); assertEquals(8,f.history.size)
        advanceTimeBy(60_000); runCurrent(); assertEquals(64,f.repeated.size)
        f.close()
    }

    @Test fun queuedInvalidEventIsSkippedAndCurrentTextIsSelectedOnlyWhenDequeued() = runTest {
        val f = Fixture(this, loops = 1)
        f.offer(PushScene.CHARGE_START); f.offer(PushScene.NETWORK); f.offer(PushScene.DOWNLOAD)
        runCurrent(); f.invalid += PushScene.NETWORK
        f.configuration = f.configuration.copy(copies = f.configuration.copies +
            (PushScene.DOWNLOAD to listOf(SceneCopy("updated", "Updated body", "View"))))
        f.dispatcher.reconsider() // 配置刷新不能让已排队的合法候选抢跑。
        advanceTimeBy(8_000); runCurrent()
        assertEquals(listOf(PushScene.CHARGE_START, PushScene.DOWNLOAD), f.sent.map {it.first})
        assertEquals("Updated body", f.sent.last().third)
        assertFalse(PushScene.NETWORK in f.history); f.close()
    }

    @Test fun dismissAdvancesQueueImmediatelyAndDisabledLoopStillReservesSingleDisplayWindow() = runTest {
        val f = Fixture(this, loops = 0)
        f.offer(PushScene.CHARGE_START); f.offer(PushScene.NETWORK); f.offer(PushScene.DOWNLOAD)
        runCurrent(); advanceTimeBy(3_999); runCurrent(); assertEquals(1, f.sent.size)
        advanceTimeBy(1); runCurrent(); assertEquals(PushScene.NETWORK, f.sent.last().first)
        f.repeat.cancel(PushScene.NETWORK.notificationId); runCurrent()
        assertEquals(PushScene.DOWNLOAD, f.sent.last().first)
        assertEquals(4_000L, f.sent.last().second); f.close()
    }

    @Test fun queuedSceneOffOrServiceCloseCannotPublishOrConsumeHistory() = runTest {
        val f = Fixture(this)
        f.offer(PushScene.CHARGE_START); f.offer(PushScene.NETWORK); f.offer(PushScene.DOWNLOAD)
        runCurrent()
        f.configuration = f.configuration.copy(choices = SceneChoices(disabled = setOf(PushScene.NETWORK)))
        f.dispatcher.reconsider(); runCurrent()
        f.close(); advanceTimeBy(20_000); runCurrent()
        assertEquals(1, f.sent.size); assertEquals(setOf(PushScene.CHARGE_START), f.history.keys)
    }
    @Test fun failedFirstPublicationReleasesSlotWithoutRecordingHistory() = runTest {
        val f = Fixture(this)
        f.failedPublications += PushScene.CHARGE_START
        f.offer(PushScene.CHARGE_START); f.offer(PushScene.NETWORK)
        runCurrent()
        assertEquals(listOf(PushScene.NETWORK), f.sent.map {it.first})
        assertEquals(setOf(PushScene.NETWORK), f.history.keys); f.close()
    }
    @Test fun longerCooldownAtDequeueDoesNotHoldUpOtherEligibleScenes() = runTest {
        advanceTimeBy(600_000)
        val f = Fixture(this)
        f.history[PushScene.NETWORK] = SceneHistory(1_000_000,0,1,"old")
        f.offer(PushScene.CHARGE_START); f.offer(PushScene.NETWORK); f.offer(PushScene.DOWNLOAD)
        runCurrent()
        f.configuration = f.configuration.copy(policy = f.configuration.policy.copy(rules = f.configuration.policy.rules +
            (PushScene.NETWORK to SceneRule(intervalMinutes = 20))))
        f.dispatcher.reconsider(); advanceTimeBy(12_000); runCurrent()
        assertEquals(listOf(PushScene.CHARGE_START, PushScene.DOWNLOAD), f.sent.map {it.first})
        assertEquals(612_000L, f.sent.last().second)
        assertEquals("old", f.history.getValue(PushScene.NETWORK).copyId); f.close()
    }

    @Test fun recognizedScreenshotQueuesImmediatelyWithoutFiveMinuteDelay() = runTest {
        val f = Fixture(this, loops = 1)
        f.offer(PushScene.NETWORK); runCurrent()
        val media = screenshot()
        f.dispatcher.offer(ScreenshotSceneRules.onMediaChanged(listOf(media), SceneMoment(1_000_000,0,1))!!)
        runCurrent(); assertFalse(PushScene.SCREENSHOT in f.history)
        advanceTimeBy(8_000); runCurrent()
        assertEquals(listOf(PushScene.NETWORK,PushScene.SCREENSHOT),f.sent.map {it.first})
        assertEquals(8_000L,f.sent.last().second) // 网络轮结束即播放，没有额外 300000ms。
        f.close()
    }

    @Test fun sameScreenshotCallbacksUseOnlyRemoteIntervalAndDoNotRestartCooldown() = runTest {
        val f = Fixture(this, loops = 0)
        f.configuration = f.configuration.copy(policy = SceneConfig.policy("""{"screenshotPushIntervalMinutes":1}"""))
        val media = screenshot()
        fun recognize() = f.dispatcher.offer(ScreenshotSceneRules.onMediaChanged(listOf(media),
            SceneMoment(1_000_000+testScheduler.currentTime,testScheduler.currentTime,1))!!)
        recognize(); runCurrent(); assertEquals(0L,f.sent.single().second)
        advanceTimeBy(10_000); recognize(); runCurrent()
        advanceTimeBy(20_000); recognize(); runCurrent() // 同 URI 不在识别层过滤，也不从新回调时间重算间隔。
        advanceTimeBy(29_999); runCurrent(); assertEquals(1,f.sent.size)
        advanceTimeBy(1); runCurrent(); assertEquals(listOf(0L,60_000L),f.sent.map {it.second})
        f.close()
    }

    private fun screenshot() = SceneMedia("content://media/external/images/media/42", "shot.png",
        "Pictures/Screenshots/", "image/png", 100, 1000, 1000, false, false, "system.screenshot")
}
