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
        assertEquals(setOf(NotificationDestination.CLEAN, NotificationDestination.NETWORK,
            NotificationDestination.PHOTOS, NotificationDestination.UNUSED_FILES), contents.map { it.destination }.toSet())
        assertTrue(contents.all { it.destination == it.iconDestination })
    }
}
