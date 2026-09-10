package com.example.aicleanphonestorage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.data.apps.InstalledAppSummary
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.databinding.ItemAppManagerBinding
import com.example.aicleanphonestorage.databinding.ScreenAppManagerBinding
import com.example.aicleanphonestorage.feature.appmanager.data.*
import com.example.aicleanphonestorage.feature.appmanager.ui.*
import com.example.aicleanphonestorage.feature.appmanager.ui.AppDetailsSettings
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 不启动 Activity、不解除锁屏、不改任何权限；真实 PackageManager 与原生 View 测量可在锁屏下测试。 */
@RunWith(AndroidJUnit4::class)
class AppManagerDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val reader
        get() = (context.applicationContext as CleanApplication).container.installedAppsReader

    @Test
    fun installedCatalogIsDeduplicatedAndMissingNotificationRulesRemainEditable() = runBlocking {
        val catalog = reader.read(progress = { _, _ -> })
        assertTrue(catalog.any { it.packageName == context.packageName })
        assertEquals(catalog.size, catalog.map { it.packageName }.toSet().size)
        assertTrue(catalog.all { it.label.isNotBlank() && it.installed })
        val missing = "com.example.aiclean.test.missing.package"
        val rules = reader.read(setOf(missing), setOf(context.packageName), true) { _, _ -> }
        assertTrue(rules.none { it.packageName == context.packageName })
        assertFalse(rules.single { it.packageName == missing }.installed)
        assertTrue(
            reader.read(setOf(missing), retainMissing = false, progress = { _, _ -> }).none {
                it.packageName == missing
            }
        )
    }

    @Test
    fun selectedPackageRoutesToItsOwnDetailsInsteadOfCleanerOrAppList() {
        val chosen = "com.example.selected.application"
        val intent = AppDetailsSettings.intent(chosen)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package", intent.data?.scheme)
        assertEquals(chosen, intent.data?.schemeSpecificPart)
        assertNotEquals(context.packageName, intent.data?.schemeSpecificPart)
    }

    @Test
    fun nativeCardsAndSortControlsHandleLargeFontsWithoutClipping() {
        for (widthDp in listOf(320, 375, 600)) for (scale in listOf(1f, 1.5f, 2f)) {
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val configured =
                    context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply {
                            fontScale = scale
                            setLocale(java.util.Locale.US)
                        }
                    )
                val themed = ContextThemeWrapper(configured, R.style.Theme_AICleanPhoneStorage)
                val container = (context.applicationContext as CleanApplication).container
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val adapter =
                    AppManagerAdapter(
                        scope,
                        AppIconLoader(themed, container.taskExecutor, 36),
                        {},
                        {},
                    )
                adapter.submitList(
                    listOf(fixture("An application with an exceptionally long display name 超长应用名称"))
                )
                val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0)
                adapter.onBindViewHolder(holder, 0)
                val binding = ItemAppManagerBinding.bind(holder.itemView)
                val density = themed.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                binding.root.layout(0, 0, width, binding.root.measuredHeight)
                assertTrue(binding.root.measuredHeight >= (220 * density).toInt())
                for (text in
                    listOf(
                        binding.appManagerName,
                        binding.appManagerInstalled,
                        binding.appManagerSize,
                        binding.appManagerUsed,
                        binding.appManagerUninstall,
                    )) {
                    assertTrue(
                        "Text clipped at $widthDp / $scale",
                        text.height - text.compoundPaddingTop - text.compoundPaddingBottom >=
                            text.layout.height,
                    )
                }
                assertTrue(binding.appManagerUninstall.height >= (48 * density).toInt())
                assertTrue(binding.appManagerUninstall.width >= (48 * density).toInt())
                assertTrue(binding.root.contentDescription.contains("APK size"))
                val screen = ScreenAppManagerBinding.inflate(LayoutInflater.from(themed))
                AppManagerSortControls(screen) {}.render(AppManagerSort())
                screen.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(
                        (812 * density).toInt(),
                        View.MeasureSpec.EXACTLY,
                    ),
                )
                screen.root.layout(0, 0, width, screen.root.measuredHeight)
                val buttons =
                    listOf(
                        screen.appManagerSortUsed,
                        screen.appManagerSortSize,
                        screen.appManagerSortName,
                    )
                for (button in buttons) {
                    assertTrue(button.width > 0 && button.right <= screen.appManagerSortBar.width)
                    assertTrue(
                        button.height - button.compoundPaddingTop - button.compoundPaddingBottom >=
                            button.layout.height
                    )
                }
                for ((i, a) in buttons.withIndex()) for (b in buttons.drop(i + 1)) {
                    assertTrue(
                        a.right <= b.left ||
                            b.right <= a.left ||
                            a.bottom <= b.top ||
                            b.bottom <= a.top
                    )
                }
                scope.cancel()
                bitmap =
                    Bitmap.createBitmap(width, binding.root.height, Bitmap.Config.ARGB_8888).also {
                        binding.root.draw(Canvas(it))
                    }
            }
            val directory =
                File(context.getExternalFilesDir(null), "app-manager-layout-tests").apply {
                    mkdirs()
                }
            bitmap!!.let { image ->
                File(directory, "card_${widthDp}_${scale}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
        }
    }

    @Test
    fun uninstallIntentTargetsOnlySelectedPackageAndAsksSystemForResult() {
        val intent = AppManagerActions.uninstallIntent("com.example.selected.application")
        assertEquals(android.content.Intent.ACTION_DELETE, intent.action)
        assertEquals("package:com.example.selected.application", intent.data.toString())
        assertTrue(intent.getBooleanExtra(android.content.Intent.EXTRA_RETURN_RESULT, false))
    }

    @Test
    fun realMetadataAndFallbackAreHonest() = runBlocking {
        val catalog =
            (context.applicationContext as CleanApplication).container.appManagerRepository.load()
        val own = catalog.apps.single { it.packageName == context.packageName }
        assertFalse(own.canUninstall)
        assertTrue(own.installedAt != null && own.installedAt > 0)
        if (!catalog.usageAccess) {
            assertTrue(
                catalog.apps.all {
                    it.sizeKind == AppSizeKind.APK && it.lastUse == AppLastUse.Unavailable
                }
            )
            assertTrue(own.sizeBytes != null && own.sizeBytes > 0)
        }
    }

    /** 仅用于设计核验的固定样本，不写入 Repository 或用户数据。 */
    private fun fixture(label: String) =
        ManagedApp(
            InstalledAppSummary("test.fixture.$label", label),
            installedAt = 1230724800000,
            sizeBytes = 112_630_000,
            canUninstall = true,
        )

    @Test
    fun designScreenSnapshot() {
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync {
            val configured =
                context.createConfigurationContext(
                    Configuration(context.resources.configuration).apply {
                        fontScale = 1f
                        setLocale(java.util.Locale.US)
                    }
                )
            val themed = ContextThemeWrapper(configured, R.style.Theme_AICleanPhoneStorage)
            val density = themed.resources.displayMetrics.density
            val screen = ScreenAppManagerBinding.inflate(LayoutInflater.from(themed))
            screen.appManagerContent.setPadding(0, (44 * density).toInt(), 0, 0)
            AppManagerSortControls(screen) {}.render(AppManagerSort())
            val container = (context.applicationContext as CleanApplication).container
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val adapter =
                AppManagerAdapter(scope, AppIconLoader(themed, container.taskExecutor, 36), {}, {})
            adapter.submitList(
                (1..4).map {
                    fixture("App name $it")
                        .copy(
                            sizeKind = AppSizeKind.USED,
                            lastUse = AppLastUse.Recorded(1789059600000),
                        )
                }
            )
            screen.appManagerList.layoutManager = LinearLayoutManager(themed)
            screen.appManagerList.adapter = adapter
            val width = (375 * density).toInt()
            val height = (812 * density).toInt()
            screen.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            screen.root.layout(0, 0, width, height)
            assertEquals(12f, screen.appManagerSortUsed.paddingStart / density, 0.5f)
            assertEquals(107f, screen.appManagerSortUsed.measuredWidth / density, 3f)
            bitmap =
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).apply {
                        drawColor(android.graphics.Color.rgb(246, 246, 246))
                        screen.root.draw(this)
                    }
                }
            screen.appManagerList.adapter = null
            scope.cancel()
        }
        val directory =
            File(context.getExternalFilesDir(null), "app-manager-layout-tests").apply { mkdirs() }
        bitmap!!.let { image ->
            File(directory, "screen_375.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        }
    }

    @Test
    fun translatedSortControlsWrapAtLargeFontSizes() {
        instrumentation.runOnMainSync {
            for (locale in listOf("fr", "de", "ar", "hi", "zh")) {
                val configured =
                    context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply {
                            fontScale = 2f
                            setLocale(java.util.Locale.forLanguageTag(locale))
                        }
                    )
                val themed = ContextThemeWrapper(configured, R.style.Theme_AICleanPhoneStorage)
                val screen = ScreenAppManagerBinding.inflate(LayoutInflater.from(themed))
                AppManagerSortControls(screen) {}.render(AppManagerSort())
                val density = themed.resources.displayMetrics.density
                val width = (320 * density).toInt()
                val height = (812 * density).toInt()
                screen.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                screen.root.layout(0, 0, width, height)
                for (button in
                    listOf(
                        screen.appManagerSortUsed,
                        screen.appManagerSortSize,
                        screen.appManagerSortName,
                    )) {
                    assertTrue(
                        "Overflow in $locale",
                        button.left >= 0 && button.right <= screen.appManagerSortBar.width,
                    )
                    assertTrue(
                        "Clipped text in $locale",
                        button.height - button.compoundPaddingTop - button.compoundPaddingBottom >=
                            button.layout.height,
                    )
                }
            }
        }
    }

    @Test
    fun pageUsesRealCatalogAndAppliesSortSelection() = runBlocking<Unit> {
        val container = (context.applicationContext as CleanApplication).container
        val catalog = container.appManagerRepository.load()
        val token = container.appManagerTransfer.put(catalog)
        androidx.test.core.app.ActivityScenario.launch<AppManagerActivity>(
                android.content
                    .Intent(context, AppManagerActivity::class.java)
                    .putExtra(AppManagerActivity.EXTRA_CATALOG, token)
            )
            .use { scenario ->
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                var ready = false
                while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val list =
                            activity.findViewById<androidx.recyclerview.widget.RecyclerView>(
                                R.id.app_manager_list
                            )
                        ready = list.adapter?.itemCount == catalog.apps.size && list.childCount > 0
                    }
                    if (!ready) android.os.SystemClock.sleep(40)
                }
                assertTrue(ready)
                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.app_manager_sort_name).performClick()
                    assertTrue(
                        activity
                            .findViewById<com.google.android.material.button.MaterialButton>(
                                R.id.app_manager_sort_name
                            )
                            .isChecked
                    )
                }
                instrumentation.waitForIdleSync()
                // Capture actual system insets, package icons and optional access notice, without
                // launching uninstall.
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    val image =
                        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(image))
                    val directory =
                        File(context.getExternalFilesDir(null), "app-manager-layout-tests").apply {
                            mkdirs()
                        }
                    File(directory, "actual_page.png").outputStream().use {
                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    image.recycle()
                }
            }
    }
}
