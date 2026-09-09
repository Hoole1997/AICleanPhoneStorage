package io.docview.push.config

import org.junit.Assert.*
import org.junit.Test

class ConfigProtocolTest {
    private val tier = """{"max_notification_limit":3,"screen_unlock_delay":"10","background_trigger_delay":"10","repeat_notification_enabled":0,"repeat_cycle_count":0,"fresh_install_grace_period":"24","quiet_hours_start":"02:00","quiet_hours_end":"08:00","push_feature_enabled":1,"service_heartbeat_interval":15}"""

    @Test fun readsNewTierNamesAndNumericStrings() {
        val parsed = parseNotificationConfig("""{"premium_tier":$tier,"standard_tier":$tier}""")
        assertEquals(3, parsed.organicChannel.totalPushCount)
        assertEquals(10, parsed.organicChannel.unlockPushInterval)
        assertEquals(24, parsed.organicChannel.newUserCooldown)
        assertEquals(15, parsed.organicChannel.keepalivePollingIntervalMinutes)
    }

    @Test(expected = com.google.gson.JsonSyntaxException::class)
    fun rejectsOldSchemaInsteadOfActivatingNullTiers() {
        parseNotificationConfig("""{"paid_channel":$tier,"organic_channel":$tier}""")
    }

    @Test(expected = com.google.gson.JsonSyntaxException::class)
    fun rejectsMalformedQuietHours() {
        parseNotificationConfig("""{"premium_tier":$tier,"standard_tier":$tier}""".replace("02:00", "bad"))
    }
}
