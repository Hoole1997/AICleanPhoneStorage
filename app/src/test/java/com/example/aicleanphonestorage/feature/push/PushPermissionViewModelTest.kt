package com.example.aicleanphonestorage.feature.push

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class PushPermissionViewModelTest {
    @Test
    fun firstVisitRequestsSystemPermissionWithoutShowingGuide() {
        val model = PushPermissionViewModel(SavedStateHandle())
        model.onForeground(false)
        assertFalse(model.state.value.guideVisible)
        assertEquals(PushPermissionRequest.AUTOMATIC, model.takeRequest())
        model.onForeground(false)
        assertNull(model.takeRequest())
        model.onResult(granted = true, denied = false)
        assertFalse(model.state.value.guideVisible)
    }

    @Test
    fun systemDenialDoesNotCreateGuideInTheSameHost() {
        val model = PushPermissionViewModel(SavedStateHandle())
        model.onForeground(false)
        model.takeRequest()
        model.onResult(granted = false, denied = false)
        assertFalse(model.state.value.guideVisible)
        model.onResult(granted = false, denied = true)
        assertFalse(model.state.value.guideVisible)
        assertTrue(model.state.value.completed)
    }

    @Test
    fun dismissingGuideDoesNotLoopOnResumeOrRecreation() {
        val saved = SavedStateHandle()
        val model = PushPermissionViewModel(saved)
        model.onForeground(false)
        model.takeRequest()
        model.onResult(false, true)
        model.guideShown()
        model.guideAction(false)
        repeat(3) { model.onForeground(false) }
        assertNull(model.state.value.request)
        assertFalse(model.state.value.guideVisible)
        val restored = PushPermissionViewModel(saved)
        restored.onForeground(false)
        assertNull(restored.state.value.request)
        assertFalse(restored.state.value.guideVisible)
    }

    @Test
    fun allowRetriesOnceAndSecondDenialDoesNotOpenAnotherGuide() {
        val model = PushPermissionViewModel(SavedStateHandle())
        model.onForeground(false)
        model.takeRequest()
        model.onResult(false, true)
        model.guideShown()
        model.guideAction(true)
        assertEquals(PushPermissionRequest.GUIDE, model.takeRequest())
        model.onForeground(false)
        assertNull(model.takeRequest())
        model.onResult(false, true)
        assertFalse(model.state.value.guideVisible)
    }

    @Test
    fun pendingDenialSurvivesRecreationButGrantDismissesIt() {
        val saved = SavedStateHandle()
        val model = PushPermissionViewModel(saved)
        model.onForeground(false)
        model.takeRequest()
        model.onForeground(false, canRequestSystem = false)
        model.completeFromPreviousHost()
        model.onForeground(false, canRequestSystem = false)
        val restored = PushPermissionViewModel(saved)
        assertTrue(restored.state.value.guideVisible)
        restored.onForeground(true)
        assertFalse(restored.state.value.guideVisible)
        assertNull(restored.takeRequest())
    }

    @Test
    fun twoSystemAttemptsThenOnlyHomeGuide() {
        val startup = PushPermissionViewModel(SavedStateHandle())
        startup.onForeground(false, canRequestSystem = true, allowGuide = false)
        assertEquals(PushPermissionRequest.AUTOMATIC, startup.takeRequest())
        startup.onResult(false, true)
        assertTrue(startup.state.value.completed)
        assertFalse(startup.state.value.guideVisible)
        val home = PushPermissionViewModel(SavedStateHandle())
        home.completeFromPreviousHost()
        home.onForeground(false, canRequestSystem = true)
        assertEquals(PushPermissionRequest.AUTOMATIC, home.takeRequest())
        home.onResult(false, true)
        assertFalse(home.state.value.guideVisible)
        val nextStartup = PushPermissionViewModel(SavedStateHandle())
        nextStartup.onForeground(false, canRequestSystem = false, allowGuide = false)
        assertTrue(nextStartup.state.value.completed)
        assertFalse(nextStartup.state.value.guideVisible)
        home.completeFromPreviousHost()
        home.onForeground(false, canRequestSystem = false)
        assertTrue(home.state.value.guideVisible)
        home.guideAction(true)
        assertEquals(PushPermissionRequest.GUIDE, home.takeRequest())
        home.onResult(true, false)
        assertTrue(home.state.value.completed)
        assertFalse(home.state.value.guideVisible)
    }

    @Test
    fun lostRuntimeCallbackAfterProcessRestoreCannotBlockStartupForever() {
        val model = PushPermissionViewModel(SavedStateHandle(mapOf("push.auto.attempted" to true)))
        model.onForeground(false)
        assertTrue(model.state.value.completed)
        assertFalse(model.state.value.guideVisible)
        assertNull(model.takeRequest())
    }

    @Test
    fun restoredSettingsRequestStillWaitsForTheSharedResult() {
        val model = PushPermissionViewModel(SavedStateHandle(mapOf("push.auto.attempted" to true)))
        model.onForeground(false, settingsPending = true)
        assertFalse(model.state.value.completed)
        model.onResult(false, false)
        assertTrue(model.state.value.completed)
    }
    @Test fun queuedRequestSurvivesProcessRestoreAndStillBlocksTheAdStep() {
        val saved = SavedStateHandle()
        val first = PushPermissionViewModel(saved)
        first.onForeground(false)
        val restored = PushPermissionViewModel(saved)
        restored.onForeground(false)
        assertFalse(restored.state.value.completed)
        assertEquals(PushPermissionRequest.AUTOMATIC, restored.takeRequest())
    }

}
