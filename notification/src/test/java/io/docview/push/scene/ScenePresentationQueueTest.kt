package io.docview.push.scene

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScenePresentationQueueTest {
    @Test fun fifoAndOldLeaseCannotReleaseNewOwner() = runTest {
        val queue = ScenePresentationQueue()
        val first = queue.acquire(PushScene.CHARGE_START)!!
        val second = async { queue.acquire(PushScene.NETWORK) }
        val third = async { queue.acquire(PushScene.DOWNLOAD) }
        runCurrent(); assertFalse(second.isCompleted); assertFalse(third.isCompleted)
        first.release(); runCurrent()
        val next = second.await()!!
        first.release(); assertTrue(next.isCurrent); assertFalse(third.isCompleted)
        next.release(); runCurrent(); third.await()!!.release(); queue.close()
    }

    @Test fun cancellingWaitingOrJustPromotedWorkNeverBlocksQueue() = runTest {
        val queue = ScenePresentationQueue()
        val first = queue.acquire(PushScene.CHARGE_START)!!
        val cancelled = async { queue.acquire(PushScene.NETWORK) }
        val promoted = async { queue.acquire(PushScene.DOWNLOAD) }
        val last = async { queue.acquire(PushScene.SCREENSHOT) }
        runCurrent(); cancelled.cancel(); runCurrent()
        first.release(); promoted.cancel() // 展示位已分配、等待协程尚未恢复的取消竞态。
        runCurrent(); assertTrue(last.await()!!.isCurrent); queue.close()
    }

    @Test fun sameSceneHasOnlyOneWaitingCandidateAndKeepsItsSlot() = runTest {
        val queue = ScenePresentationQueue()
        val first = queue.acquire(PushScene.CHARGE_START)!!
        val old = async { queue.acquire(PushScene.NETWORK) }
        val last = async { queue.acquire(PushScene.DOWNLOAD) }
        runCurrent()
        val replacement = async { queue.acquire(PushScene.NETWORK) }
        runCurrent(); assertNull(old.await())
        first.release(); runCurrent(); assertFalse(last.isCompleted)
        replacement.await()!!.release(); runCurrent(); last.await()!!.release(); queue.close()
    }

    @Test fun foregroundClearDropsWaitingCandidatesAndCloseRejectsNewWork() = runTest {
        val queue = ScenePresentationQueue()
        val first = queue.acquire(PushScene.CHARGE_START)!!
        val next = async { queue.acquire(PushScene.NETWORK) }
        runCurrent(); queue.clear(); runCurrent()
        assertNull(next.await()); assertFalse(first.isCurrent)
        val fresh = queue.acquire(PushScene.DOWNLOAD)!!
        first.release(); assertTrue(fresh.isCurrent)
        queue.close(); assertFalse(fresh.isCurrent); assertNull(queue.acquire(PushScene.SCREENSHOT))
    }
    @Test fun clearAfterPromotionBeforeResumeDoesNotReturnAnExpiredLease() = runTest {
        val queue = ScenePresentationQueue()
        val first = queue.acquire(PushScene.CHARGE_START)!!
        val next = async { queue.acquire(PushScene.NETWORK) }
        runCurrent(); first.release(); queue.clear(); runCurrent()
        assertNull(next.await()); queue.close()
    }
}
