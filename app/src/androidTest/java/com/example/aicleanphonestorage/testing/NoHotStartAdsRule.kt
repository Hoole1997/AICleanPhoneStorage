package com.example.aicleanphonestorage.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import org.junit.rules.ExternalResource

/** 生命周期/数据测试只控制自己的页面；SDK 云端频限放行也不能插入真实热启动广告。 */
class NoHotStartAdsRule : ExternalResource() {
    private var guard: AutoCloseable? = null
    override fun before() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            guard = ForegroundTransitionGuard.hold("instrumentation")
        }
    }
    override fun after() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { guard?.close(); guard = null }
    }
}
