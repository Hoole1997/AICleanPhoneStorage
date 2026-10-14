package io.docview.push.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** 只持有 Application；系统只保存一个显式、不可变的广播 PendingIntent。 */
internal class AndroidAlarmWakeup(context: Context, private val requestCode: Int = REQUEST_CODE) : AlarmWakeupPort {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AlarmManager::class.java)

    override fun exists(): Boolean = pendingIntent(PendingIntent.FLAG_NO_CREATE) != null

    override fun schedule(registration: AlarmWakeupRegistration) {
        val pending = requireNotNull(pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT, registration.token))
        // 运营通知允许系统合批/Doze 延迟；不申请精确闹钟，也不绕过省电限制。
        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, registration.dueElapsedMillis, pending)
    }

    override fun cancel() {
        pendingIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(flags: Int, token: String? = null): PendingIntent? =
        PendingIntent.getBroadcast(app, requestCode,
            Intent(app, AlarmWakeupReceiver::class.java).setAction(ACTION).apply {
                token?.let { putExtra(EXTRA_TOKEN, it) }
            }, flags or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION = "io.docview.push.ALARM_WAKEUP"
        const val EXTRA_TOKEN = "alarm_wakeup_token"
        private const val REQUEST_CODE = 4103
        fun bootCount(context: Context): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    }
}

/** 有界配置和注册元数据同步提交后才安排系统任务；调用方始终在 IO 线程。 */
@android.annotation.SuppressLint("UseKtx") // 必须检查 commit 返回值，KTX edit 不向调用者返回提交是否成功。
internal class AndroidAlarmWakeupStore(context: Context) : AlarmWakeupStore {
    private val preferences = context.applicationContext.getSharedPreferences("alarm_wakeup", Context.MODE_PRIVATE)

    var hasLaunched: Boolean
        get() = preferences.getBoolean("has_launched", false)
        set(value) { check(preferences.edit().putBoolean("has_launched", value).commit()) }

    var cachedConfig: String
        get() = preferences.getString("config", "").orEmpty()
        set(value) { check(preferences.edit().putString("config", value).commit()) }

    override var registration: AlarmWakeupRegistration?
        get() = preferences.getString("token", null)?.let {
            AlarmWakeupRegistration(it, preferences.getInt("boot", -1), preferences.getLong("due", -1L))
        }
        set(value) {
            val editor = preferences.edit().remove("token").remove("boot").remove("due")
            if (value != null) editor.putString("token", value.token).putInt("boot", value.bootCount)
                .putLong("due", value.dueElapsedMillis)
            check(editor.commit()) { "Unable to persist alarm registration" }
        }
}
