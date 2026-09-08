package com.example.aicleanphonestorage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.databinding.ItemAppManagerBinding
import com.example.aicleanphonestorage.feature.appmanager.ui.AppDetailsSettings
import java.io.File
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
    fun nativeCardHandlesLongNamesAndLargeFontsWithoutClippingArrow() {
        for (widthDp in listOf(320, 375, 600)) for (scale in listOf(1f, 1.5f, 2f)) {
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val configured =
                    context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply { fontScale = scale }
                    )
                val themed = ContextThemeWrapper(configured, R.style.Theme_AICleanPhoneStorage)
                val binding = ItemAppManagerBinding.inflate(LayoutInflater.from(themed))
                binding.appManagerName.text =
                    "An application with an exceptionally long display name 超长应用名称"
                val density = themed.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                binding.root.layout(0, 0, width, binding.root.measuredHeight)
                assertTrue(binding.root.measuredHeight >= (68 * density).toInt())
                assertTrue(binding.appManagerName.right <= binding.appManagerArrow.left)
                assertTrue(binding.appManagerName.height >= binding.appManagerName.layout.height)
                assertTrue(binding.appManagerArrow.right <= width)
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
}
