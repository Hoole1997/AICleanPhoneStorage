package com.example.aicleanphonestorage.core.diagnostics

import android.os.StrictMode

/** 仅 Debug 编译进检查逻辑；日志用于尽早定位主线程 I/O 和未释放资源，不代表能检测全部 ANR/OOM。 */
object PerformanceDiagnostics {
    fun install() {
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
