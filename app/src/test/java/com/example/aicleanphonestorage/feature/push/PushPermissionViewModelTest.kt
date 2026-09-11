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
    fun onlyDeniedResultCanCreateTheGuide() {
        val model = PushPermissionViewModel(SavedStateHandle())
        model.onForeground(false)
        model.takeRequest()
        model.onResult(granted = false, denied = false)
        assertFalse(model.state.value.guideVisible)
        model.onResult(granted = false, denied = true)
        assertTrue(model.state.value.guideVisible)
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
        model.onResult(false, true)
        val restored = PushPermissionViewModel(saved)
        assertTrue(restored.state.value.guideVisible)
        restored.onForeground(true)
        assertFalse(restored.state.value.guideVisible)
        assertNull(restored.takeRequest())
    }

    @Test
    fun completionWaitsForDenialGuideAndThenUnblocksNextStep() {
        val model = PushPermissionViewModel(SavedStateHandle())
        assertFalse(model.state.value.completed)
        model.onForeground(false)
        model.takeRequest()
        model.onResult(false, true)
        assertFalse(model.state.value.completed)
        model.guideShown()
        model.guideAction(true)
        assertFalse(model.state.value.completed)
        model.takeRequest()
        model.onResult(false, false)
        assertTrue(model.state.value.completed)
    }

    @Test
    fun previousActivityCompletionSkipsDuplicateRequestWithoutGrantingPermission() {
        val model = PushPermissionViewModel(SavedStateHandle())
        model.completeFromPreviousHost()
        model.onForeground(false)
        assertNull(model.takeRequest())
        assertFalse(model.state.value.guideVisible)
        assertTrue(model.state.value.completed)
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
