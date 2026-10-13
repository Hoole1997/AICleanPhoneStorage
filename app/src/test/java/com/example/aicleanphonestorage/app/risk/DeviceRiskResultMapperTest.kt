package com.example.aicleanphonestorage.app.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class DeviceRiskResultMapperTest {
    private val localSignals = DeviceRiskSignal.entries.toSet() - DeviceRiskSignal.PLAY

    @Test fun `nonempty device ID passes remote validation even when SDK remote flag is abnormal`() {
        val result = map(deviceId = "product-device", errorMessage = "", rawResponse = "", local = localSignals)
        assertEquals(localSignals, result.detectedSignals)
        assertFalse(result.detectedSignals.contains(DeviceRiskSignal.PLAY))
    }

    @Test fun `a valid empty ID marks PLAY alongside all eight completed local dimensions`() {
        val responses = listOf(
            """{"code":200,"data":{"deviceId":""}}""",
            """{"code":200,"data":{"deviceId":null}}""",
            """{"code":200,"data":{}}""",
            """{"code":"200","data":{"device_id":""}}""",
            """{"code":200.0,"data":{"device_id":null}}""",
            """{"code":"success","data":{"device_id":null}}""",
            """{"code":"success","data":{"device_id":"  ","deviceId":null}}""",
            """{"code":"success","data":{"deviceId":"","device_id":null,"device_risk":{"decision":"reject"}}}""",
        )
        responses.forEach { response ->
            val result = map(local = localSignals, rawResponse = response)
            assertEquals(DeviceRiskSignal.entries.toSet(), result.detectedSignals)
        }
    }

    @Test fun `successful rejection response still requires the SDK remote abnormal flag for PLAY`() {
        val response = """{"code":"success","data":{"device_id":null,"device_risk":{"decision":"reject"}}}"""
        val result = map(rawResponse = response, remoteAbnormal = false, local = localSignals)
        assertEquals(localSignals, result.detectedSignals)
        assertFalse(DeviceRiskSignal.PLAY in result.detectedSignals)
    }

    @Test fun `nonempty or malformed ID in either alias cannot be treated as completed empty ID`() {
        val invalidData = listOf(
            """{"device_id":"server-device"}""",
            """{"deviceId":null,"device_id":"server-device"}""",
            """{"deviceId":"server-device","device_id":null}""",
            """{"deviceId":"one-device","device_id":"another-device"}""",
            """{"device_id":42}""",
            """{"deviceId":null,"device_id":false}""",
            """{"deviceId":"","device_id":[]}""",
            """{"deviceId":{},"device_id":null}""",
        )
        invalidData.forEach { data ->
            assertUnavailable {
                map(rawResponse = """{"code":"success","data":$data}""", local = localSignals)
            }
        }
    }

    @Test fun `unknown remote errors fail open rather than applying already detected local flags`() {
        listOf("HTTP 500", "timeout", "Play Integrity failed", "JSON parse failure", "device_id missing").forEach { error ->
            assertUnavailable {
                map(local = localSignals, errorMessage = error,
                    rawResponse = """{"code":"success","data":{"device_id":null}}""")
            }
        }
        // 矛盾的成功 ID 不能覆盖 SDK 明确标识的检测错误。
        assertUnavailable { map(deviceId = "product-device", errorMessage = "HTTP 500", local = localSignals) }
    }

    @Test fun `empty ID requires the real successful code and data object protocol`() {
        val invalid = listOf(
            "",
            " ",
            "not JSON",
            """{"code":500,"data":{"deviceId":""}}""",
            """{"code":"error","data":{"device_id":null}}""",
            """{"code":"SUCCESS","data":{"device_id":null}}""",
            """{"code":" success ","data":{"device_id":null}}""",
            """{"code":"2e2","data":{"device_id":null}}""",
            """{"code":200.1,"data":{"device_id":null}}""",
            """{"code":200}""",
            """{"code":200,"data":null}""",
            """{"code":200,"data":[]}""",
            """{"code":200,"data":{"deviceId":42}}""",
            """{"code":200,"data":{"deviceId":"different-nonempty-id"}}""",
            """{"code":true,"data":{}}""",
            """{"code":null,"data":{}}""",
            """{"code":{},"data":{}}""",
            """[{"code":200,"data":{}}]""",
            """{"code":200,"data":{}} trailing-invalid""",
            """{code:200,data:{}}""",
            """{'code':200,'data':{}}""",
            """{"code":200,/* comment */"data":{}}""",
        )
        invalid.forEach { response ->
            assertUnavailable { map(errorMessage = "", rawResponse = response, local = localSignals) }
        }
    }

    @Test fun `oversized and overly nested remote responses fail before recursive parsing`() {
        val longResponse = """{"code":200,"data":{},"padding":"${"x".repeat(70_000)}"}"""
        assertUnavailable { map(rawResponse = longResponse, local = localSignals) }
        val nested = "[".repeat(20) + "0" + "]".repeat(20)
        val deepResponse = """{"code":200,"data":{},"padding":$nested}"""
        assertUnavailable { map(rawResponse = deepResponse, local = localSignals) }
    }

    @Test fun `structural characters inside escaped quoted strings do not create false depth errors`() {
        val response = """{"code":200,"data":{},"note":"escaped \"quote\" [[[[{{{{"}"""
        assertEquals(setOf(DeviceRiskSignal.PLAY), map(rawResponse = response).detectedSignals)
    }

    @Test fun `each completed local dimension maps independently without inventing remote risk`() {
        localSignals.forEach { signal ->
            val result = map(deviceId = "product-device", errorMessage = "", local = setOf(signal))
            assertEquals(setOf(signal), result.detectedSignals)
        }
        assertEquals(emptySet<DeviceRiskSignal>(),
            map(deviceId = "product-device", errorMessage = "", remoteAbnormal = false).detectedSignals)
    }

    @Test fun `Play Integrity failure cannot become risk through a successful fallback upload`() {
        assertUnavailable { map(local = localSignals, integrityUnavailable = true) }
        assertUnavailable { map(deviceId = "product-device", errorMessage = "", integrityUnavailable = true) }
        assertUnavailable {
            map(local = localSignals, integrityUnavailable = true,
                rawResponse = """{"code":"success","data":{"device_id":null,"device_risk":{"decision":"reject"}}}""")
        }
    }

    private fun assertUnavailable(block: () -> DeviceRiskSnapshot) {
        try {
            block()
            fail("incomplete SDK result must not apply any local or remote restriction")
        } catch (error: DeviceRiskUnavailableException) {
            assertEquals(DeviceRiskUnavailableReason.SDK_ERROR, error.reason)
        }
    }

    private fun map(
        local: Set<DeviceRiskSignal> = emptySet(),
        remoteAbnormal: Boolean = true,
        deviceId: String = "",
        errorMessage: String = "deviceId 缺失",
        rawResponse: String = """{"code":200,"data":{"deviceId":""}}""",
        integrityUnavailable: Boolean = false,
    ): DeviceRiskSnapshot = DeviceRiskResultMapper.map(
        isVpn = DeviceRiskSignal.VPN in local,
        isDnsAbnormal = DeviceRiskSignal.DNS in local,
        isIpAbnormal = DeviceRiskSignal.IP in local,
        isSimulator = DeviceRiskSignal.SIMULATOR in local,
        isAdb = DeviceRiskSignal.ADB in local,
        isNoSim = DeviceRiskSignal.NO_SIM in local,
        isNoGp = DeviceRiskSignal.NO_GP in local,
        isSys9Low = DeviceRiskSignal.SYS_9_LOW in local,
        isRemoteAbnormal = remoteAbnormal,
        deviceId = deviceId,
        errorMessage = errorMessage,
        rawResponse = rawResponse,
        integrityUnavailable = integrityUnavailable,
    )
}
