package com.example.aicleanphonestorage.core.diagnostics

/** Release 的空实现，不安装 StrictMode，不引入运行时检查开销。 */
object PerformanceDiagnostics {
    fun install() = Unit
}
