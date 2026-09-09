import com.google.gms.googleservices.GoogleServicesTask
import java.util.Properties
import java.io.InputStreamReader

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// 使用显式 flavor 配置，不按 taskNames 猜测渠道，支持同时构建 local/google。
fun channelConfig(name: String) = Properties().apply {
    file("src/$name/config.properties").inputStream().use { load(InputStreamReader(it, Charsets.UTF_8)) }
}

// 两个脚本分别导出独立 Map，避免来源项目按任务名选择配置造成多渠道串用。
apply(from = "src/local/config.gradle")
apply(from = "src/google/config.gradle")

fun adConfig(channel: String): Map<*, *> = extensions.extraProperties["${channel}AdConfig"] as Map<*, *>
fun Map<*, *>.section(name: String): Map<*, *> = this[name] as? Map<*, *> ?: emptyMap<String, String>()
fun Map<*, *>.text(name: String) = this[name]?.toString().orEmpty()
fun buildString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.aicleanphonestorage"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        listOf("local", "google").forEach { channel ->
            create(channel) {
                dimension = "distribution"
                val config = channelConfig(channel)
                applicationId = config.getProperty("applicationId")
                versionCode = config.getProperty("versionCode").toInt()
                versionName = config.getProperty("versionName")
                buildConfigField("boolean", "REMOTE_PUSH_ENABLED", config.getProperty("remotePushEnabled"))
                val ads = adConfig(channel)
                val platforms = listOf("admob", "gam", "pangle", "topon", "max")
                val slots = mapOf("splash" to "SPLASH", "banner" to "BANNER", "interstitial" to "INTERSTITIAL",
                    "native" to "NATIVE", "full_native" to "FULL_NATIVE", "rewarded" to "REWARDED")
                platforms.forEach { platform ->
                    val item = ads.section(platform)
                    val prefix = platform.uppercase()
                    buildConfigField("String", "${prefix}_APPLICATION_ID", buildString(item.text("applicationId")))
                    val units = item.section("adUnitIds")
                    slots.forEach { (key, suffix) ->
                        val value = units.text(key).ifEmpty { if (key == "full_native") units.text("fullNative") else "" }
                        buildConfigField("String", "${prefix}_${suffix}_ID", buildString(value))
                    }
                }
                buildConfigField("String", "TOPON_APP_KEY", buildString(ads.section("topon").text("appKey")))
                buildConfigField("String", "MAX_SDK_KEY", buildString(ads.section("max").text("sdkKey")))
                val analytics = ads.section("analytics")
                buildConfigField("String", "DEFAULT_USER_CHANNEL", buildString(analytics.text("defaultUserChannel")))
                buildConfigField("String", "ADJUST_APP_TOKEN", buildString(analytics.text("adjustAppToken")))
                buildConfigField("String", "THINKING_DATA_APP_ID", buildString(analytics.text("thinkingDataAppId")))
                buildConfigField("String", "THINKING_DATA_SERVER_URL", buildString(analytics.text("thinkingDataServerUrl")))
                manifestPlaceholders["ADMOB_APPLICATION_ID"] = ads.section("admob").text("applicationId")

            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    // 应用内切换可离线完成，避免语言资源只随安装时的系统语言拆分交付。
    bundle {
        language { enableSplit = false }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.toukaremax:core:1.0.15")
    implementation("com.github.toukaremax:bill:1.0.51") {
        // 保留 Unity Mediation 9.2.0，排除旧 IronSource 坐标，避免同包类冲突。
        exclude(group = "com.ironsource.sdk", module = "mediationsdk")
    }
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config")
    implementation("cn.thinkingdata.android:ThinkingAnalyticsSDK:3.0.2")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation(libs.androidx.splashscreen)
    implementation(libs.xxpermissions)
    implementation(libs.device.compat)
    implementation(project(":notification"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.firebase.bom))
    androidTestImplementation(libs.firebase.messaging)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
}

// 配置目录与 distribution flavor 同名，配置和 Firebase JSON 直接放在该目录。
// 在插件创建 variant 任务后覆盖输入，避免其默认 src/... 路径随后覆盖自定义值。
androidComponents.onVariants { variant ->
    val channel = variant.productFlavors.single { it.first == "distribution" }.second
    val jsonFile = layout.projectDirectory.file("src/$channel/google-services.json").asFile
    val taskName = "process${variant.name.replaceFirstChar { it.uppercaseChar() }}GoogleServices"
    tasks.named<GoogleServicesTask>(taskName).configure {
        googleServicesJsonFiles.set(listOf(jsonFile))
        // 发布渠道目前只有示例 JSON，未提供真实配置时保留可编译占位。
        enabled = channel == "local" || jsonFile.isFile
    }
}
