package io.docview.push.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmWakeupConfigTest {
    @Test fun readsIndependentPaidAndStandardPolicies() {
        val config = parseAlarmWakeupConfig(config(paid = policy(enabled = "1", interval = "30")))
        assertEquals(AlarmWakeupPolicy(true, 30), config.paidTier)
        assertEquals(AlarmWakeupPolicy(false, 15), config.standardTier)
    }

    @Test fun onlyConfirmedPaidAudienceUsesPaidTier() {
        val config = parseAlarmWakeupConfig(config())
        assertTrue(config.policyFor(confirmedPaid = true).enabled)
        // 未确认归因和自然用户均使用 standard，不借 paid 默认值启动后台任务。
        assertFalse(config.policyFor(confirmedPaid = false).enabled)
    }

    @Test fun legacyConfigWithoutMasterSwitchKeepsTierRules() {
        val parsed = parseAlarmWakeupConfig(config())
        assertTrue(parsed.enabled)
        assertEquals(AlarmWakeupPolicy(true, 15), parsed.policyFor(confirmedPaid = true))
        assertEquals(AlarmWakeupPolicy(false, 15), parsed.policyFor(confirmedPaid = false))
    }

    @Test fun masterSwitchDisablesBothAudiencesWithoutLosingTheirIntervals() {
        val parsed = parseAlarmWakeupConfig(
            """{"alarm_wakeup_enabled":0,"paid_tier":${policy(interval = "30")},"standard_tier":${policy(interval = "60")}}""",
        )
        assertFalse(parsed.enabled)
        assertEquals(AlarmWakeupPolicy(false, 30), parsed.policyFor(confirmedPaid = true))
        assertEquals(AlarmWakeupPolicy(false, 60), parsed.policyFor(confirmedPaid = false))
    }

    @Test fun enabledMasterSwitchStillRespectsAudienceSwitches() {
        val parsed = parseAlarmWakeupConfig(
            """{"alarm_wakeup_enabled":1,"paid_tier":${policy(enabled = "0")},"standard_tier":${policy(interval = "20")}}""",
        )
        assertTrue(parsed.enabled)
        assertFalse(parsed.policyFor(confirmedPaid = true).enabled)
        assertEquals(AlarmWakeupPolicy(true, 20), parsed.policyFor(confirmedPaid = false))
    }

    @Test fun rejectsInvalidAndRepeatedMasterSwitches() {
        listOf("2", "-1", "true", "false", "\"1\"", "null", "1.0", "1e0", "[]", "{}")
            .forEach { value ->
                assertDisabled("""{"alarm_wakeup_enabled":$value,"paid_tier":${policy()},"standard_tier":${policy()}}""")
            }
        assertDisabled(
            """{"alarm_wakeup_enabled":1,"alarm_wakeup_enabled":0,"paid_tier":${policy()},"standard_tier":${policy()}}""",
        )
    }

    @Test fun missingIntervalUsesFifteenMinutes() {
        val config = parseAlarmWakeupConfig(config(paid = """{"alarm_wakeup_enabled":1}"""))
        assertEquals(AlarmWakeupPolicy(true, 15), config.paidTier)
    }

    @Test fun clampsIntegerIntervalsWithoutOverflow() {
        val intervals = mapOf(
            "-1" to 15, "0" to 15, "14" to 15, "15" to 15,
            "1440" to 1_440, "1441" to 1_440,
            "999999999999999999999999999999999999" to 1_440,
            "-999999999999999999999999999999999999" to 15,
        )
        intervals.forEach { (remote, expected) ->
            val parsed = parseAlarmWakeupConfig(config(paid = policy(interval = remote)))
            assertEquals(remote, expected, parsed.paidTier.intervalMinutes)
            assertTrue(remote, parsed.paidTier.enabled)
        }
    }

    @Test fun rejectsNonIntegerTypesInsteadOfCoercingRemoteValues() {
        val invalid = listOf("\"15\"", "true", "false", "null", "15.0", "1.5", "1e2", "[]", "{}")
        invalid.forEach { value ->
            assertDisabled(config(paid = policy(interval = value)))
            assertDisabled(config(paid = policy(enabled = value)))
        }
    }

    @Test fun rejectsMissingAndInvalidSwitchesOrTiersForWholeConfig() {
        listOf(
            config(paid = """{"service_heartbeat_interval":15}"""),
            config(paid = policy(enabled = "2")),
            config(standard = policy(enabled = "-1")),
            """{"paid_tier":${policy()}}""",
            """{"standard_tier":${policy()}}""",
            """{"premium_tier":${policy()},"standard_tier":${policy()}}""",
            config(paid = "null"), config(standard = "[]"),
        ).forEach(::assertDisabled)
    }

    @Test fun rejectsAmbiguousRepeatedFieldsAndTrailingData() {
        listOf(
            """{"paid_tier":${policy()},"paid_tier":${policy()},"standard_tier":${policy()}}""",
            config(paid = """{"alarm_wakeup_enabled":1,"alarm_wakeup_enabled":0}"""),
            config(paid = """{"alarm_wakeup_enabled":1,"service_heartbeat_interval":15,"service_heartbeat_interval":30}"""),
            config() + "{}",
            config().replace("\"paid_tier\"", "paid_tier"),
            "/* commented config */" + config(),
        ).forEach(::assertDisabled)
    }

    @Test fun missingMalformedEncryptedAndOversizedPayloadsAreDisabled() {
        listOf("", " ", "null", "[]", "{}", "{", "encrypted:unreadable", "x".repeat(16_385))
            .forEach(::assertDisabled)
    }

    @Test fun acceptsUnknownFieldsForForwardCompatibleRemoteSchema() {
        val futurePolicy = """{"alarm_wakeup_enabled":1,"service_heartbeat_interval":20,"future":{"value":true}}"""
        val parsed = parseAlarmWakeupConfig("""{"paid_tier":$futurePolicy,"standard_tier":${policy(enabled = "0")},"revision":2}""")
        assertEquals(AlarmWakeupPolicy(true, 20), parsed.paidTier)
    }

    private fun assertDisabled(json: String) {
        assertEquals(json.take(160), AlarmWakeupConfig(), parseAlarmWakeupConfig(json))
    }

    private fun config(paid: String = policy(), standard: String = policy(enabled = "0")) =
        """{"paid_tier":$paid,"standard_tier":$standard}"""

    private fun policy(enabled: String = "1", interval: String = "15") =
        """{"alarm_wakeup_enabled":$enabled,"service_heartbeat_interval":$interval}"""
}
