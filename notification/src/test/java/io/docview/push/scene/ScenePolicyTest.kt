package io.docview.push.scene

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScenePolicyTest {
    private fun asset(name: String): String = listOf(File("src/main/assets/$name"), File("notification/src/main/assets/$name"))
        .first { it.isFile }.readText()

    @Test fun localDefaultsHaveEightIndependentMinuteIntervalsAndUniqueIds() {
        val policy = SceneConfig.policy(asset("scene_push_config.json"))
        assertTrue(policy.enabled)
        assertEquals((1101..1108).toList(), PushScene.entries.map { it.code })
        assertEquals(8, PushScene.entries.map { it.notificationId }.toSet().size)
        assertTrue(PushScene.entries.all { it.notificationId !in 10000..10008 })
        PushScene.entries.forEach { assertEquals(600_000L, policy.rules.getValue(it).intervalMs) }
    }
    @Test fun invalidScalarIntervalsFallBackWithoutCoercionAndExplicitSwitchOffWins() {
        for (raw in listOf("null", "0", "-1", "1.2", "1.0", "\"2\"", "true", "[]", "999999999999999999999999")) {
            val result = SceneConfig.policy("""{"chargeStartPushIntervalMinutes":$raw,"appInstallPushIntervalMinutes":2,"scenes":{"1107":{"enabled":false}}}""")
            assertEquals(raw, 10, result.rules.getValue(PushScene.CHARGE_START).intervalMinutes)
            assertEquals(2, result.rules.getValue(PushScene.APP_INSTALL).intervalMinutes)
            assertFalse(result.rules.getValue(PushScene.APP_UNINSTALL).enabled)
        }
        assertFalse(SceneConfig.policy("""{"enabled":false}""").enabled)
        for (raw in listOf("{enabled:false}", "[]", "{} trailing")) assertTrue(runCatching { SceneConfig.policy(raw) }.isFailure)
    }
    @Test fun requiredCopyAndPartialChargeFallbacksArePresent() {
        val copies = SceneCopies.parse(asset("scene_push_content.json"))
        assertEquals(27, copies.values.sumOf { it.size })
        val full = SceneEvent(PushScene.CHARGE_END, 1, 1, fullCharge = true)
        val partial = full.copy(fullCharge = false)
        assertEquals("<font color=\"#FB4949\"><b>Fully charged!</b></font> Check health.", SceneCopies.select(copies.getValue(full.scene), "", full, "en")!!.text.text)
        assertEquals("<font color=\"#FB4949\"><b>Charging ended!</b></font> View status.", SceneCopies.select(copies.getValue(full.scene), "", partial, "en")!!.text.text)
        assertEquals("<font color=\"#FB4949\"><b>New download!</b></font> Manage files.", copies.getValue(PushScene.DOWNLOAD).first().text)
    }
    @Test fun cursorIsPerArrayAndDeletedIdsRestartAtFirstAvailableCopy() {
        val copies = SceneCopies.parse(asset("scene_push_content.json")).getValue(PushScene.APP_INSTALL)
        val event = SceneEvent(PushScene.APP_INSTALL, 1, 1)
        var id = ""
        val actual = List(4) { SceneCopies.select(copies, id, event, "en")!!.also { id = it.id }.id }
        assertEquals(listOf("1106_1", "1106_2", "1106_3", "1106_1"), actual)
        assertEquals("1106_1", SceneCopies.select(copies, "removed_id", event, "en")!!.id)
    }
    @Test fun unsafeOrOversizedHtmlIsRejectedAndMissingVariablesAreSkipped() {
        val raw = """{"1106":[{"id":"variable","text":"<b>{app_name}</b> Check safety.","buttonText":"Check"},{"id":"fallback","text":"<b>New app!</b> Review safety.","buttonText":"Review"},{"id":"bad","text":"<script>x</script> Hi","buttonText":"View"}]}"""
        val copies = SceneCopies.parse(raw).getValue(PushScene.APP_INSTALL)
        assertEquals(2, copies.size)
        val event = SceneEvent(PushScene.APP_INSTALL, 1, 1)
        assertEquals("fallback", SceneCopies.select(copies, "", event, "en")!!.id)
        val title = SceneCopies.select(copies, "", event.copy(displayName = "A < B"), "en")!!.text.text
        assertTrue(title.contains("&lt;"))
    }
    @Test fun onlySingleRichTextFieldIsAcceptedAndTranslationsUseSameSchema() {
        val split = """{"1101":[{"id":"old","title":"Charging started!","desc":"View status.","buttonText":"View"}]}"""
        assertTrue(runCatching { SceneCopies.parse(split) }.isFailure)
        val raw = """{"1101":[{"id":"one","text":"<b>Charging started!</b> View status.","buttonText":"View","translations":{"zh":{"text":"<b>开始充电！</b> 查看状态。","buttonText":"查看"}}}]}"""
        val copies = SceneCopies.parse(raw).getValue(PushScene.CHARGE_START)
        assertEquals("<b>Charging started!</b> View status.", copies.single().text("en").text)
        assertEquals("<b>开始充电！</b> 查看状态。", copies.single().text("zh-CN").text)
    }
    @Test fun truthfulBatteryAndNetworkVariantsRespectObservedState() {
        val copies = SceneCopies.parse(asset("scene_push_content.json"))
        val low = SceneEvent(PushScene.LOW_BATTERY, 1, 1, batteryPercent = 19)
        assertEquals("1103_1", SceneCopies.select(copies.getValue(low.scene), "1103_1", low, "en")!!.id)
        assertEquals("1103_3", SceneCopies.select(copies.getValue(low.scene), "1103_1", low.copy(batteryPercent = 9), "en")!!.id)
        val network = SceneEvent(PushScene.NETWORK, 1, 1, network = SceneNetwork.WIFI)
        assertEquals("1104_3", SceneCopies.select(copies.getValue(network.scene), "1104_1", network, "en")!!.id)
        val connectedFull = SceneEvent(PushScene.CHARGE_START, 1, 1, fullCharge = true, charging = false, plugged = true)
        assertEquals("1101_2", SceneCopies.select(copies.getValue(PushScene.CHARGE_START), "", connectedFull, "en")!!.id)
        val stoppedButPlugged = SceneEvent(PushScene.CHARGE_END, 1, 1, fullCharge = false, plugged = true)
        assertEquals("1102_6", SceneCopies.select(copies.getValue(PushScene.CHARGE_END), "1102_4", stoppedButPlugged, "en")!!.id)
    }
    @Test fun clockRollbackAndRestartCannotOverflowOrBypassCooldown() {
        val history = SceneHistory(100_000, 10_000, 1, "copy")
        assertEquals(590_000L, history.remaining(SceneMoment(1, 20_000, 1), 600_000))
        assertEquals(600_000L, history.remaining(SceneMoment(1, 1, 2), 600_000))
        assertEquals(0L, SceneHistory().remaining(SceneMoment(1, 1, 2), 600_000))
    }
}
