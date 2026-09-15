package com.example.aicleanphonestorage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.feature.notifications.data.*
import com.example.aicleanphonestorage.feature.notifications.ui.NotificationCleanerViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 独立 DataStore 文件，不写入真实监听规则，不清理设备上的任何通知。 */
@RunWith(AndroidJUnit4::class)
class NotificationDraftDeviceTest {
    @Test fun selectionStaysInDraftUntilAtomicConfirmation() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "notification_draft_${UUID.randomUUID()}").apply { mkdirs() }
        val diskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val rules = NotificationRulesStore(PreferenceDataStoreFactory.create(scope = diskScope, produceFile = { File(folder, "test.preferences_pb") }))
        val catalog = NotificationCatalog(listOf(NotificationApp("fixture.a", "A"), NotificationApp("fixture.b", "B")))
        val repository = object : NotificationAppsRepository {
            override val selectedPackages = rules.selectedPackages
            override suspend fun hasAccess() = true
            override suspend fun loadApps(progress: (Int, Int) -> Unit) = catalog
            override suspend fun setEnabled(packageName: String, enabled: Boolean) = rules.setEnabled(packageName, enabled)
            override suspend fun setSelection(packages: Set<String>) = rules.setSelection(packages)
        }
        val changes = Channel<Set<String>>(Channel.UNLIMITED)
        val observer = launch { rules.selectedPackages.collect { changes.send(it) } }
        try {
            assertEquals(emptySet<String>(), withTimeout(5000) { changes.receive() })
            lateinit var model: NotificationCleanerViewModel
            val saved = SavedStateHandle()
            instrumentation.runOnMainSync {
                model = NotificationCleanerViewModel(repository, flowOf(true), saved, false, catalog)
                store.put("notification", model)
                model.onForeground()
            }
            withTimeout(5000) { while (!model.state.value.rulesLoaded) delay(10) }
            instrumentation.runOnMainSync { model.setEnabled("fixture.a", true); model.setEnabled("fixture.b", true) }
            assertTrue(rules.selectedPackages.first().isEmpty())
            assertNull(model.completionReport())
            instrumentation.runOnMainSync { model.commitSelection() }
            assertEquals(setOf("fixture.a", "fixture.b"), withTimeout(5000) { changes.receive() })
            withTimeout(5000) { while (model.completionReport() == null) delay(10) }
            assertEquals(2, model.completionReport()!!.completed)
            assertFalse(saved.contains("notification.draft"))
            instrumentation.runOnMainSync { model.completionPresented() }
            assertNull(model.completionReport())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            observer.cancelAndJoin()
            diskScope.cancel()
            diskScope.coroutineContext[Job]?.join()
            folder.deleteRecursively()
        }
    }
}
