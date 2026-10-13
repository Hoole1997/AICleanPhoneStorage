package com.example.aicleanphonestorage.app.risk

import com.android.common.bill.ads.config.AdPlatform
import com.android.common.risk.AdRestrictionScope
import com.android.common.risk.AdRiskSignalConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRiskPolicyBridgeTest {
    @Test fun `aggregate overlapping risks includes MAX without clearing remaining ADMOB risk`() = runTest {
        val configs = MutableStateFlow(AdRiskSignalConfig(vpnAdConfig = 0, dnsAdConfig = 1))
        val sdk = Restrictions()
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN, DeviceRiskSignal.DNS)))
        runCurrent()
        assertTrue(bridge.awaitSynchronized())
        assertEquals(AdPlatform.entries.toSet(), sdk.blocked)
        assertTrue(AdPlatform.MAX in sdk.blocked)
        assertEquals(mapOf(DeviceRiskSignal.VPN to 0, DeviceRiskSignal.DNS to 1), bridge.blockedSignals())

        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN)))
        runCurrent()
        assertEquals(setOf(AdPlatform.ADMOB), sdk.blocked)
        assertFalse(sdk.calls.any { it.platform == AdPlatform.ADMOB && !it.restricted })
    }

    @Test fun `remote configuration can enable and release saved signals without new detection`() = runTest {
        val configs = MutableStateFlow(AdRiskSignalConfig())
        val sdk = Restrictions()
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.IP)))
        runCurrent()
        assertTrue(sdk.blocked.isEmpty())

        configs.value = AdRiskSignalConfig(ipAdConfig = 1)
        runCurrent()
        assertEquals(AdPlatform.entries.toSet(), sdk.blocked)
        configs.value = AdRiskSignalConfig(ipAdConfig = -1)
        runCurrent()
        assertTrue(sdk.blocked.isEmpty())
        assertTrue(bridge.blockedSignals().isEmpty())
    }

    @Test fun `all supported detection dimensions use their corresponding policy`() = runTest {
        val configs = MutableStateFlow(AdRiskSignalConfig())
        val sdk = Restrictions()
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(DeviceRiskSignal.entries.toSet()))
        listOf(
            DeviceRiskSignal.VPN to AdRiskSignalConfig(vpnAdConfig = 0),
            DeviceRiskSignal.DNS to AdRiskSignalConfig(dnsAdConfig = 0),
            DeviceRiskSignal.IP to AdRiskSignalConfig(ipAdConfig = 0),
            DeviceRiskSignal.SIMULATOR to AdRiskSignalConfig(simulatorAdConfig = 0),
            DeviceRiskSignal.ADB to AdRiskSignalConfig(adbAdConfig = 0),
            DeviceRiskSignal.NO_SIM to AdRiskSignalConfig(simAdConfig = 0),
            DeviceRiskSignal.NO_GP to AdRiskSignalConfig(noGpAdConfig = 0),
            DeviceRiskSignal.SYS_9_LOW to AdRiskSignalConfig(sys9LowAdConfig = 0),
            DeviceRiskSignal.PLAY to AdRiskSignalConfig(playAdConfig = 0),
        ).forEach { (signal, config) ->
            configs.value = config
            runCurrent()
            assertEquals(mapOf(signal to 0), bridge.blockedSignals())
            assertEquals(setOf(AdPlatform.ADMOB), sdk.blocked)
        }
    }

    @Test fun `initial gate waits until signal configuration is available`() = runTest {
        val configs = MutableSharedFlow<AdRiskSignalConfig>(replay = 1)
        val sdk = Restrictions()
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.PLAY)))
        val synchronized = async { bridge.awaitSynchronized() }
        runCurrent()
        assertFalse(synchronized.isCompleted)
        assertTrue(sdk.calls.isEmpty())
        configs.emit(AdRiskSignalConfig(playAdConfig = 0))
        runCurrent()
        assertTrue(synchronized.await())
        assertEquals(setOf(AdPlatform.ADMOB), sdk.blocked)
    }

    @Test fun `initial gate waits for successful SDK retry`() = runTest {
        val configs = MutableStateFlow(AdRiskSignalConfig(vpnAdConfig = 0))
        var rejected = 2
        val sdk = Restrictions { _, _ -> rejected-- <= 0 }
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN)))
        val synchronized = async { bridge.awaitSynchronized() }
        runCurrent()
        assertFalse(synchronized.isCompleted)
        assertTrue(bridge.blockedSignals().isEmpty())
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(synchronized.await())
        assertEquals(3, sdk.calls.size)
        assertEquals(mapOf(DeviceRiskSignal.VPN to 0), bridge.blockedSignals())
    }

    @Test fun `failed SDK synchronization is bounded and not reported as success`() = runTest {
        val configs = MutableStateFlow(AdRiskSignalConfig(vpnAdConfig = 0))
        val sdk = Restrictions { _, _ -> false }
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN)))
        val synchronized = async { bridge.awaitSynchronized() }
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(synchronized.await())
        assertEquals(5, sdk.calls.size)
        assertTrue(bridge.blockedSignals().isEmpty())
    }

    @Test fun `new policy cancels stale clearing retries and gate follows new target`() = runTest {
        val configs = MutableStateFlow(AdRiskSignalConfig(vpnAdConfig = 1))
        val sdk = Restrictions { platform, restricted -> platform != AdPlatform.MAX || restricted }
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN)))
        runCurrent()

        configs.value = AdRiskSignalConfig(vpnAdConfig = 0)
        val synchronized = async { bridge.awaitSynchronized() }
        runCurrent()
        assertFalse(synchronized.isCompleted)
        assertEquals(1, sdk.calls.count { it.platform == AdPlatform.MAX && !it.restricted })
        configs.value = AdRiskSignalConfig(vpnAdConfig = 1)
        runCurrent()
        assertTrue(synchronized.await())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, sdk.calls.count { it.platform == AdPlatform.MAX && !it.restricted })
        assertEquals(AdPlatform.entries.toSet(), sdk.blocked)
    }

    @Test fun `unavailable releases owned restrictions immediately and cancels late blocking retries`() = runTest {
        val configs = MutableStateFlow(AdRiskSignalConfig(vpnAdConfig = 0))
        var accepting = true
        val sdk = Restrictions { _, _ -> accepting }
        // 另一个来源原有的 MAX 标志不属于本桥接，失败释放不应盲目清全部平台。
        sdk.blocked.add(AdPlatform.MAX)
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN)))
        runCurrent()
        bridge.releaseForUnavailable()
        assertEquals(setOf(AdPlatform.MAX), sdk.blocked)
        assertFalse(sdk.calls.any { it.platform == AdPlatform.MAX })

        accepting = false
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN)))
        runCurrent()
        val attempts = sdk.calls.size
        bridge.releaseForUnavailable()
        accepting = true
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(attempts, sdk.calls.size)
        assertEquals(setOf(AdPlatform.MAX), sdk.blocked)
        assertTrue(bridge.blockedSignals().isEmpty())
    }

    @Test fun `failed immediate release shares the bounded retry budget`() = runTest {
        val configs = MutableStateFlow(AdRiskSignalConfig(vpnAdConfig = 0))
        val sdk = Restrictions { _, restricted -> restricted }
        val bridge = DeviceRiskPolicyBridge(backgroundScope, configs, sdk::update)
        bridge.apply(DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN)))
        runCurrent()
        bridge.releaseForUnavailable()
        assertEquals(1, sdk.calls.count { !it.restricted })
        val synchronized = async { bridge.awaitSynchronized() }
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(synchronized.await())
        assertEquals(5, sdk.calls.count { !it.restricted })
        assertTrue(bridge.blockedSignals().isEmpty())
    }

    private data class Update(val platform: AdPlatform, val restricted: Boolean)

    private class Restrictions(private val accept: (AdPlatform, Boolean) -> Boolean = { _, _ -> true }) {
        val blocked = mutableSetOf<AdPlatform>()
        val calls = mutableListOf<Update>()

        fun update(platform: AdPlatform, scope: AdRestrictionScope, restricted: Boolean): Boolean {
            assertEquals(AdRestrictionScope.PROCESS, scope)
            calls.add(Update(platform, restricted))
            if (!accept(platform, restricted)) return false
            if (restricted) blocked.add(platform) else blocked.remove(platform)
            return true
        }
    }
}
