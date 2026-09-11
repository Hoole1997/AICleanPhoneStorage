package com.example.aicleanphonestorage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.ad.*
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import com.example.aicleanphonestorage.feature.home.preview.HomePreviewSupport
import com.example.aicleanphonestorage.feature.home.ui.*
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import java.io.File
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 假广告仅存在测试中，用于检查容器显隐/占位/生命周期；不请求或点击真实广告。 */
@RunWith(AndroidJUnit4::class)
class NativeAdDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    @Test
    fun goneContainerLoadsOnceAndSdkControlsVisibility() {
        lateinit var slot: ViewGroup
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            var calls = 0
            lateinit var callback: (Boolean) -> Unit
            lateinit var job: CompletableJob
            scenario.onActivity { activity ->
                val frame = FrameLayout(activity)
                slot =
                    activity.layoutInflater.inflate(R.layout.view_native_ad_slot, frame, false)
                        as ViewGroup
                frame.addView(slot)
                activity.setContentView(frame)
                NativeAdCoordinator(activity, slot, NativeAdPlacements.HOME) {
                    key,
                    container,
                    complete ->
                    assertEquals("native_home", key)
                    assertEquals(View.GONE, container.visibility)
                    calls++
                    callback = complete
                    Job().also { job = it }
                }
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(1, calls)
                assertEquals(View.GONE, slot.visibility)
                fill(slot)
                slot.visibility = View.VISIBLE // 模拟 loadNative 成功，由请求方法控制可见性。
                callback(true)
                job.complete()
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertEquals(1, calls)
                assertEquals(View.VISIBLE, slot.visibility)
            }
        }
        assertEquals(0, slot.childCount)
    }

    @Test
    fun backgroundCancelsPendingLoadAndFailureLeavesNoSpace() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var slot: ViewGroup
            val jobs = mutableListOf<CompletableJob>()
            lateinit var callback: (Boolean) -> Unit
            scenario.onActivity { activity ->
                val frame = FrameLayout(activity)
                slot =
                    activity.layoutInflater.inflate(R.layout.view_native_ad_slot, frame, false)
                        as ViewGroup
                frame.addView(slot)
                activity.setContentView(frame)
                NativeAdCoordinator(activity, slot, NativeAdFeature.LARGE.featureSlot) {
                    _,
                    _,
                    complete ->
                    callback = complete
                    Job().also { jobs += it }
                }
            }
            instrumentation.waitForIdleSync()
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(jobs.first().isCancelled)
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(2, jobs.size)
                callback(false)
                jobs.last().complete()
                assertEquals(View.GONE, slot.visibility)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals(2, jobs.size) }
        }
    }

    @Test
    fun featureAndResultLayoutsCollapseEmptySlotAndKeepActionsAccessible() {
        val layouts =
            listOf(
                Triple(R.layout.screen_junk_cleaning, R.id.native_ad, R.id.junk_clean),
                Triple(R.layout.screen_file_cleanup, R.id.native_ad, R.id.cleanup_action),
                Triple(
                    R.layout.screen_notification_cleaner,
                    R.id.native_ad,
                    R.id.notification_done,
                ),
                Triple(R.layout.screen_app_manager, R.id.native_ad, 0),
                Triple(R.layout.screen_network_traffic, R.id.native_ad, 0),
                Triple(R.layout.screen_completion, R.id.completion_ad, 0),
            )
        for ((layout, slotId, actionId) in layouts) for ((width, height) in
            listOf(375 to 812, 600 to 320)) {
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val themed = themed(width, height)
                val density = themed.resources.displayMetrics.density
                val root = LayoutInflater.from(themed).inflate(layout, null) as ViewGroup
                val slot = root.findViewById<ViewGroup>(slotId)
                assertEquals(View.GONE, slot.visibility)
                assertEquals(0, slot.childCount)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, slot.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, slot.layoutParams.height)
                assertEquals(0, slot.paddingLeft + slot.paddingRight + slot.paddingTop + slot.paddingBottom)
                measure(root, width, height, density)
                val column = slot.parent as ViewGroup
                val flexibleBody = column.getChildAt(column.indexOfChild(slot) - 1)
                val withoutAdHeight = flexibleBody.height
                fill(slot)
                slot.visibility = View.VISIBLE
                measure(root, width, height, density)
                assertTrue(flexibleBody.height < withoutAdHeight)
                assertEquals("Fixed bottom ad fills the content width", column.width, slot.width)
                val bounds = bounds(root, slot)
                assertTrue("Ad outside screen", bounds.top >= 0 && bounds.bottom <= root.height)
                if (actionId != 0) {
                    val action = root.findViewById<View>(actionId)
                    if (action.visibility == View.VISIBLE) {
                        val actionBounds = bounds(root, action)
                        assertTrue("Ad overlaps action", bounds.bottom <= actionBounds.top)
                        assertTrue(actionBounds.bottom <= root.height)
                        assertTrue(action.height >= (48 * density).toInt())
                    }
                }
                bitmap =
                    Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                        root.draw(Canvas(it))
                    }
                slot.visibility = View.GONE
                measure(root, width, height, density)
                assertEquals(View.GONE, slot.visibility)
                assertEquals("Hidden ad must not reserve layout space", withoutAdHeight, flexibleBody.height)
            }
            save(bitmap!!, "${context.resources.getResourceEntryName(layout)}_$width")
        }
    }

    @Test
    fun homeAdUsesOneContainerBetweenSummaryAndTools() {
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync {
            val themed = themed(375, 1200)
            val binding = ScreenHomeBinding.inflate(LayoutInflater.from(themed))
            val slot =
                LayoutInflater.from(themed)
                    .inflate(R.layout.view_native_ad_slot, binding.homeContent, false) as ViewGroup
            val renderer = HomeRenderer(binding, HomeUiActions.None, slot)
            renderer.render(HomeUiState.Loading, HomePreviewSupport.content("initial"))
            measure(binding.root, 375, 1200, themed.resources.displayMetrics.density)
            assertEquals(11, binding.homeList.adapter!!.itemCount)
            assertEquals(HomeListAdapter.NATIVE, binding.homeList.adapter!!.getItemViewType(2))
            assertEquals(View.GONE, slot.visibility)
            fill(slot)
            slot.visibility = View.VISIBLE
            measure(binding.root, 375, 1200, themed.resources.displayMetrics.density)
            assertTrue(slot.parent != null)
            val margin = themed.resources.getDimensionPixelSize(R.dimen.home_page_margin)
            val adBounds = bounds(binding.homeList, slot)
            assertEquals(margin, adBounds.left)
            assertEquals(binding.homeList.width - margin, adBounds.right)
            assertEquals(binding.homeList.width - 2 * margin, slot.width)
            assertEquals(0, slot.paddingLeft + slot.paddingRight + slot.paddingTop + slot.paddingBottom)
            bitmap =
                Bitmap.createBitmap(
                        binding.root.width,
                        binding.root.height,
                        Bitmap.Config.ARGB_8888,
                    )
                    .also { binding.root.draw(Canvas(it)) }
            renderer.dispose()
        }
        save(bitmap!!, "home_native_test")
    }

    private fun themed(width: Int, height: Int) =
        ContextThemeWrapper(
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    fontScale = 1f
                    screenWidthDp = width
                    screenHeightDp = height
                    setLocale(java.util.Locale.US)
                }
            ),
            R.style.Theme_AICleanPhoneStorage,
        )

    private fun fill(slot: ViewGroup) {
        val density = slot.resources.displayMetrics.density
        slot.addView(
            TextView(slot.context).apply {
                text = "Native ad layout fixture (test only)"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.DKGRAY)
                gravity = android.view.Gravity.CENTER
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (96 * density).toInt()),
        )
    }

    private fun measure(root: View, width: Int, height: Int, density: Float) {
        repeat(2) {
            root.requestLayout()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(
                    (width * density).toInt(),
                    View.MeasureSpec.EXACTLY,
                ),
                View.MeasureSpec.makeMeasureSpec(
                    (height * density).toInt(),
                    View.MeasureSpec.EXACTLY,
                ),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun bounds(root: ViewGroup, view: View) =
        Rect(0, 0, view.width, view.height).also { root.offsetDescendantRectToMyCoords(view, it) }

    private fun save(bitmap: Bitmap, name: String) {
        val directory =
            File(context.getExternalFilesDir(null), "native-ad-tests").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
