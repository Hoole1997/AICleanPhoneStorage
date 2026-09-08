package com.example.aicleanphonestorage.core.ui.motion

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

/** 仅在页面 resumed 期间监听系统动效偏好，停止时注销；不用定时轮询设置。 */
internal class MotionPreferences(context: Context, private val changed: (Boolean) -> Unit) {
    private val app = context.applicationContext
    private val power = app.getSystemService(PowerManager::class.java)
    private val accessibility = app.getSystemService(AccessibilityManager::class.java)
    private var observing = false
    private val scaleObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = publish()
        }
    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = publish()
        }
    private val touchListener =
        AccessibilityManager.TouchExplorationStateChangeListener { publish() }

    fun start() {
        if (!observing) {
            observing = true
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                scaleObserver,
            )
            ContextCompat.registerReceiver(
                app,
                powerReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            accessibility.addTouchExplorationStateChangeListener(touchListener)
        }
        publish()
    }

    fun stop() {
        if (!observing) return
        observing = false
        app.contentResolver.unregisterContentObserver(scaleObserver)
        app.unregisterReceiver(powerReceiver)
        accessibility.removeTouchExplorationStateChangeListener(touchListener)
        changed(false)
    }

    private fun publish() {
        if (!observing) return
        val enabled =
            if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
            else
                Settings.Global.getFloat(
                    app.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) > 0f
        changed(enabled && !power.isPowerSaveMode && !accessibility.isTouchExplorationEnabled)
    }
}
