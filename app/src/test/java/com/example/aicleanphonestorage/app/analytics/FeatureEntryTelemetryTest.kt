package com.example.aicleanphonestorage.app.analytics

import com.example.aicleanphonestorage.core.analytics.BusinessPageNames
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureEntryTelemetryTest {
    @Test fun allHomeEntriesReportTheDocumentBadgeEnums() {
        val entries = mapOf(
            "junk" to "junk",
            "malware" to "MalwareScan",
            "traffic" to "traffic",
            "notify" to "notify",
            "apps" to "apps",
            "photo" to "photo",
            "large" to "large",
            "unused" to "unused",
            "screenshots" to "screenshots",
            "video" to "vedio",
            "duplicate" to "duplicatephoto",
            "battery" to "BatteryInfo",
            "speedtest" to "speedtest",
        )
        entries.forEach { (entry, wireEntry) ->
            assertEquals(mapOf("entry" to wireEntry, "home_badge" to "yes"), FeatureTelemetry.entryParameters(entry, true))
            assertEquals(mapOf("entry" to wireEntry, "home_badge" to "no"), FeatureTelemetry.entryParameters(entry, false))
        }
    }

    @Test fun speedTestEntryAndPageKeepTheirDifferentDocumentEnums() {
        assertEquals("speedtest", FeatureTelemetry.entryParameters("speedtest", true)["entry"])
        assertEquals("speedtestScan", BusinessPageNames.wire("speedtest"))
    }
}
