# ==================== Notification 模块混淆规则 ====================

# 保留 Gson 反序列化的数据类
-keep class io.docview.push.earthquake.EarthquakeResponse { *; }
-keep class io.docview.push.earthquake.Metadata { *; }
-keep class io.docview.push.earthquake.EarthquakeFeature { *; }
-keep class io.docview.push.earthquake.EarthquakeProperties { *; }
-keep class io.docview.push.earthquake.EarthquakeGeometry { *; }
-keep class io.docview.push.earthquake.EarthquakeInfo { *; }


-keep class io.docview.push.config.Config { *; }
-keep class io.docview.push.config.NotificationConfig { *; }
-keep class io.docview.push.config.Content { *; }
-keep class io.docview.push.config.Content$Companion { *; }

# 保留 ContentProvider
-keep public class * extends android.content.ContentProvider { *; }

# 保留 Service
-keep public class * extends android.app.Service { *; }

# 保留 BroadcastReceiver
-keep public class * extends android.content.BroadcastReceiver { *; }

# 保留 Worker
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Gson 规则
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
