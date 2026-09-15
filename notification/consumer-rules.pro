# notification 对宿主导出的 R8 契约；所有 flavor/buildType 共用。
# 只保留实际反射入口，不向宿主导出所有 Service/Provider/Receiver/Worker 的全成员 keep。
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# parsePushContents 使用匿名 TypeToken<List<Content?>>，full mode 下仅保留
# Signature 不够，还须保留承载泛型的类。限定子类在本模块，避免扩大宿主保留范围。
# 显式提供此契约，不能依靠 app 广告依赖将 Gson 2.10.1 间接升级为 2.13.2。
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class io.docview.push.** extends com.google.gson.reflect.TypeToken

# 这些 Kotlin DTO 没有无参构造，Gson 使用 Unsafe 创建实例；保留类的可实例化性
# 以及反射字段。SerializedName 固定 JSON 键，所以允许类名和字段名混淆。
# 不保留 copy/componentN/toString、计算属性或 Content.Companion 等非反射成员。
-keep,allowobfuscation class io.docview.push.config.Config {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation class io.docview.push.config.NotificationConfig {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation class io.docview.push.config.Content {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 地震扩展虽不属于当前清理入口，源码和协议仍保留；嵌套泛型元素也经 Gson 创建。
-keep,allowobfuscation class io.docview.push.earthquake.EarthquakeResponse {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation class io.docview.push.earthquake.Metadata {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation class io.docview.push.earthquake.EarthquakeFeature {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation class io.docview.push.earthquake.EarthquakeProperties {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation class io.docview.push.earthquake.EarthquakeGeometry {
    @com.google.gson.annotations.SerializedName <fields>;
}
# MsgBuilder 把 EarthquakeInfo 写入 JSON，字段同样不能被裁剪。
-keep,allowobfuscation class io.docview.push.earthquake.EarthquakeInfo {
    @com.google.gson.annotations.SerializedName <fields>;
}

# WorkManager 会把 Worker 类名持久化，随后通过 (Context, WorkerParameters) 创建。
# 只固定本模块的入口和该构造函数；保留旧名字也兼容已入库的任务。
-keep,allowoptimization class io.docview.push.worker.KeepAliveWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Provider、MessageService、CoreService、DeleteReceiver 的入口由 Manifest/AAPT 保留。
# 系统生命周期回调可由继承关系追踪；动态注册的匿名 Receiver 由显式构造追踪。
# PushPreferences 委托使用显式 key，不读取 KProperty.name，无需保留属性名。
# RemoteViews 仅调用系统 View API，资源均通过 R 引用；无自定义反射 setter。
