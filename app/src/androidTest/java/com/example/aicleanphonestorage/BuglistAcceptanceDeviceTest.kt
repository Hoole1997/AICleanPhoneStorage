package com.example.aicleanphonestorage

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import com.example.aicleanphonestorage.core.ui.completion.*
import com.example.aicleanphonestorage.databinding.ScreenCompletionBinding
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningState
import com.example.aicleanphonestorage.feature.junkcleaner.ui.JunkCleaningActivity
import com.example.aicleanphonestorage.feature.push.CleanNotificationHost
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import java.io.File
import java.util.Locale
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 仅使用测试索引/注入状态；不删除用户文件、不变更设备授权。 */
@RunWith(AndroidJUnit4::class)
class BuglistAcceptanceDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun themed(scale: Float = 1f): Context {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.US); fontScale = scale }
        return ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
    }
    private fun isolated(block: (ScanIndex) -> Unit) {
        val root = File(context.cacheDir, "buglist_${UUID.randomUUID()}").apply { mkdirs() }
        val wrapper = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(root, name)
        }
        try { ScanIndex(wrapper).use(block) } finally { root.deleteRecursively() }
    }
    private fun withoutStartupAd(block: () -> Unit) {
        lateinit var lease: AutoCloseable
        instrumentation.runOnMainSync { lease = ForegroundTransitionGuard.hold("buglist_fixture") }
        try { block() } finally { instrumentation.runOnMainSync { lease.close() } }
    }

    @Test fun unusedDefaultsToAllAndSortsNewestFirstAndFooterShowsCountAndSize() = isolated { index ->
        val now = System.currentTimeMillis()
        for (feature in listOf(CleanupFeature.UNUSED_FILES, CleanupFeature.LARGE_FILES)) {
            val scan = index.start(feature)
            index.insert(scan, listOf(90, 31, 60).mapIndexed { i, days -> ScannedFile(
                uri = "file:///buglist/$i", name = "$i.pdf", mime = "application/pdf", size = 12_000_000L + i,
                modifiedMillis = now - days * 86_400_000L, category = FileCategory.DOCUMENTS,
                backend = FileBackend.DIRECT, scope = "/buglist",
                bucket = if (feature == CleanupFeature.UNUSED_FILES) "unused_download" else "",
            ) })
            val handle = ScanHandle(scan, feature, 3, "Test fixtures").also(index::finishScan)
            val totals = index.totals(handle, CleanupFilter())
            assertEquals(3, totals.selectedCount)
            if (feature == CleanupFeature.UNUSED_FILES)
                assertEquals(listOf("1.pdf", "2.pdf", "0.pdf"), index.page(handle, CleanupFilter(), 0, 60).map { it.name })
            instrumentation.runOnMainSync {
                val binding = ScreenFileCleanupBinding.inflate(LayoutInflater.from(themed()))
                CleanupActionRenderer(binding).render(CleanupUiState(handle = handle, totals = totals, totalsReady = true))
                assertTrue(binding.cleanupAction.isEnabled)
                assertTrue(binding.cleanupAction.text.startsWith("Clean (3 · "))
                assertTrue(binding.cleanupAction.text.contains("MB"))
            }
        }
    }

    @Test fun largeFilterMenusIncludeAllRequiredOptionsAndLongLabelsFit() = withoutStartupAd {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var binding: ScreenFileCleanupBinding
            lateinit var filters: CleanupFilters
            var selected = CleanupFilter()
            scenario.onActivity { activity ->
                binding = ScreenFileCleanupBinding.inflate(LayoutInflater.from(themed(2f)))
                activity.setContentView(binding.root)
                binding.cleanupFilters.visibility = View.VISIBLE
                filters = CleanupFilters(binding) { selected = it; filters.render(it, true) }
                filters.render(selected, true)
            }
            onView(withId(R.id.cleanup_size)).perform(click())
            onView(withText("1GB")).perform(click())
            scenario.onActivity { assertEquals(1_000_000_000L, selected.minimumBytes) }
            onView(withId(R.id.cleanup_age)).perform(click())
            onView(withText("6 months")).check(matches(isDisplayed()))
            onView(withText("1 year")).perform(click())
            scenario.onActivity {
                assertEquals(365, selected.recentDays)
                assertEquals("1 year", binding.cleanupAge.text.toString())
                assertTrue(binding.cleanupAge.height >= binding.cleanupAge.layout.height)
                save(binding.root, "large_filters_2x")
                filters.close()
            }
        }
    }

    @Test fun zeroJunkKeepsInteractiveOverviewAndProducesCleanResult() = withoutStartupAd {
        val container = (context.applicationContext as CleanApplication).container
        val index = container.fileScanRepository.index
        val scan = index.start(CleanupFeature.SMART_CLEAN)
        index.finishScan(ScanHandle(scan, CleanupFeature.SMART_CLEAN, 0, "Test fixtures"))
        val store = ViewModelStore()
        try {
            ActivityScenario.launch<JunkCleaningActivity>(Intent(context, JunkCleaningActivity::class.java).putExtra(FileCleanupActivity.EXTRA_SCAN, scan)).use { scenario ->
                waitUntil {
                    var ready = false
                    scenario.onActivity { ready = it.findViewById<View>(R.id.junk_clean).isEnabled }
                    ready
                }
                scenario.onActivity {
                    assertEquals(it.getString(R.string.junk_got_it), it.findViewById<TextView>(R.id.junk_clean).text.toString())
                    assertEquals(View.VISIBLE, it.findViewById<View>(R.id.junk_categories).visibility)
                    save(it.findViewById(android.R.id.content), "junk_empty_overview")
                }
            }
            lateinit var model: CleanupViewModel
            instrumentation.runOnMainSync {
                model = CleanupViewModel(container.fileScanRepository, container.fileOperations, SavedStateHandle(), scan)
                store.put("empty", model)
            }
            waitUntil { model.state.value.totalsReady }
            instrumentation.runOnMainSync { model.prepare() }
            waitUntil { model.state.value.operation is CleanupOperationState.EmptyJunkReady }
            val ready = model.state.value.operation as CleanupOperationState.EmptyJunkReady
            instrumentation.runOnMainSync { model.startEmptyJunk(ready.id) }
            waitUntil { model.state.value.operation is CleanupOperationState.Result }
            val result = model.state.value.operation as CleanupOperationState.Result
            val report = result.summary.completionReport(CleanupFeature.SMART_CLEAN, result.id)
            assertTrue(report.emptyScan)
            assertEquals(0L, report.freedBytes)
            assertEquals(report, CompletionContract.read(CompletionContract.intent(context, report)))
            instrumentation.runOnMainSync {
                val binding = ScreenCompletionBinding.inflate(LayoutInflater.from(themed()))
                CompletionRenderer(binding).render(report)
                measure(binding.root, 375, 740)
                assertEquals("Your Phone is Very Clean", binding.completionTitle.text.toString())
                assertEquals(View.GONE, binding.completionCount.visibility)
                save(binding.root, "junk_clean_result")
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            index.discard(scan)
        }
    }

    @Test fun residentVariantsUseThreeOrFourEntriesAndCorrectBadges() {
        instrumentation.runOnMainSync {
            val cleaning = HomeCleaningState(false, { 1000L }, { 3201 })
            val host = CleanNotificationHost(context, cleaning)
            for (paid in listOf(false, true)) for (compact in listOf(false, true)) {
                cleaning.setPaidUser(paid)
                val view = host.residentViews(compact, themed(), "112").apply(themed(), null)
                assertEquals(if (paid) View.VISIBLE else View.GONE, view.findViewById<View>(R.id.shortcut_unused).visibility)
                assertEquals("App", view.findViewById<TextView>(R.id.shortcut_network_label).text.toString())
                assertEquals("Photos", view.findViewById<TextView>(R.id.shortcut_photos_label).text.toString())
                assertEquals("Accelerate", view.findViewById<TextView>(R.id.shortcut_unused_label).text.toString())
                for (id in listOf(R.id.shortcut_clean_badge, R.id.shortcut_network_badge))
                    assertEquals(if (paid) View.VISIBLE else View.GONE, view.findViewById<View>(id).visibility)
                if (paid) assertEquals("112", view.findViewById<TextView>(R.id.shortcut_network_badge).text.toString())
                measure(view, 343, null)
                save(view, "resident_${paid}_$compact")
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test fun allApplicationScreensDeclarePortraitAndDefaultAudienceIsOrganic() {
        val activities = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_ACTIVITIES).activities.orEmpty()
            .filter { it.name.startsWith("com.example.aicleanphonestorage.") }
        assertTrue(activities.size >= 10)
        activities.forEach { assertEquals(it.name, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, it.screenOrientation) }
        assertEquals("natural", BuildConfig.DEFAULT_USER_CHANNEL)
    }

    @Test fun appManagerLastUsedHasNoBlueBackgroundAndUsesNeverUsedLabel() {
        instrumentation.runOnMainSync {
            val ctx = themed()
            val presentation = com.example.aicleanphonestorage.feature.appmanager.ui.AppManagerPresentation(ctx)
            val app = com.example.aicleanphonestorage.feature.appmanager.data.ManagedApp(
                com.example.aicleanphonestorage.core.data.apps.InstalledAppSummary("test.app", "Fixture"), canUninstall = true)
            val text = presentation.used(app)
            assertTrue(text.toString().contains("Never Used"))
            val binding = com.example.aicleanphonestorage.databinding.ItemAppManagerBinding.inflate(LayoutInflater.from(ctx))
            binding.appManagerUsed.text = text
            assertNull(binding.appManagerUsed.background)
            assertTrue((text as android.text.Spanned).getSpans(0, text.length, android.text.style.BackgroundColorSpan::class.java).isEmpty())
            assertEquals("Uninstall", binding.appManagerUninstall.text.toString())
            measure(binding.root, 343, null)
            save(binding.root, "app_manager_labels")
        }
    }

    @Test fun dirtyHomeUsesCategoryCopyAndCleanHomeKeepsStoragePercentage() {
        instrumentation.runOnMainSync {
            val ctx = themed()
            val adapter = com.example.aicleanphonestorage.feature.home.ui.HomeListAdapter(false,
                com.example.aicleanphonestorage.feature.home.ui.HomeUiActions.None)
            val dirty = com.example.aicleanphonestorage.feature.home.ui.HeroContent(
                virtualJunk = true, value = "456.7", unit = "MB", usedPercent = 8)
            adapter.submitList(listOf(com.example.aicleanphonestorage.feature.home.ui.HomeRow.Hero(dirty)))
            val holder = adapter.onCreateViewHolder(android.widget.FrameLayout(ctx), adapter.getItemViewType(0))
            adapter.onBindViewHolder(holder, 0)
            measure(holder.itemView, 343, null)
            val label = holder.itemView.findViewById<TextView>(R.id.usage_label)
            assertEquals("Cache · Residual files · Downloads", label.text.toString())
            assertTrue(label.height >= label.layout.height)
            save(holder.itemView, "home_dirty_copy")
            // 非虚拟/已清理状态仍保留原有真实存储百分比。
            val cleanAdapter = com.example.aicleanphonestorage.feature.home.ui.HomeListAdapter(false,
                com.example.aicleanphonestorage.feature.home.ui.HomeUiActions.None)
            cleanAdapter.submitList(listOf(com.example.aicleanphonestorage.feature.home.ui.HomeRow.Hero(dirty.copy(virtualJunk = false))))
            val clean = cleanAdapter.onCreateViewHolder(android.widget.FrameLayout(ctx), cleanAdapter.getItemViewType(0))
            cleanAdapter.onBindViewHolder(clean, 0)
            assertTrue(clean.itemView.findViewById<TextView>(R.id.usage_label).text.contains("8%"))
        }
    }

    private fun measure(view: View, widthDp: Int, heightDp: Int?) {
        val density = view.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightDp?.let { (it * density).toInt() } ?: 0, if (heightDp == null) View.MeasureSpec.UNSPECIFIED else View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun save(view: View, name: String) {
        if (view.width == 0 || view.height == 0) return
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image); canvas.drawColor(android.graphics.Color.WHITE); view.draw(canvas)
            val folder = File(context.getExternalFilesDir(null), "buglist-tests").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (!condition() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(25)
        assertTrue("Bugfix UI did not reach its verified state", condition())
    }
}
