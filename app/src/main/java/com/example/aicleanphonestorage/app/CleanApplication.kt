package com.example.aicleanphonestorage.app

import android.app.Application
import com.example.aicleanphonestorage.core.diagnostics.PerformanceDiagnostics

class CleanApplication : Application() {
    // 容器和数据源按需初始化，冷启动不读取磁盘、不扫描、不创建常驻协程。
    val container: AppContainer by lazy { AppContainer() }

    override fun onCreate() {
        super.onCreate()
        PerformanceDiagnostics.install()
    }
}
