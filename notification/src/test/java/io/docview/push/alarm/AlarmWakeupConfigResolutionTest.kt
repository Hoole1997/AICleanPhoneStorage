package io.docview.push.alarm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验真实打包资产和配置来源，避免本地回退意外覆盖运营主动关闭的策略。 */
class AlarmWakeupConfigResolutionTest {
    private val defaultJson get() = File("src/main/assets/alarm_wakeup_config.json").readText()

    @Test fun packagedDefaultEnablesOnlyConfirmedPaidUsersEveryFifteenMinutes() {
        val resolved = resolveAlarmWakeupConfig(null, "", defaultJson)
        assertTrue(resolved.config.enabled)
        assertEquals(AlarmWakeupPolicy(true, 15), resolved.config.policyFor(confirmedPaid = true))
        assertEquals(AlarmWakeupPolicy(false, 15), resolved.config.policyFor(confirmedPaid = false))
        assertNull(resolved.remoteJson)
    }

    @Test fun unavailableOrUnpublishedRemoteUsesLocalDefaultWhenThereIsNoCache() {
        listOf(null, "", " ").forEach { remote ->
            val resolved = resolveAlarmWakeupConfig(remote, "", defaultJson)
            assertTrue(resolved.config.policyFor(confirmedPaid = true).enabled)
            assertFalse(resolved.config.policyFor(confirmedPaid = false).enabled)
            assertNull(resolved.remoteJson)
        }
    }

    @Test fun invalidRemoteUsesDefaultWithoutProducingCacheReplacement() {
        listOf("{", "encrypted:unreadable", "x".repeat(16_385), config(master = "true"))
            .forEach { remote ->
                val resolved = resolveAlarmWakeupConfig(remote, "", defaultJson)
                assertEquals(AlarmWakeupPolicy(true, 15), resolved.config.policyFor(confirmedPaid = true))
                assertNull(resolved.remoteJson)
            }
    }

    @Test fun validRemoteOverridesCachedAndLocalAudienceRulesAndIntervals() {
        val remote = config(paid = "0", standard = "1", interval = "45")
        val resolved = resolveAlarmWakeupConfig(remote, config(master = "0"), defaultJson)
        assertEquals(remote, resolved.remoteJson)
        assertEquals(AlarmWakeupPolicy(false, 45), resolved.config.policyFor(confirmedPaid = true))
        assertEquals(AlarmWakeupPolicy(true, 45), resolved.config.policyFor(confirmedPaid = false))
    }

    @Test fun explicitRemoteMasterOffOverridesEnabledCacheAndLocalDefault() {
        val remote = config(master = "0", paid = "1", standard = "1")
        val resolved = resolveAlarmWakeupConfig(remote, config(), defaultJson)
        assertEquals(remote, resolved.remoteJson)
        assertFalse(resolved.config.policyFor(confirmedPaid = true).enabled)
        assertFalse(resolved.config.policyFor(confirmedPaid = false).enabled)
    }

    @Test fun cachedExplicitMasterOrTierOffSurvivesRemoteUnavailabilityAndErrors() {
        listOf(config(master = "0", standard = "1"), config(paid = "0", standard = "0"))
            .forEach { cached ->
                listOf(null, "", " ", "{", config(master = "null")).forEach { remote ->
                    val resolved = resolveAlarmWakeupConfig(remote, cached, defaultJson)
                    assertFalse(resolved.config.policyFor(confirmedPaid = true).enabled)
                    assertFalse(resolved.config.policyFor(confirmedPaid = false).enabled)
                    assertNull(resolved.remoteJson)
                }
            }
    }

    @Test fun legacyRemoteAndCachedSchemasKeepTheirExplicitTierRules() {
        val legacy = """{"paid_tier":{"alarm_wakeup_enabled":0,"service_heartbeat_interval":30},"standard_tier":{"alarm_wakeup_enabled":1,"service_heartbeat_interval":30}}"""
        val remote = resolveAlarmWakeupConfig(legacy, "", defaultJson)
        val cached = resolveAlarmWakeupConfig(null, legacy, defaultJson)
        assertEquals(legacy, remote.remoteJson)
        assertNull(cached.remoteJson)
        listOf(remote, cached).forEach { resolved ->
            assertTrue(resolved.config.enabled)
            assertEquals(AlarmWakeupPolicy(false, 30), resolved.config.policyFor(confirmedPaid = true))
            assertEquals(AlarmWakeupPolicy(true, 30), resolved.config.policyFor(confirmedPaid = false))
        }
    }

    @Test fun emptyOrInvalidOldCacheCannotPreventLocalDefaultsFromEnablingPaidUsers() {
        listOf("", " ", "{}", "{", config(master = "2")).forEach { cached ->
            val resolved = resolveAlarmWakeupConfig(null, cached, defaultJson)
            assertEquals(AlarmWakeupPolicy(true, 15), resolved.config.policyFor(confirmedPaid = true))
            assertFalse(resolved.config.policyFor(confirmedPaid = false).enabled)
            assertNull(resolved.remoteJson)
        }
    }

    @Test fun corruptDefaultWithoutAnyValidSourceCannotScheduleEitherAudience() {
        val resolved = resolveAlarmWakeupConfig(null, "", "{")
        assertFalse(resolved.config.policyFor(confirmedPaid = true).enabled)
        assertFalse(resolved.config.policyFor(confirmedPaid = false).enabled)
        assertNull(resolved.remoteJson)
    }

    private fun config(master: String = "1", paid: String = "1", standard: String = "0", interval: String = "15") =
        """{"alarm_wakeup_enabled":$master,"paid_tier":{"alarm_wakeup_enabled":$paid,"service_heartbeat_interval":$interval},"standard_tier":{"alarm_wakeup_enabled":$standard,"service_heartbeat_interval":$interval}}"""
}
