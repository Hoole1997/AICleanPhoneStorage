package com.example.aicleanphonestorage.core.data.apps

import com.example.aicleanphonestorage.feature.home.data.HomeToolMetric
import com.example.aicleanphonestorage.feature.home.data.withSharedAppCount
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstalledAppCountRepositoryTest {
    @Test fun simultaneousHomeAndResidentRefreshShareOneQuery() = runTest {
        var queries = 0
        val result = CompletableDeferred<Int>()
        val repository = InstalledAppCountRepository { queries++; result.await() }
        assertNull(repository.count.value)
        val home = async { repository.refresh() }
        val resident = async { repository.refresh() }
        runCurrent()
        assertEquals(1, queries)
        result.complete(42)
        assertEquals(42, home.await())
        assertEquals(42, resident.await())
        assertEquals(42, repository.count.value)
        assertEquals(1, queries)
    }

    @Test fun refreshUpdatesBothConsumersWithoutSeparateCaches() = runTest {
        var value = 12
        val repository = InstalledAppCountRepository { value }
        repository.refresh()
        val previousHome = HomeToolMetric.AppCount(12)
        value = 13
        repository.refresh()
        assertEquals(HomeToolMetric.AppCount(13), previousHome.withSharedAppCount(repository.count.value))
        assertEquals(13, repository.count.value)
        value = 11
        repository.refresh()
        assertEquals(HomeToolMetric.AppCount(11), previousHome.withSharedAppCount(repository.count.value))
    }

    @Test fun failureClearsUnknownCountInsteadOfPublishingZero() = runTest {
        var failRead = false
        val repository = InstalledAppCountRepository { if (failRead) throw IOException("unavailable") else 0 }
        assertEquals(0, repository.refresh())
        assertEquals(0, repository.count.value)
        failRead = true
        try { repository.refresh(); fail("Expected read failure") } catch (_: IOException) { }
        assertNull(repository.count.value)
        assertEquals(HomeToolMetric.Unavailable, HomeToolMetric.AppCount(0).withSharedAppCount(repository.count.value))
    }

    @Test fun cancellationCannotPublishLateResultsOrEraseTheLastSuccessfulCount() = runTest {
        val delayed = CompletableDeferred<Int>()
        var first = true
        val repository = InstalledAppCountRepository { if (first) 7 else delayed.await() }
        repository.refresh()
        first = false
        val refresh = launch { repository.refresh() }
        runCurrent()
        refresh.cancelAndJoin()
        delayed.complete(8)
        assertEquals(7, repository.count.value)
        assertEquals(8, repository.refresh())
    }

    @Test fun storageAndLoadingStatesAreNotReplacedByAppCounts() {
        val storage = HomeToolMetric.Bytes(1234)
        assertSame(storage, storage.withSharedAppCount(10))
        assertSame(HomeToolMetric.Reading, HomeToolMetric.Reading.withSharedAppCount(10))
        assertSame(HomeToolMetric.AccessRequired, HomeToolMetric.AccessRequired.withSharedAppCount(null))
    }
}
