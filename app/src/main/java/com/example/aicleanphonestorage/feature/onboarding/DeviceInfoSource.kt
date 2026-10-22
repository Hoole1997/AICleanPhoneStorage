package com.example.aicleanphonestorage.feature.onboarding

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor

internal enum class DeviceInfoField { DEVICE, SYSTEM, RESOLUTION, DENSITY, STORAGE, ENGINE }
internal sealed interface DeviceInfoValue {
    data class Text(val value: String) : DeviceInfoValue
    data class Storage(val used: Long, val total: Long) : DeviceInfoValue
    data object Done : DeviceInfoValue
    data object Unknown : DeviceInfoValue
}

internal fun interface DeviceInfoSource { suspend fun read(field: DeviceInfoField): DeviceInfoValue }

/** 只持有 Application；屏幕以真实显示像素/dpi 读取，不把 Activity 的可用窗口大小当屏幕分辨率。 */
internal class AndroidDeviceInfoSource(
    context: Context,
    private val executor: TaskExecutor,
    private val initializeEngine: suspend () -> Unit,
) : DeviceInfoSource {
    private val app = context.applicationContext

    override suspend fun read(field: DeviceInfoField): DeviceInfoValue {
        // 引擎本身已在共享 TaskExecutor 上运行，不能嵌套获取同一个并发许可。
        if (field == DeviceInfoField.ENGINE) {
            initializeEngine()
            return DeviceInfoValue.Done
        }
        return executor.io {
            when (field) {
                DeviceInfoField.DEVICE -> text(listOf(Build.BRAND, Build.MODEL).filter { it.isNotBlank() }.joinToString(" "))
                DeviceInfoField.SYSTEM -> text(Build.VERSION.RELEASE.takeIf { it.isNotBlank() }?.let { "Android $it" }.orEmpty())
                DeviceInfoField.RESOLUTION -> displayMetrics().let { text("${it.widthPixels} × ${it.heightPixels}") }
                DeviceInfoField.DENSITY -> displayMetrics().let { text("${it.densityDpi} dpi") }
                DeviceInfoField.STORAGE -> {
                    val stat = StatFs(Environment.getDataDirectory().absolutePath)
                    check(stat.totalBytes > 0)
                    DeviceInfoValue.Storage((stat.totalBytes - stat.availableBytes).coerceIn(0, stat.totalBytes), stat.totalBytes)
                }
                DeviceInfoField.ENGINE -> error("Engine handled separately")
            }
        }
    }

    @Suppress("DEPRECATION") // realMetrics 读取整个默认显示屏，避免系统栏/应用兼容窗口缩小报告值。
    private fun displayMetrics(): DisplayMetrics {
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            ?: error("Display unavailable")
        return DisplayMetrics().also {
            display.getRealMetrics(it)
            check(it.widthPixels > 0 && it.heightPixels > 0 && it.densityDpi > 0)
        }
    }

    private fun text(value: String) = if (value.isBlank()) DeviceInfoValue.Unknown else DeviceInfoValue.Text(value)
}
