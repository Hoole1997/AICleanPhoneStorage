package com.example.aicleanphonestorage

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.home.data.FirebaseHomeBadgeRemoteSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 真机 Remote Config 下的统一开关：未下发按默认 true，下发 false 时全部隐藏（无界面）。 */
@RunWith(AndroidJUnit4::class)
class HomeBadgeRemoteDeviceTest {
    @Test fun unifiedSwitchFollowsRemoteValueOrDefault() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CleanApplication
        val remote = withTimeout(40_000) { FirebaseHomeBadgeRemoteSource.enabled() }
        val visible = withTimeout(40_000) { app.container.homeBadges.observeVisible().first { true } }
        val enabled = remote ?: FirebaseHomeBadgeRemoteSource.DEFAULT_ENABLED
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("badges", "ready=${FirebaseHomeBadgeRemoteSource.ready()} remote=$remote enabled=$enabled visible=${visible.map { it.key }}")
        })
        if (!enabled) assertEquals(emptySet<Any>(), visible)
    }
}
