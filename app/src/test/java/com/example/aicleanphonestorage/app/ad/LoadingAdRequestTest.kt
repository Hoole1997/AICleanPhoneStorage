package com.example.aicleanphonestorage.app.ad

import org.junit.Assert.*
import org.junit.Test

class LoadingAdRequestTest {
    @Test fun closeContinuesImmediatelyAndIgnoresReentrantAndLateResults() {
        val events = mutableListOf<String>()
        lateinit var request: LoadingAdRequest
        request = LoadingAdRequest(
            cancelWork = { events += "cancel"; request.complete(true) },
            hideLoading = { events += "hide" },
            release = { events += "release" },
            onResult = { events += "result:$it" },
        )
        request.cancelLoading()
        request.cancelLoading()
        request.complete(true)
        assertEquals(listOf("cancel", "hide", "release", "result:false"), events)
    }

    @Test fun sdkCompletionDoesNotCancelOrHideAnotherLoadingWindow() {
        val results = mutableListOf<Boolean>()
        var released = 0
        val request = LoadingAdRequest({ fail("Must not cancel") }, { fail("Must not hide") }, { released++ }, results::add)
        request.complete(true)
        request.cancelLoading()
        request.complete(false)
        assertEquals(listOf(true), results)
        assertEquals(1, released)
    }

    @Test fun hideFailureStillReleasesAndContinuesOnlyOnce() {
        var released = 0
        val results = mutableListOf<Boolean>()
        val request = LoadingAdRequest({}, { throw IllegalStateException("window gone") }, { released++ }, results::add)
        assertThrows(IllegalStateException::class.java) { request.cancelLoading() }
        request.complete(true)
        assertEquals(listOf(false), results)
        assertEquals(1, released)
    }
}
