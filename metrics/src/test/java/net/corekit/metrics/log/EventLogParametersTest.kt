package net.corekit.metrics.log

import org.junit.Assert.*
import org.junit.Test

class EventLogParametersTest {
    @Test fun diagnosticRedactionDoesNotChangeTheAuthorizedUploadPayload() {
        val payload = mapOf("action" to "uninstall", "reason" to "other", "has_other_content" to "test raw input\n原文")
        val logged = EventLogParameters.redact(payload)
        assertEquals("[redacted]", logged["has_other_content"])
        assertEquals("other", logged["reason"])
        assertEquals("test raw input\n原文", payload["has_other_content"])
    }
    @Test fun unrelatedEventsKeepTheirExistingLogParameters() {
        val payload = mapOf("action" to "retry", "reason" to "timeout")
        assertEquals(payload, EventLogParameters.redact(payload))
    }
}
