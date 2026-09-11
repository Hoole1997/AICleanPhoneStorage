package io.docview.push.config

import io.docview.push.NotificationDestination
import org.junit.Assert.*
import org.junit.Test

class ContentBusinessContractTest {
    private fun json(action: Int, icon: Int = action) =
        """{"id":"test","title":"Review","desc":"Choose files","buttonText":"Open","actionType":$action,"iconType":$icon}"""

    @Test fun everyConfiguredTypeResolvesToTheCorrespondingBusiness() {
        val expected = mapOf(
            Content.TYPE_CLEAN to NotificationDestination.CLEAN,
            Content.TYPE_NETWORK to NotificationDestination.NETWORK,
            Content.TYPE_PHOTO_COMPRESS to NotificationDestination.PHOTOS,
            Content.TYPE_UNUSED_FILES to NotificationDestination.UNUSED_FILES,
            Content.TYPE_SCREENSHOTS to NotificationDestination.SCREENSHOTS,
            Content.TYPE_HOME to NotificationDestination.HOME,
            Content.TYPE_LARGE_FILES to NotificationDestination.LARGE_FILES,
            Content.TYPE_NOTIFICATION_CLEANER to NotificationDestination.NOTIFICATION_CLEANER,
            Content.TYPE_APP_MANAGER to NotificationDestination.APP_MANAGER,
        )
        for ((type, destination) in expected) {
            val content = parsePushContents("[${json(type)}]").single()
            assertEquals(destination, content.destination)
            assertEquals(destination, content.iconDestination)
            assertEquals(destination, NotificationDestination.fromKey(destination.key))
        }
    }

    @Test fun missingOrUnsupportedIconUsesTheActionIcon() {
        val content = parsePushContents("[${json(Content.TYPE_SCREENSHOTS, 999)}]").single()
        assertEquals(Content.TYPE_SCREENSHOTS, content.iconType)
        assertEquals(NotificationDestination.SCREENSHOTS, content.iconDestination)
    }

    @Test fun unsupportedBrowserActionsAreFilteredWithoutReinterpretingTheirTargets() {
        val content = parsePushContents("[null,${json(7)},${json(8)},${json(12)},${json(Content.TYPE_NETWORK)}]")
        assertEquals(listOf(NotificationDestination.NETWORK), content.map { it.destination })
        assertEquals(NotificationDestination.HOME, NotificationDestination.fromContentType(12))
    }

    @Test fun shippedContentUsesOnlySupportedActionsAndIcons() {
        val contents = parsePushContents(java.io.File("src/main/assets/pvvvvush_content_config.json").readText())
        assertEquals(103, contents.size)
        assertEquals(contents.size, contents.map { it.id }.toSet().size)
        assertEquals(setOf(NotificationDestination.CLEAN, NotificationDestination.LARGE_FILES,
            NotificationDestination.PHOTOS, NotificationDestination.UNUSED_FILES, NotificationDestination.HOME,
            NotificationDestination.APP_MANAGER, NotificationDestination.NOTIFICATION_CLEANER), contents.map { it.destination }.toSet())
        assertTrue(contents.all { it.title.isNotBlank() && it.desc.isNotBlank() && it.buttonText.isNotBlank() })
        assertTrue(contents.none { '%' in it.title || '%' in it.desc })
        assertEquals("local_127_notifications", contents.last().id)
        val expected = mapOf(
            "local_006_large" to NotificationDestination.LARGE_FILES,
            "local_007_clean" to NotificationDestination.CLEAN,
            "local_013_unused" to NotificationDestination.UNUSED_FILES,
            "local_037_apps" to NotificationDestination.APP_MANAGER,
            "local_058_home" to NotificationDestination.HOME,
            "local_066_photos" to NotificationDestination.PHOTOS,
            "local_127_notifications" to NotificationDestination.NOTIFICATION_CLEANER,
        )
        for ((id, destination) in expected) assertEquals(destination, contents.single { it.id == id }.destination)
        assertTrue(contents.all { it.destination == it.iconDestination })
    }
    @Test fun contentCapacityIncludesTheWholeBatchButRemainsBounded() {
        val contents = parsePushContents((1..300).joinToString(prefix = "[", postfix = "]") {
            json(Content.TYPE_CLEAN).replace("\"test\"", "\"item_$it\"")
        })
        assertEquals(MAX_PUSH_CONTENTS, contents.size)
        assertEquals("item_256", contents.last().id)
    }

}
