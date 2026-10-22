package io.docview.push.boot

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootRestoreDeviceTest {
    @Test fun openedAndAttemptPersistAcrossStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "io.docview.push.test")
        val prefs = context.getSharedPreferences("notification_boot", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            assertFalse(BootRestoreStore(context).wasOpened())
            assertEquals(-1, BootRestoreStore(context).lastAttemptBoot())
            BootRestoreStore(context).recordOpened()
            BootRestoreStore(context).recordAttempt(42)
            val restored = BootRestoreStore(context)
            assertTrue(restored.wasOpened())
            assertEquals(42, restored.lastAttemptBoot())
            assertNotNull(BootRestorePolicy.skipReason(restored.wasOpened(), true, true, false, false, 42, restored.lastAttemptBoot()))
            assertNull(BootRestorePolicy.skipReason(restored.wasOpened(), true, true, false, false, 43, restored.lastAttemptBoot()))
        } finally { prefs.edit().clear().commit() }
    }
}
