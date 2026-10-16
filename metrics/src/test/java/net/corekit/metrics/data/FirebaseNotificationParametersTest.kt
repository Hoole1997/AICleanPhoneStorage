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

    @Test fun allSixTriggerSourcesKeepTheirLegacyNotificationTypeAcrossFirebaseAdaptation() {
        // 来源是新增维度；文案截断不能覆盖来源，也不能把原有 1/3/4 类型改为六种新类型。
        val sourceTypes = mapOf(
            "unlock" to 1,
            "background" to 1,
            "keepalive" to 1,
            "alarmmanager" to 1,
            "fcm" to 3,
            "resident" to 4,
        )
        for (event in listOf("Notific_Show", "Notific_Click", "Notific_Enter")) {
            for ((source, type) in sourceTypes) {
                val input = mapOf<String, Any>(
                    "trigger_source" to source,
                    "Notific_Type" to type,
                    "from_background" to "false",
                    "title" to "Title",
                    "text" to "x".repeat(130),
                )

                val output = firebaseNotificationParameters(event, input)

                assertEquals("$event / $source", source, output["trigger_source"])
                assertEquals("$event / $source", type, output["Notific_Type"])
                assertEquals("false", output["from_background"])
                assertEquals("Title", output["title"])
                assertEquals("x".repeat(100), output["text"])
                assertEquals("x".repeat(130), input["text"])
            }
        }
    }
}
