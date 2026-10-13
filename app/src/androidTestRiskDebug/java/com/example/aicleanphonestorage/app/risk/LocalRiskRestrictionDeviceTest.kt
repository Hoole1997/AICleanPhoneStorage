package com.example.aicleanphonestorage.app.risk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.extension.AdRiskPlugin
import com.android.common.risk.AdRisk
import com.android.common.risk.AdRiskSignalConfig
import com.example.aicleanphonestorage.BuildConfig
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 仅在显式 -PlocalRiskInstrumentation=true 时编入 localRiskDebug 的测试 APK。
 * 正向策略输入是独立测试快照，不是线上 XM 检测结果；真实检测及失败放行逻辑不替换。
 * 测试写真实 AdRisk PROCESS 限制并检查 Bill 的真实平台候选门禁，不请求、展示或点击广告。
 * 事件交由真实 Telemetry 转换后进入测试收件箱，不上传测试埋点到数数。
 */
@RunWith(AndroidJUnit4::class)
class LocalRiskRestrictionDeviceTest {
    private val billGate = InstalledBillRiskGate()

    @Before
    fun awaitActualDetectionAndRequireIsolatedLocalPackage() = runBlocking {
        assertEquals("local", BuildConfig.FLAVOR)
        assertEquals("riskDebug", BuildConfig.BUILD_TYPE)
        assertEquals(
            "com.hotpotgames.happysave.global",
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
        )
        // 本进程真实检测先结束，避免测试快照与应用的首次检测释放相互覆盖。
        withTimeout(20_000L) { DeviceRiskRuntime.awaitInitialDecision() }
        assertPlatforms(emptySet())
    }

