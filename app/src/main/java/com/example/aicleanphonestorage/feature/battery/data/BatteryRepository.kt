package com.example.aicleanphonestorage.feature.battery.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

internal interface BatteryRepository {
    suspend fun read(): BatterySnapshot

    fun observe(): Flow<BatterySnapshot>
}

/** 只读 BatteryManager、系统电池广播和亮度设置；不申请权限、不写系统设置、不做后台轮询。 */
internal class AndroidBatteryRepository(context: Context, private val executor: TaskExecutor) :
    BatteryRepository {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val manager = app.getSystemService(BatteryManager::class.java)

    override suspend fun read(): BatterySnapshot = executor.io {
        val sticky = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val present = sticky?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) != false
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent =
            if (!present) null
            else if (scale > 0 && level in 0..scale) (level.toDouble() / scale * 100).roundToInt()
            else property(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        val brightness = setting(Settings.System.SCREEN_BRIGHTNESS)
        val counter =
            if (present) property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) else null
        BatterySnapshot(
            percent = percent,
            charging = if (present) readCharging(sticky) else null,
            powerConnected =
                if (present) sticky?.integer(BatteryManager.EXTRA_PLUGGED)?.let { it != 0 }
                else null,
            brightnessPercent =
                brightness?.takeIf { it in 0..255 }?.let { (it / 255.0 * 100).roundToInt() },
            // 自动亮度下不把存储的手动亮度设置伪装成当前面板的物理亮度。
            automaticBrightness =
                setting(Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            temperatureTenthsC =
                if (present)
                    sticky?.integer(BatteryManager.EXTRA_TEMPERATURE)?.takeIf { it in -1000..2000 }
                else null,
            voltageMv =
                if (present) sticky?.integer(BatteryManager.EXTRA_VOLTAGE)?.takeIf { it > 0 }
                else null,
            technology =
                if (present)
                    sticky
                        ?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
                        ?.trim()
                        ?.take(48)
                        ?.takeIf { it.isNotEmpty() }
                else null,
            remainingMah =
                counter?.takeIf { it > 0 || (it == 0 && percent == 0) }?.toLong()?.div(1000),
            // 普通应用没有跨设备可靠的公开额定满容量 API。不可用时保留 null，不用剩余电量反推或读隐藏 PowerProfile。
            fullMah = null,
            health =
                if (present) health(sticky?.integer(BatteryManager.EXTRA_HEALTH))
                else BatteryHealth.UNKNOWN,
        )
    }

    override fun observe(): Flow<BatterySnapshot> = callbackFlow {
        var receiverRegistered = false
        var observerRegistered = false
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_BATTERY_CHANGED,
                        BatteryManager.ACTION_CHARGING,
                        BatteryManager.ACTION_DISCHARGING -> trySend(Unit)
                    }
                }
            }
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
        try {
            // 系统电池广播是受保护广播；接收端不导出给其他应用。
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                    // 与 isCharging 配套的状态事件，仍只在页面前台订阅期间监听。
                    addAction(BatteryManager.ACTION_CHARGING)
                    addAction(BatteryManager.ACTION_DISCHARGING)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                observer,
            )
            observerRegistered = true
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                false,
                observer,
            )
            trySend(Unit)
        } catch (error: Exception) {
            close(error)
        }
        awaitClose {
            if (receiverRegistered) app.unregisterReceiver(receiver)
            if (observerRegistered) resolver.unregisterContentObserver(observer)
        }
    }
        .buffer(Channel.CONFLATED)
        .map { read() } // receiver/observer 只投递事件，读取和计算在受限 I/O 执行器。
        .distinctUntilChanged()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(BatterySnapshot())
        }

    private fun readCharging(sticky: Intent?): Boolean? {
        // 入口扫描和每次前台订阅都主动查询 BatteryService，不能只依赖上次广播快照，
        // 否则已在充电的设备可能要等下一次插拔才更新闪电。调用沿用 read() 的 I/O 上下文。
        val current =
            try {
                manager?.isCharging
            } catch (_: RuntimeException) {
                null
            }
        if (current != null) return current

        // 服务不可用时才回退广播；缺少 plugged 不能当成明确的拔电状态。
        return when (sticky?.integer(BatteryManager.EXTRA_STATUS)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> true
            BatteryManager.BATTERY_STATUS_FULL ->
                sticky.integer(BatteryManager.EXTRA_PLUGGED)?.let { it != 0 }
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
    }

    private fun property(id: Int): Int? =
        try {
            manager?.getIntProperty(id)?.takeIf { it != Int.MIN_VALUE }
        } catch (_: RuntimeException) {
            null
        }

    private fun setting(name: String): Int? =
        try {
            Settings.System.getInt(resolver, name)
        } catch (_: Settings.SettingNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }

    private fun Intent.integer(key: String): Int? =
        if (hasExtra(key)) getIntExtra(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null

    private fun health(value: Int?): BatteryHealth =
        when (value) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEATING
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVERVOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            BatteryManager.BATTERY_HEALTH_DEAD,
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.BAD
            else -> BatteryHealth.UNKNOWN
        }
}
