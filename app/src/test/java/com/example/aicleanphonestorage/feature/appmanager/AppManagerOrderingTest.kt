package com.example.aicleanphonestorage.feature.appmanager

import com.example.aicleanphonestorage.core.data.apps.InstalledAppSummary
import com.example.aicleanphonestorage.feature.appmanager.data.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AppManagerOrderingTest {
    private fun app(name: String, bytes: Long? = null, use: AppLastUse = AppLastUse.Unavailable) =
        ManagedApp(InstalledAppSummary(name, name), sizeBytes = bytes, lastUse = use)

    private fun names(rows: List<ManagedApp>, sort: AppManagerSort) =
        AppManagerOrdering.sorted(rows, sort, Locale.US).map { it.label }

    @Test
    fun unknownSizesStayLastInBothDirectionsAndTiesRemainStable() {
        val rows = listOf(app("Unknown"), app("B", 10), app("A", 10), app("Large", 30))
        assertEquals(
            listOf("Large", "A", "B", "Unknown"),
            names(rows, AppManagerSort(AppSortKey.SIZE)),
        )
        assertEquals(
            listOf("A", "B", "Large", "Unknown"),
            names(rows, AppManagerSort(AppSortKey.SIZE, false)),
        )
    }

    @Test
    fun absentHistoryIsDistinctFromUnavailableAndRecentUse() {
        val rows =
            listOf(
                app("Unknown"),
                app("No record", use = AppLastUse.NoRecentRecord),
                app("Old", use = AppLastUse.Recorded(10)),
                app("Recent", use = AppLastUse.Recorded(20)),
            )
        assertEquals(listOf("Recent", "Old", "No record", "Unknown"), names(rows, AppManagerSort()))
        assertEquals(
            listOf("No record", "Old", "Recent", "Unknown"),
            names(rows, AppManagerSort(descending = false)),
        )
    }

    @Test
    fun switchingKeyUsesMeaningfulDefaultAndRepeatedSelectionReversesIt() {
        val sort = AppManagerSort().select(AppSortKey.NAME)
        assertFalse(sort.descending)
        assertTrue(sort.select(AppSortKey.NAME).descending)
        assertTrue(sort.select(AppSortKey.SIZE).descending)
        assertEquals(listOf("A", "B"), names(listOf(app("B"), app("A")), sort))
    }
}
