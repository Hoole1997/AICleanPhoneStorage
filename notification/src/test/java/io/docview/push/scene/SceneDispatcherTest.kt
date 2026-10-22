package io.docview.push.scene

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SceneDispatcherTest {
    private class History : SceneHistoryStore {
        val values=mutableMapOf<PushScene,SceneHistory>()
        var writeDelay=0L
        override suspend fun history(scene:PushScene)=values[scene]?:SceneHistory()
        override suspend fun succeeded(scene:PushScene,moment:SceneMoment,copyId:String) {
            delay(writeDelay)
            values[scene]=SceneHistory(moment.wall,moment.elapsed,moment.boot,copyId)
        }
    }
    private fun configuration(interval:Int=10)=SceneConfiguration(ScenePolicy(rules=PushScene.entries.associateWith { SceneRule(intervalMinutes=interval) }),
        PushScene.entries.associateWith { listOf(SceneCopy("one","One Body","View"),SceneCopy("two","Two Body","View"),SceneCopy("three","Three Body","View")) },SceneChoices())

    @Test fun independentPoliciesHaveNoSharedQuotaOrCooldownWithoutPresentationGate()=runTest {
        val history=History();val sent=mutableListOf<Pair<PushScene,String>>()
        val cfg=configuration()
        val d=SceneDispatcher(this,{cfg},history,{SceneMoment(1_000_000+testScheduler.currentTime,testScheduler.currentTime,1)},{"en"},{true},
            validate={true},publish={event,copy,_,guard->if(guard()){sent+=event.scene to copy.id;true}else false})
        PushScene.entries.forEach { d.offer(SceneEvent(it,0,0)) };runCurrent()
        assertEquals(8,sent.size);assertEquals(8,history.values.size)
        PushScene.entries.forEach { d.offer(SceneEvent(it,1,0)) };runCurrent()
        assertEquals(8,sent.size)
        advanceTimeBy(600_000);runCurrent()
        assertEquals(16,sent.size);assertTrue(sent.takeLast(8).all { it.second=="two" })
        d.close()
    }
    @Test fun failedOrForegroundOrInvalidEventsNeverAdvanceHistory()=runTest {
        val history=History();var send=false;var background=true;var valid=true;var hints=0
        val d=SceneDispatcher(this,{configuration()},history,{SceneMoment(1000,testScheduler.currentTime,1)},{"en"},{background},
            validate={valid},publish={_,_,_,_->send},foregroundHint={_,_->hints++})
        val event=SceneEvent(PushScene.APP_INSTALL,0,0)
        d.offer(event);runCurrent();assertTrue(history.values.isEmpty())
        background=false;d.offer(event);runCurrent();assertEquals(1,hints);assertTrue(history.values.isEmpty())
        background=true;send=true;valid=false;d.offer(event);runCurrent();assertTrue(history.values.isEmpty())
        valid=true;d.offer(event);runCurrent();assertEquals("one",history.values[event.scene]?.copyId)
        d.close()
    }
    @Test fun replacementCancelAndServiceClosePreventStalePublication()=runTest {
        val history=History();val sent=mutableListOf<String>()
        val d=SceneDispatcher(this,{configuration()},history,{SceneMoment(1000,testScheduler.currentTime,1)},{"en"},{true},
            validate={true},publish={e,_,_,guard->if(guard()){sent+=e.reference;true}else false})
        d.offer(SceneEvent(PushScene.SCREENSHOT,0,5000,"old"))
        d.offer(SceneEvent(PushScene.SCREENSHOT,0,6000,"new"))
        advanceTimeBy(5000);runCurrent();assertTrue(sent.isEmpty())
        advanceTimeBy(1000);runCurrent();assertEquals(listOf("new"),sent)
        d.offer(SceneEvent(PushScene.DOWNLOAD,0,7000,"cancelled"));d.cancel(PushScene.DOWNLOAD)
        d.offer(SceneEvent(PushScene.APP_INSTALL,0,8000,"closed"));d.close()
        advanceTimeBy(20_000);runCurrent();assertEquals(listOf("new"),sent)
    }
    @Test fun changedIntervalReusesLastSuccessAndDisabledSceneDiscardsPendingTask()=runTest {
        var cfg=configuration();val history=History();var count=0
        val d=SceneDispatcher(this,{cfg},history,{SceneMoment(1000+testScheduler.currentTime,testScheduler.currentTime,1)},{"en"},{true},
            validate={true},publish={_,_,_,guard->guard().also{if(it)count++}})
        val event=SceneEvent(PushScene.APP_INSTALL,0,0)
        d.offer(event);runCurrent();d.offer(event);runCurrent()
        advanceTimeBy(30_000);cfg=configuration(1);d.reconsider();runCurrent()
        advanceTimeBy(29_999);runCurrent();assertEquals(1,count)
        advanceTimeBy(1);runCurrent();assertEquals(2,count)
        d.offer(event);runCurrent();cfg=cfg.copy(choices=SceneChoices(enabled=false));d.reconsider()
        advanceTimeBy(120_000);runCurrent();assertEquals(2,count)
        cfg=configuration(1);d.reconsider();runCurrent();assertEquals(2,count)
        d.close()
    }
    @Test fun noDuplicateCanSlipInWhileSuccessfulTimestampIsBeingWritten()=runTest {
        val history=History().apply{writeDelay=5000};var count=0
        val d=SceneDispatcher(this,{configuration()},history,{SceneMoment(1000+testScheduler.currentTime,testScheduler.currentTime,1)},{"en"},{true},
            validate={true},publish={_,_,_,guard->guard().also{if(it)count++}})
        val event=SceneEvent(PushScene.APP_INSTALL,0,0)
        d.offer(event);runCurrent();assertEquals(1,count)
        d.offer(event);runCurrent();assertEquals(1,count)
        advanceTimeBy(5000);runCurrent();assertEquals(1,count)
        d.close()
    }
    @Test fun switchChangedWhileValidationSuspendsBlocksPublish()=runTest {
        var cfg=configuration();val validation=CompletableDeferred<Boolean>();var count=0
        val d=SceneDispatcher(this,{cfg},History(),{SceneMoment(1000,testScheduler.currentTime,1)},{"en"},{true},
            validate={validation.await()},publish={_,_,_,_->count++;true})
        d.offer(SceneEvent(PushScene.APP_UNINSTALL,0,0));runCurrent()
        cfg=cfg.copy(choices=SceneChoices(disabled=setOf(PushScene.APP_UNINSTALL)))
        validation.complete(true);runCurrent();assertEquals(0,count)
        d.close()
    }
    @Test fun latestIntervalWinsEvenBeforeConfigObserverReschedulesTheJob()=runTest {
        var cfg=configuration(1)
        val history=History().apply {values[PushScene.APP_INSTALL]=SceneHistory(1_000_000,0,1,"one")}
        val validation=CompletableDeferred<Boolean>();var count=0
        val d=SceneDispatcher(this,{cfg},history,{SceneMoment(1_120_000+testScheduler.currentTime,120_000+testScheduler.currentTime,1)},{"en"},{true},
            validate={validation.await()},publish={_,_,_,guard->guard().also{if(it)count++}})
        d.offer(SceneEvent(PushScene.APP_INSTALL,0,0));runCurrent()
        cfg=configuration(10);validation.complete(true);runCurrent()
        assertEquals(0,count)
        advanceTimeBy(479_999);runCurrent();assertEquals(0,count)
        advanceTimeBy(1);runCurrent();assertEquals(1,count)
        d.close()
    }
}
