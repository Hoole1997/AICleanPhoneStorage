package com.example.aicleanphonestorage.feature.onboarding

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NewUserFlowConfigTest {
    @Test fun explicitBooleanFalseIsNotReplacedByDefaultAndMissingUsesTrue() = runTest {
        assertTrue(NewUserFlowConfig.enabled { true })
        assertFalse(NewUserFlowConfig.enabled { false })
        assertTrue(NewUserFlowConfig.enabled { null })
    }

    @Test fun unavailableRemoteReadKeepsFlowEnabled() = runTest {
        assertTrue(NewUserFlowConfig.enabled { throw IllegalStateException("Unavailable remote config") })
    }

    @Test fun cancellationDoesNotTurnIntoAnEnabledFlow() = runTest {
        val cancelled = CancellationException("Startup cancelled")
        try {
            NewUserFlowConfig.enabled { throw cancelled }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            // 协程调试恢复可能复制异常；验证取消语义，而非异常对象的引用身份。
            assertEquals(cancelled.message, actual.message)
        }
    }
}
