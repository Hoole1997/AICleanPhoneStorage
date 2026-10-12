package com.example.aicleanphonestorage

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.analytics.FeatureTelemetry
import com.example.aicleanphonestorage.feature.home.ui.HeroContent
import com.example.aicleanphonestorage.feature.home.ui.HomeListAdapter
import com.example.aicleanphonestorage.feature.home.ui.HomeRow
import com.example.aicleanphonestorage.feature.home.ui.HomeTool
import com.example.aicleanphonestorage.feature.home.ui.HomeToolItem
import com.example.aicleanphonestorage.feature.home.ui.HomeUiActions
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException
import com.example.aicleanphonestorage.feature.speedtest.ui.SpeedTestFailureDialog
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在真实窗口中核验首页原生卡片绑定与点击。样例只交给生产 Adapter 的 UI 契约，
 * 回调用本地收集器记录协议字段，不导航、请求权限或向埋点 SDK 发送测试事件。
 */
@RunWith(AndroidJUnit4::class)
class NewTelemetryDeviceTest {
    @get:Rule val noHotAds = NoHotStartAdsRule()

    private data class Entry(
        val wire: String,
        val badgeId: Int,
        val clickIds: List<Int?>,
        val row: (Boolean) -> HomeRow,
    )

    @Test
    fun homeClicksUseTheDisplayedBadgeUntilRecyclerActuallyRebinds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val calls = mutableListOf<Map<String, Any>>()
        val actions = object : HomeUiActions {
            override fun onSettings() = Unit
            override fun onSmartClean() = error("Rendered Smart Clean must carry its badge snapshot")
            override fun onMalwareScan() = error("Rendered malware entry must carry its badge snapshot")
            override fun onToolSelected(tool: HomeTool) = error("Rendered tool must carry its badge snapshot")
            override fun onSmartClean(homeBadgeVisible: Boolean) {
                calls += FeatureTelemetry.entryParameters("junk", homeBadgeVisible)
            }
            override fun onMalwareScan(homeBadgeVisible: Boolean) {
                calls += FeatureTelemetry.entryParameters("malware", homeBadgeVisible)
            }
            override fun onToolSelected(tool: HomeTool, homeBadgeVisible: Boolean) {
                assertEquals(HomeTool.SpeedTest, tool)
                calls += FeatureTelemetry.entryParameters("speedtest", homeBadgeVisible)
            }
        }
        val entries = listOf(
            Entry("junk", R.id.clean_badge, listOf(R.id.clean_button)) {
                HomeRow.Hero(HeroContent(value = "0.0", unit = "MB", badge = it))
            },
            Entry("MalwareScan", R.id.malware_entry_badge, listOf(null, R.id.malware_entry_scan)) {
                HomeRow.Malware(it)
            },
            Entry("speedtest", R.id.tool_badge, listOf(null)) {
                HomeRow.Tool(HomeToolItem(HomeTool.SpeedTest, badge = it))
            },
        )
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            for (entry in entries) {
                lateinit var recycler: RecyclerView
                lateinit var adapter: HomeListAdapter
                val initial = CountDownLatch(1)
                scenario.onActivity { activity ->
                    adapter = HomeListAdapter(expanded = false, actions = actions)
                    recycler = RecyclerView(activity).apply {
                        layoutManager = LinearLayoutManager(activity)
                        itemAnimator = null
                        this.adapter = adapter
                    }
                    activity.setContentView(recycler, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    ))
                    adapter.submitList(listOf(entry.row(true))) { initial.countDown() }
                }
                assertTrue("${entry.wire} first binding", initial.await(5, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                lateinit var visible: RecyclerView.ViewHolder
                scenario.onActivity {
                    val holder = recycler.findViewHolderForAdapterPosition(0)
                    assertNotNull("${entry.wire} must be bound in the real RecyclerView", holder)
                    visible = requireNotNull(holder)
                    assertTrue(visible.itemView.isAttachedToWindow)
                    assertTrue(visible.itemView.width > 0)
                    assertEquals(View.VISIBLE, visible.itemView.findViewById<View>(entry.badgeId).visibility)
                }

                val updated = CountDownLatch(1)
                scenario.onActivity {
                    // Diff 提交与真实重绑之间仍是旧界面；同一主线程调用内不能把待显示状态当成点击状态。
                    adapter.submitList(listOf(entry.row(false))) { updated.countDown() }
                    for (id in entry.clickIds) {
                        val target = id?.let { visible.itemView.findViewById<View>(it) } ?: visible.itemView
                        assertTrue(target.performClick())
                        assertEquals(mapOf("entry" to entry.wire, "home_badge" to "yes"), calls.last())
                    }
                }
                assertTrue("${entry.wire} changed binding", updated.await(5, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val rebound = requireNotNull(recycler.findViewHolderForAdapterPosition(0))
                    assertSame("${entry.wire} should reuse its holder", visible, rebound)
                    assertEquals(View.GONE, rebound.itemView.findViewById<View>(entry.badgeId).visibility)
                    for (id in entry.clickIds) {
                        val target = id?.let { rebound.itemView.findViewById<View>(it) } ?: rebound.itemView
                        assertTrue(target.performClick())
                        assertEquals(mapOf("entry" to entry.wire, "home_badge" to "no"), calls.last())
                    }
                    recycler.adapter = null
                }
            }
            assertEquals(8, calls.size)
        }
    }

    @Test
    fun restoredFailureDialogKeepsOriginalCauseAndDeliversItsActionOnlyOnce() {
        val responses = mutableListOf<Bundle>()
        val originalReason = SpeedTestException.Reason.NetworkChanged
        val originalGeneration = 47L
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResultListener(
                    SpeedTestFailureDialog.RESULT, activity,
                ) { _, result -> responses += Bundle(result) }
                SpeedTestFailureDialog.create(originalReason, originalGeneration)
                    .showNow(activity.supportFragmentManager, SpeedTestFailureDialog.TAG)
            }
            // 重建由真实 FragmentManager 恢复弹框，验证原因与代次不会被重新推导成当前扫描状态。
            scenario.recreate()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResultListener(
                    SpeedTestFailureDialog.RESULT, activity,
                ) { _, result -> responses += Bundle(result) }
                val restored = activity.supportFragmentManager
                    .findFragmentByTag(SpeedTestFailureDialog.TAG) as SpeedTestFailureDialog
                assertTrue(restored.belongsTo(originalReason, originalGeneration))
                assertFalse(restored.belongsTo(SpeedTestException.Reason.Timeout, originalGeneration))
                assertFalse(restored.belongsTo(originalReason, originalGeneration + 1))
                assertTrue(requireNotNull(restored.dialog).isShowing)
                assertTrue(responses.isEmpty())
                val retry = requireNotNull(restored.dialog).findViewById<View>(R.id.failed_retry)
                assertTrue(retry.performClick())
                // 已排队的第二次按钮点击仍落到旧 View；它不能重复提交同一弹框的操作。
                assertTrue(retry.performClick())
                assertEquals(1, responses.size)
                val response = responses.single()
                assertEquals(SpeedTestFailureDialog.RETRY, response.getString(SpeedTestFailureDialog.ACTION))
                assertEquals(originalGeneration, response.getLong(SpeedTestFailureDialog.GENERATION))
                assertEquals(originalReason, SpeedTestFailureDialog.reasonFrom(response))
                assertEquals(setOf("action", "generation", "reason"), response.keySet())
            }
        }
    }
}
