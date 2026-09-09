import java.util.Properties
import java.io.InputStreamReader

plugins { alias(libs.plugins.android.library) }

fun channelConfig(name: String) = Properties().apply {
    rootProject.file("app/src/$name/config.properties").inputStream().use {
        load(InputStreamReader(it, Charsets.UTF_8))
    }
}

android {
    namespace = "io.docview.push"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 24 }
    flavorDimensions += "distribution"
    productFlavors {
        listOf("local", "google").forEach { channel ->
            create(channel) {
                dimension = "distribution"
                val config = channelConfig(channel)
                // 独立 flavor 的 BuildConfig 避免同一 Gradle 调用中混用 token 上报地址和包名。
                buildConfigField("String", "FCM_URL", "\"${config.getProperty("fcmUrl")}\"")
                buildConfigField("String", "FCM_PKG", "\"${config.getProperty("fcmPkg")}\"")
                buildConfigField("boolean", "REMOTE_PUSH_ENABLED", config.getProperty("remotePushEnabled"))
                buildConfigField("String", "USER_CHANNEL", "\"${config.getProperty("userChannel")}\"")
            }
        }
    }
    buildTypes {
        release { consumerProguardFiles("proguard-rules.pro") }
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.utilcodex)
    implementation(libs.xxpermissions)
    implementation(libs.device.compat)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
