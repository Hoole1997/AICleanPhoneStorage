package com.example.aicleanphonestorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.feature.startup.grouping.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupingPersistenceDeviceTest {
    @Test fun savedValuesSurviveStoreRecreationAndAreNotPlaintext() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 使用独立测试存储和假上报器，不改变真实去重记录或调用线上埋点。
        val name = "grouping_persistence_test"
        val stored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        val a = "test-group-${System.nanoTime()}"
        val b = "$a-B"
        val sent = mutableListOf<String>()
        suspend fun report(value: String) = GroupingReporter({ value }, PersistentGroupingStore(context, name),
            { sent += it; true }).reportCurrent()
        try {
            report(a); report(b); report(a)
            assertEquals(listOf("Grouping_$a", "Grouping_$b"), sent)
            assertFalse(stored.all.keys.any { it.contains(a) })
            assertTrue(PersistentGroupingStore(context, name).contains(a))
            assertTrue(PersistentGroupingStore(context, name).contains(b))
        } finally { stored.edit().clear().commit() }
    }
}
