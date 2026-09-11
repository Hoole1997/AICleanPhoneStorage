plugins {
    // AGP 9 内置 Kotlin，与 app/notification 一致，不再叠加 kotlin.android 插件。
    alias(libs.plugins.android.library)
}

// 直接读取 app 同名渠道的配置；不复制配置文件，不根据 taskNames 猜测构建渠道。
listOf("local", "google").forEach { channel ->
    apply(from = rootProject.file("app/src/$channel/config.gradle"))
}
fun analyticsConfig(channel: String): Map<*, *> {
    val config = extensions.extraProperties["${channel}AdConfig"] as Map<*, *>
    return requireNotNull(config["analytics"] as? Map<*, *>) { "Missing analytics section for $channel" }
}
fun buildString(value: Any?): String = "\"" + value?.toString().orEmpty()
    .replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "net.corekit.metrics"
    compileSdk { version = release(37) }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
    }
    flavorDimensions += "distribution"
    productFlavors {
        listOf("local", "google").forEach { channel ->
            create(channel) {
                dimension = "distribution"
                val analytics = analyticsConfig(channel)
                buildConfigField("String", "ADJUST_APP_TOKEN", buildString(analytics["adjustAppToken"]))
                buildConfigField("String", "THINKING_DATA_APP_ID", buildString(analytics["thinkingDataAppId"]))
                buildConfigField("String", "THINKING_DATA_SERVER_URL", buildString(analytics["thinkingDataServerUrl"]))
                buildConfigField("String", "DEFAULT_USER_CHANNEL", buildString(analytics["defaultUserChannel"]))
            }
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.toukaremax:core:1.0.15")
    // metrics 只使用 core 的上报协议，无需引入完整 bill 广告依赖。
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    api("com.adjust.sdk:adjust-android:5.4.3")
    api("com.android.installreferrer:installreferrer:2.2")
    api("com.google.android.gms:play-services-ads-identifier:18.0.1")
    api("cn.thinkingdata.android:ThinkingAnalyticsSDK:3.0.2")
}