    @Test
    fun policiesAndOverlappingSignalsControlActualBillPlatformsIncludingMax() = runBlocking {
        withBridge(AdRiskSignalConfig()) { bridge, config, _ ->
            bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN, DeviceRiskSignal.DNS)))
            awaitPolicy(bridge, emptyMap())
            assertPlatforms(emptySet()) // -1 不施加平台限制。

            config.value = AdRiskSignalConfig(vpnAdConfig = 0)
            awaitPolicy(bridge, mapOf(DeviceRiskSignal.VPN to 0))
            assertPlatforms(setOf(AdPlatform.ADMOB))
            assertTrue("policy 0 must allow MAX", billGate.isPlatformAllowed(AdPlatform.MAX))

            config.value = AdRiskSignalConfig(vpnAdConfig = 0, dnsAdConfig = 1)
            awaitPolicy(bridge, mapOf(DeviceRiskSignal.VPN to 0, DeviceRiskSignal.DNS to 1))
            assertPlatforms(AdPlatform.entries.toSet())
            assertFalse("policy 1 must also block MAX", billGate.isPlatformAllowed(AdPlatform.MAX))

            // 移除全平台风险后，重叠的 VPN 风险仍保留 ADMOB 限制。
            config.value = AdRiskSignalConfig(vpnAdConfig = 0)
            awaitPolicy(bridge, mapOf(DeviceRiskSignal.VPN to 0))
            assertPlatforms(setOf(AdPlatform.ADMOB))

            config.value = AdRiskSignalConfig()
            awaitPolicy(bridge, emptyMap())
            assertPlatforms(emptySet())
        }
    }

    @Test
    fun allNineSignalsReportExactEventsOnceAfterRestrictionsReallyApply() = runBlocking {
        for ((policy, scope) in listOf(0 to "admob", 1 to "all")) {
            withBridge(allPolicies(policy)) { bridge, config, events ->
                bridge.apply(DeviceRiskSnapshot(DeviceRiskSignal.entries.toSet()))
                awaitPolicy(bridge, DeviceRiskSignal.entries.associateWith { policy })
                assertPlatforms(if (policy == 0) setOf(AdPlatform.ADMOB) else AdPlatform.entries.toSet())
                assertTrue("No report before the initial APPLIED decision", events.isEmpty())
                bridge.confirmInitialDecision()
                eventually { events.size == EXPECTED_EVENTS.size }
                assertEquals(EXPECTED_EVENTS, events.map { it.name }.toSet())
                assertTrue(events.all { it.parameters == mapOf("scope" to scope) })

                // 更新配置、重复快照及撤销再恢复都不重复统计同进程同一风险维度。
                config.value = AdRiskSignalConfig()
                awaitPolicy(bridge, emptyMap())
                config.value = allPolicies(policy)
                awaitPolicy(bridge, DeviceRiskSignal.entries.associateWith { policy })
                bridge.apply(DeviceRiskSnapshot(DeviceRiskSignal.entries.toSet()))
                awaitPolicy(bridge, DeviceRiskSignal.entries.associateWith { policy })
                delay(150L) // 给独立遥测 collector 一次明确的重复派发观察窗口。
                assertEquals(EXPECTED_EVENTS.size, events.size)
            }
        }
    }

    @Test
    fun detectorFailureAndDeadlineReleaseActualBillRestrictionsAndWaitingAds() = runBlocking {
        for (timeout in listOf(false, true)) {
            withBridge(allPolicies(1)) { bridge, _, events ->
                bridge.apply(DeviceRiskSnapshot(DeviceRiskSignal.entries.toSet()))
                awaitPolicy(bridge, DeviceRiskSignal.entries.associateWith { 1 })
                assertPlatforms(AdPlatform.entries.toSet())
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                try {
                    val detector = DeviceRiskCoordinator(
                        scope = scope,
                        detect = {
                            if (timeout) awaitCancellation()
                            throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.SDK_ERROR)
                        },
                        apply = { error("Unavailable detector must never apply a snapshot") },
                        release = bridge::releaseForUnavailable,
                        timeoutMillis = 100L,
                    )
                    withTimeout(2_000L) { detector.awaitInitialDecision() }
                    assertEquals(
                        if (timeout) DeviceRiskStatus.TIMED_OUT else DeviceRiskStatus.FAILED_OPEN,
                        detector.state.value,
                    )
                    awaitPolicy(bridge, emptyMap())
                    assertPlatforms(emptySet())
                    assertTrue("Failed/timed-out decisions must not report Ad_Block", events.isEmpty())
                } finally {
                    scope.cancel()
                }
            }
        }
    }

    private suspend fun withBridge(
        initial: AdRiskSignalConfig,
        test: suspend (DeviceRiskPolicyBridge, MutableStateFlow<AdRiskSignalConfig>, List<Event>) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val config = MutableStateFlow(initial)
        val events = CopyOnWriteArrayList<Event>()
        val telemetry = DeviceRiskTelemetry(eventSink = { name, params ->
            events += Event(name, params.toMap())
        })
        val bridge = DeviceRiskPolicyBridge(
            scope, config, AdRisk::updatePlatformRestriction, telemetry::reportAdBlock,
        )
        try {
            test(bridge, config, events)
        } finally {
            // 只涉及测试包本进程的 PROCESS 限制；不读取/清除 TODAY 或业务持久配置。
            bridge.releaseForUnavailable()
            try { withTimeout(2_000L) { bridge.awaitSynchronized() } }
            finally { scope.cancel() }
            assertPlatforms(emptySet())
        }
    }

    private suspend fun awaitPolicy(bridge: DeviceRiskPolicyBridge, expected: Map<DeviceRiskSignal, Int>) {
        // Flow 发布异步，不用旧版本的 awaitSynchronized() 结果作为新配置已处理的证明。
        eventually {
            bridge.blockedSignals() == expected &&
                AdPlatform.entries.all { billGate.isPlatformAllowed(it) == (it !in platformsFor(expected)) }
        }
        assertTrue(withTimeout(2_000L) { bridge.awaitSynchronized() })
    }

    private fun assertPlatforms(blocked: Set<AdPlatform>) {
        for (platform in AdPlatform.entries) {
            assertEquals("SDK plugin policy for $platform", platform !in blocked, billGate.isPlatformAllowed(platform))
            assertEquals("Bill candidate gate for $platform", platform !in blocked, billGate.allowsCandidate(platform))
        }
    }

    private fun platformsFor(policies: Map<DeviceRiskSignal, Int>): Set<AdPlatform> =
        if (1 in policies.values) AdPlatform.entries.toSet()
        else if (0 in policies.values) setOf(AdPlatform.ADMOB) else emptySet()

    private suspend fun eventually(condition: () -> Boolean) {
        withTimeout(3_000L) { while (!condition()) delay(10L) }
    }

    private fun allPolicies(policy: Int) = AdRiskSignalConfig(
        vpnAdConfig = policy, dnsAdConfig = policy, ipAdConfig = policy,
        simulatorAdConfig = policy, adbAdConfig = policy, simAdConfig = policy,
        noGpAdConfig = policy, sys9LowAdConfig = policy, playAdConfig = policy,
    )

    private data class Event(val name: String, val parameters: Map<String, Any>)

    private companion object {
        val EXPECTED_EVENTS = setOf(
            "Ad_Block_VPN", "Ad_Block_DNS", "Ad_Block_IP", "Ad_Block_Simulator", "Ad_Block_ADB",
            "Ad_Block_SIM", "Ad_Block_No_GP", "Ad_Block_Sys_9_Low", "Ad_Block_Play",
        )
    }
}

/**
 * Bill support_risk_1.0.11 只有公开插件安装 API，没有公开 getter。
 * 测试只读取该锁定版本的插件槽和平台候选 gate（当前 ShowExt/竞价缓存筛选都会调用它），
 * 从不替换插件或反射 SDK 策略状态。SDK 升级若改符号，应显式更新本测试，不能静默跳过。
 * 此类仅在 localRiskDebug 测试 APK，正式 APK/Google 产物不包含反射验证代码。
 */
private class InstalledBillRiskGate {
    private val plugin: AdRiskPlugin by lazy {
        val holder = Class.forName("e.b")
        val field = holder.declaredFields.single {
            Modifier.isStatic(it.modifiers) && AdRiskPlugin::class.java.isAssignableFrom(it.type)
        }
        field.isAccessible = true
        requireNotNull(field.get(null) as? AdRiskPlugin) { "AdRisk provider must install the real Bill plugin" }
    }
    private val candidateGate by lazy {
        Class.forName("e.a").getDeclaredMethod("a", AdPlatform::class.java)
    }

    fun isPlatformAllowed(platform: AdPlatform): Boolean = plugin.isPlatformAllowed(platform)
    fun allowsCandidate(platform: AdPlatform): Boolean = candidateGate.invoke(null, platform) as Boolean
}
