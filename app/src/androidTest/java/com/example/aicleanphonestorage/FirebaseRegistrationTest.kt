package com.example.aicleanphonestorage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 独立的在线检查：只验证当前测试 App 注册，不给 ALL 主题或其他用户发消息，不输出 token。 */
@RunWith(AndroidJUnit4::class)
class FirebaseRegistrationTest {
    @Test fun configuredTestPackageCanRegisterWithFcm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.leafmotivation.quizguessoncolor", context.packageName)
        val app = FirebaseApp.getInstance()
        assertTrue(app.options.applicationId.isNotBlank())
        assertTrue(!app.options.gcmSenderId.isNullOrBlank())
        assertTrue(Tasks.await(FirebaseMessaging.getInstance().token, 30, TimeUnit.SECONDS).isNotBlank())
    }
}
