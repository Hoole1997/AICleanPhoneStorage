package com.remax.notification.utils

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/** 继承源项目 ALL 与 ALL_(UTC整数小时偏移+24) 协议；SDK 自己管理网络重试。 */
internal class FCMTopicManager(context: Context) {
    private val app = context.applicationContext
    private var requestedZone: String? = null

    @Synchronized fun subscribeCommonTopic() {
        val zone = DateUtil.getFirebaseTopicWithTimezone(Topic.ALL)
        if (requestedZone == zone) return
        requestedZone = zone
        val messaging = FirebaseMessaging.getInstance()
        messaging.subscribeToTopic(Topic.ALL).addOnFailureListener {
            synchronized(this) { requestedZone = null }
            Log.w("CleanPush", "Common topic subscription pending")
        }
        val prefs = app.getSharedPreferences("push_delivery", Context.MODE_PRIVATE)
        val previous = prefs.getString("timezone_topic", null)
        messaging.subscribeToTopic(zone).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (previous != null && previous != zone) messaging.unsubscribeFromTopic(previous)
                prefs.edit().putString("timezone_topic", zone).apply()
            } else {
                synchronized(this) { requestedZone = null }
                Log.w("CleanPush", "Timezone topic subscription pending")
            }
        }
    }
}
