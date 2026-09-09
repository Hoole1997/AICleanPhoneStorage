package com.example.aicleanphonestorage.core.diagnostics

import android.os.StrictMode
import com.example.aicleanphonestorage.BuildConfig

/** 检查由构建生成的 DEBUG 常量控制，与 local/google 渠道无关；Release 不安装 StrictMode。 */
object PerformanceDiagnostics {
    fun install() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }
}
