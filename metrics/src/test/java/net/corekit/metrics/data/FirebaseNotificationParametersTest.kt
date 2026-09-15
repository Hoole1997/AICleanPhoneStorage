package net.corekit.metrics.data

import org.junit.Assert.*
import org.junit.Test

class FirebaseNotificationParametersTest {
    @Test fun allThreeNotificationEventsKeepRequiredFieldsWithoutSplittingEmoji() {
        val longText = "😀".repeat(140)
        val input = mapOf<String, Any>("title" to "Title", "text" to longText, "from_background" to "true")
        for (event in listOf("Notific_Show", "Notific_Click", "Notific_Enter")) {
            val output = firebaseNotificationParameters(event, input)
            assertEquals("Title", output["title"])
            assertEquals("true", output["from_background"])
            assertEquals("😀".repeat(100), output["text"])
        }
        assertEquals(longText, input["text"])
    }

    @Test fun unrelatedEventsAndShortValuesRemainUnchanged() {
        val input = mapOf<String, Any>("text" to "x".repeat(130), "title" to "", "count" to 1)
        assertSame(input, firebaseNotificationParameters("other", input))
        assertEquals("", firebaseNotificationParameters("Notific_Show", input)["title"])
        assertEquals(1, firebaseNotificationParameters("Notific_Show", input)["count"])
    }
}
