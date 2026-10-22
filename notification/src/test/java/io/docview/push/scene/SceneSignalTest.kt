package io.docview.push.scene

import org.junit.Assert.*
import org.junit.Test

class SceneSignalTest {
    private fun time(ms: Long) = SceneMoment(ms + 1_000_000, ms, 1)
    private fun battery(percent: Int, plugged: Boolean = false, full: Boolean = false) = SceneBattery(percent, plugged, plugged && !full, full)
    @Test fun initialStickyStateDoesNotInventEventsAndConnectionBounceIsSuppressed() {
        val rules = BatterySceneRules()
        assertTrue(rules.update(battery(50), time(0)).events.isEmpty())
        assertEquals(PushScene.CHARGE_START, rules.update(battery(50, true), time(1)).events.single().scene)
        assertTrue(rules.update(battery(50), time(1_000)).events.isEmpty())
        assertTrue(rules.update(battery(50, true), time(2_000)).events.isEmpty())
    }
    @Test fun pluggedThenChargingUpdatesDoNotReplaceSameConnectionSession() {
        val rules = BatterySceneRules()
        rules.update(battery(50), time(0))
        val start = rules.update(SceneBattery(50, true, false, false), time(100)).events.single()
        assertTrue(rules.valid(start))
        assertTrue(rules.update(battery(50, true), time(200)).events.isEmpty())
        assertTrue("同一次插电的后续充电状态不能使原触发事件失效", rules.valid(start))
    }
    @Test fun connectionDebounceRejectsBelowOneMinuteAndAcceptsExactBoundary() {
        for (elapsed in listOf(59_999L, 60_000L, 120_000L)) {
            val rules = BatterySceneRules()
            rules.update(battery(50), time(0))
            rules.update(battery(50, true), time(100))
            rules.update(battery(50), time(200))
            val reconnect = rules.update(battery(50, true), time(100 + elapsed))
            assertEquals("重连间隔=$elapsed", if (elapsed < 60_000L) 0 else 1,
                reconnect.events.count { it.scene == PushScene.CHARGE_START })
        }
    }
    @Test fun chargingBeforePluggedUpdateKeepsOriginalSessionAndDuration() {
        val rules = BatterySceneRules()
        rules.update(battery(50), time(0))
        val start = rules.update(SceneBattery(50, false, true, false), time(100)).events.single()
        rules.update(battery(50, true), time(300))
        assertTrue(rules.valid(start))
        val end = rules.update(battery(70), time(120_100)).events.single()
        assertEquals(120_000L, end.chargeDurationMs)
    }
    @Test fun unplugUsesOneMinuteDelayAndReconnectInvalidatesPendingEnd() {
        val rules = BatterySceneRules()
        rules.update(battery(40), time(0)); rules.update(battery(40, true), time(10))
        val end = rules.update(battery(60), time(120_010)).events.single()
        assertFalse(end.fullCharge); assertEquals(120_000L, end.chargeDurationMs)
        assertEquals(180_010L, end.dueElapsed); assertTrue(rules.valid(end))
        val reconnect = rules.update(battery(60, true), time(130_000))
        assertTrue(PushScene.CHARGE_END in reconnect.cancel); assertFalse(rules.valid(end))
    }
    @Test fun reachingFullIsRealCompletionAndDoesNotEmitAgainOnUnplug() {
        val rules = BatterySceneRules()
        rules.update(battery(90, true), time(0))
        val end = rules.update(battery(100, true, true), time(120_000)).events.single()
        assertTrue(end.fullCharge); assertTrue(rules.valid(end))
        assertTrue(rules.update(battery(100), time(180_000)).events.isEmpty())
    }
    @Test fun lowBatteryHasTwoCrossingsAndChargingOrOkayCancels() {
        val rules = BatterySceneRules()
        rules.update(battery(30), time(0))
        assertEquals(20, rules.update(battery(20), time(1)).events.single().batteryPercent)
        assertTrue(rules.update(battery(19), time(2)).events.isEmpty())
        assertEquals(10, rules.update(battery(10), time(3)).events.single().batteryPercent)
        assertTrue(rules.update(battery(9), time(4)).events.isEmpty())
        assertTrue(PushScene.LOW_BATTERY in rules.update(battery(9, true), time(5)).cancel)
        assertFalse(rules.valid(SceneEvent(PushScene.LOW_BATTERY, 1, 1)))
    }
    @Test fun networkOnlySchedulesMeaningfulStableChangesAndIgnoresRoamingOrVpn() {
        val rules = NetworkSceneRules(SceneNetworkState(SceneNetwork.WIFI, "wifi1"))
        assertTrue(rules.update(SceneNetworkState(SceneNetwork.WIFI, "wifi2"), time(0)).events.isEmpty())
        val mobile = rules.update(SceneNetworkState(SceneNetwork.MOBILE, "cell1"), time(1)).events.single()
        assertEquals(60_001L, mobile.dueElapsed)
        val offline = rules.update(SceneNetworkState(SceneNetwork.OFFLINE), time(2)).events.single()
        assertEquals(60_002L, offline.dueElapsed); assertFalse(rules.valid(mobile))
        assertTrue(rules.update(SceneNetworkState(SceneNetwork.VPN, "vpn"), time(3)).events.isEmpty())
        assertTrue(rules.update(SceneNetworkState(SceneNetwork.WIFI, "wifi3"), time(4)).events.isEmpty())
    }
    @Test fun metadataRulesExcludePendingRemovedPrivateOwnAndTemporaryFiles() {
        val shot = SceneMedia("content://media/external/images/media/1", "s.png", "Pictures/Screenshots/", "image/png", 100, 1, 1, false, false, "other")
        assertTrue(SceneMediaPolicy.eligible(shot, PushScene.SCREENSHOT, "self"))
        listOf(shot.copy(pending=true),shot.copy(trashed=true),shot.copy(owner="self"),shot.copy(bytes=0),shot.copy(path="Android/data/a/Pictures/Screenshots/"),shot.copy(mime="video/mp4"))
            .forEach { assertFalse(SceneMediaPolicy.eligible(it, PushScene.SCREENSHOT, "self")) }
        val download = shot.copy(path="Download/",name="report.pdf",mime="application/pdf")
        assertTrue(SceneMediaPolicy.eligible(download, PushScene.DOWNLOAD,"self"))
        assertFalse(SceneMediaPolicy.eligible(download.copy(name="report.part"),PushScene.DOWNLOAD,"self"))
        assertFalse(SceneMediaPolicy.eligible(download.copy(path="Download/admob/"),PushScene.DOWNLOAD,"self"))
    }
    @Test fun briefLostCallbacksDuringVpnAndWifiRoamingDoNotBecomeNewNetworkAlerts() {
        val vpn = NetworkSceneRules(SceneNetworkState(SceneNetwork.VPN, "vpn"))
        assertEquals(PushScene.NETWORK, vpn.update(SceneNetworkState(SceneNetwork.OFFLINE), time(0)).events.single().scene)
        assertTrue(vpn.update(SceneNetworkState(SceneNetwork.WIFI, "underlying"), time(100)).events.isEmpty())
        val wifi = NetworkSceneRules(SceneNetworkState(SceneNetwork.WIFI, "old_ap"))
        wifi.update(SceneNetworkState(SceneNetwork.OFFLINE), time(0))
        assertTrue(wifi.update(SceneNetworkState(SceneNetwork.WIFI, "roamed_ap"), time(100)).events.isEmpty())
        wifi.update(SceneNetworkState(SceneNetwork.OFFLINE), time(1000))
        assertEquals(PushScene.NETWORK, wifi.update(SceneNetworkState(SceneNetwork.WIFI, "new_connection"), time(180_000)).events.single().scene)
    }
    @Test fun smallDownloadsAggregateWithinThirtyMinutesAndDoNotDuplicateUris() {
        val batch=DownloadSceneBatch()
        fun event(uri:String,time:Long,bytes:Long)=SceneEvent(PushScene.DOWNLOAD,time,0,uri,bytes=bytes)
        assertNull(batch.add(event("a",0,100)))
        assertNull(batch.add(event("a",1,100)))
        assertEquals(2,batch.add(event("b",2,100))!!.fileCount)
        assertNull(batch.add(event("c",1_800_003,100)))
        batch.clear()
        assertNull(batch.add(event("exact_boundary",1_800_003,100_000_000)))
        batch.clear()
        assertNotNull(batch.add(event("large",1_800_004,100_000_001)))
    }
    @Test fun wifiLostBeforeCellularCallbackStillSchedulesStableMobileChange() {
        val rules = NetworkSceneRules(SceneNetworkState(SceneNetwork.WIFI, "wifi"))
        val offline = rules.update(SceneNetworkState(SceneNetwork.OFFLINE), time(0)).events.single()
        val mobile = rules.update(SceneNetworkState(SceneNetwork.MOBILE, "cell"), time(100)).events.single()
        assertEquals(SceneNetwork.MOBILE, mobile.network)
        assertEquals(60_100L, mobile.dueElapsed)
        assertFalse(rules.valid(offline))
        assertTrue(rules.valid(mobile))
    }
}
