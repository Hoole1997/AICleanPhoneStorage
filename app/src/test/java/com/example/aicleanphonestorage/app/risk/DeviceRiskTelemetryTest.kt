package com.example.aicleanphonestorage.app.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRiskTelemetryTest {
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val telemetry = DeviceRiskTelemetry(
        eventSink = { name, parameters -> events += name to parameters },
        reportFailure = {},
    )

    @Test fun `dynamic XM event keeps a JSON tree and removes invalid values`() {
        val nested = linkedMapOf<String, Any?>("valid" to "value", "invalid" to Double.NaN)
        telemetry.reportXmEvent("XM_Get_Detection_Result", mapOf(
            "" to "empty key",
            " " to "blank key",
            "null" to null,
            "blank" to " ",
            "nan" to Double.NaN,
            "infinity" to Float.POSITIVE_INFINITY,
            "object" to nested,
            "array" to listOf(2, null, " ", Double.NEGATIVE_INFINITY, mapOf("ok" to true)),
            "primitive" to doubleArrayOf(1.5, Double.NaN, 2.5),
            "opaque" to object { override fun toString(): String = error("must not stringify SDK objects") },
        ))
        assertEquals("XM_Get_Detection_Result", events.single().first)
        assertEquals(mapOf(
            "object" to mapOf("valid" to "value"),
            "array" to listOf(2, mapOf("ok" to true)),
            "primitive" to listOf(1.5, 2.5),
        ), events.single().second)
        // 保留独立快照，SDK 修改原始容器时不会篡改已交给上报器的参数。
        nested["valid"] = "changed"
        assertEquals(mapOf("valid" to "value"), events.single().second["object"])
    }

    @Test fun `cyclic and excessive nested SDK containers cannot expand unboundedly`() {
        val cyclic = linkedMapOf<String, Any>()
        cyclic["self"] = cyclic
        cyclic["keep"] = true
        var deep: Any = "leaf"
        repeat(XmEventParameters.MAX_DEPTH + 10) { deep = mapOf("child" to deep) }
        telemetry.reportXmEvent("XM_Information", mapOf("cycle" to cyclic, "deep" to deep, "normal" to 3))
        assertEquals(mapOf("cycle" to mapOf("keep" to true), "normal" to 3), events.single().second)
    }

    @Test fun `text nodes and container entries are bounded independently`() {
        val largeString = "x".repeat(XmEventParameters.MAX_STRING_LENGTH * 3)
        val source = (0..100).associate { "key$it" to largeString }
        telemetry.reportXmEvent("XM_Information", source)
        val parameters = events.single().second
        assertTrue(parameters.size <= XmEventParameters.MAX_CONTAINER_ENTRIES)
        assertTrue(parameters.values.all { (it as String).length <= XmEventParameters.MAX_STRING_LENGTH })
        val totalText = parameters.entries.sumOf { it.key.length + (it.value as String).length }
        assertTrue(totalText <= XmEventParameters.MAX_TEXT_LENGTH)

        events.clear()
        val hugeArray = List(2_000) { index -> List(100) { index } }
        telemetry.reportXmEvent("XM_Array", mapOf("items" to hugeArray))
        fun nodes(value: Any): Int = when (value) {
            is Map<*, *> -> 1 + value.values.filterNotNull().sumOf(::nodes)
            is List<*> -> 1 + value.filterNotNull().sumOf(::nodes)
            else -> 1
        }
        // 顶层参数容器不消耗节点预算，所有 SDK 值（含嵌套容器）都消耗预算。
        assertTrue(nodes(events.single().second) <= XmEventParameters.MAX_NODES + 1)
    }

    @Test fun `blank and oversized event names are ignored while parameterless events remain valid`() {
        telemetry.reportXmEvent(" ", emptyMap<String, Any>())
        telemetry.reportXmEvent("X".repeat(XmEventParameters.MAX_NAME_LENGTH + 1), emptyMap<String, Any>())
        assertTrue(events.isEmpty())
        telemetry.reportXmEvent("XM_SDK_Init", mapOf("null" to null))
        assertEquals(listOf("XM_SDK_Init" to emptyMap<String, Any>()), events)
    }

    @Test fun `reporter and logger failures cannot suppress the next XM event`() {
        val attempted = mutableListOf<String>()
        val failing = DeviceRiskTelemetry(eventSink = { name, _ ->
            attempted += name
            if (name == "XM_Information") error("reporter failed")
        }, reportFailure = { error("logger failed") })
        failing.reportXmEvent("XM_Information", emptyMap<String, Any>())
        failing.reportXmEvent("XM_SDK_Init", emptyMap<String, Any>())
        assertEquals(listOf("XM_Information", "XM_SDK_Init"), attempted)
    }

    @Test fun `long parameter keys are dropped without changing their wire identity`() {
        telemetry.reportXmEvent("XM_Information", mapOf(
            "x".repeat(XmEventParameters.MAX_KEY_LENGTH + 1) to "reject",
            "valid" to "keep",
        ))
        assertEquals(mapOf("valid" to "keep"), events.single().second)
        assertFalse(events.single().second.containsValue("reject"))
    }
}
