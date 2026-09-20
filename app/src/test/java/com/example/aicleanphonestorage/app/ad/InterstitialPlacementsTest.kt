package com.example.aicleanphonestorage.app.ad

import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import org.junit.Assert.*
import org.junit.Test

/** 固定业务到总表的对应关系，防止重构枚举名称时静默改变后台 Key。 */
class InterstitialPlacementsTest {
    @Test fun confirmationAndHomeSlotsKeepTheSameFeatureIdentity() {
        val expected = mapOf(
            CleanupFeature.SIMILAR_PHOTOS to ("clean_confirm_duplicate" to "back_home_duplicate"),
            CleanupFeature.VIDEOS to ("clean_confirm_video" to "back_home_video"),
            CleanupFeature.SMART_CLEAN to ("clean_confirm_junk" to "back_home_junk"),
            CleanupFeature.SCREENSHOTS to ("clean_confirm_screenshots" to "back_home_screenshots"),
            CleanupFeature.PHOTO_COMPRESS to ("clean_confirm_photo" to "back_home_photo"),
            CleanupFeature.LARGE_FILES to ("clean_confirm_large" to "back_home_large"),
            CleanupFeature.UNUSED_FILES to ("clean_confirm_unused" to "back_home_unused"),
        )
        assertEquals(CleanupFeature.entries.toSet(), expected.keys)
        expected.forEach { (feature, slots) ->
            assertEquals(slots.first, InterstitialPlacements.clean(feature))
            assertEquals(slots.second, InterstitialPlacements.exit(feature))
        }
        assertEquals(setOf(
            "back_home_duplicate", "back_home_video", "back_home_junk", "back_home_screenshots", "back_home_photo", "back_home_large",
            "back_home_unused", "back_home_notify", "back_home_apps", "back_home_network", "back_home_malware",
        ), InterstitialPlacements.homeExits)
    }

    @Test fun successfulScanSlotsMatchAllElevenDocumentKeys() {
        val fileSlots = CleanupFeature.entries.map(InterstitialPlacements::scan)
        val slots = fileSlots + listOf(InterstitialPlacements.NOTIFICATIONS_SCAN,
            InterstitialPlacements.APPS_SCAN, InterstitialPlacements.NETWORK_SCAN,
            InterstitialPlacements.MALWARE_SCAN)
        assertEquals(11, slots.size)
        assertEquals(setOf("scan_complete_junk", "scan_complete_screenshots", "scan_complete_photo",
            "scan_complete_large", "scan_complete_unused", "scan_complete_notify", "scan_complete_apps",
            "scan_complete_network", "scan_complete_video", "scan_complete_duplicate", "scan_complete_virus"), slots.toSet())
        // 文档刻意使用不同后缀：病毒返回位是 malware，扫描完成位是 virus。
        assertEquals("back_home_malware", InterstitialPlacements.MALWARE_EXIT)
    }

    @Test fun missingSourceDoesNotInventOrMisattributeASlot() {
        assertNull(InterstitialPlacements.clean(null))
        assertNull(InterstitialPlacements.exit(null))
        assertFalse(InterstitialPlacements.homeExits.contains(InterstitialPlacements.NETWORK_CONFIRM))
    }
}
