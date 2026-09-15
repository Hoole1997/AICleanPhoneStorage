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

    // 只给 googleRelease 绑定上传签名；签名文件由 Actions 从 main 复用或首次生成，密码从 Secret 注入。
    signingConfigs {
        create("googleRelease") {
            val signingPath = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
            if (!signingPath.isNullOrBlank()) {
                storeFile = rootProject.file(signingPath)
                storePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
                storeType = "PKCS12"
            }
        }
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
                // 设置页目标地址由渠道配置提供，不放进语言资源，避免多处维护。
                mapOf("privacyUrl" to "PRIVACY_URL", "feedbackEmail" to "FEEDBACK_EMAIL")
                    .forEach { (property, field) ->
                        val value = config.getProperty(property)
                        require(!value.isNullOrBlank()) { "app/src/$channel/config.properties 缺少 $property" }
                        buildConfigField("String", field, buildString(value))
                    }
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
                // AGP 9.3 自动合入 src/main/keepRules/*.keep 及 Android 默认规则。
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
    implementation("com.google.android.play:review:2.0.2")
    implementation(libs.lottie)
    implementation("com.github.toukaremax:core:1.0.15")
    implementation("com.github.toukaremax:bill:1.0.51") {
        // 保留 Unity Mediation 9.2.0，排除旧 IronSource 坐标，避免同包类冲突。
        exclude(group = "com.ironsource.sdk", module = "mediationsdk")
    }
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation(libs.androidx.splashscreen)
    implementation(libs.xxpermissions)
    implementation(libs.device.compat)
    implementation(project(":notification"))
    implementation(project(":metrics"))
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
    implementation(libs.androidx.lifecycle.process)
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
    // SDK 将 XPopup 作为运行时依赖；设备测试需要它的公开窗口 API 采集实际弹框帧耗时。
    androidTestImplementation("com.github.li-xiaojun:XPopup:2.10.0")
}

// 配置目录与 distribution flavor 同名，配置和 Firebase JSON 直接放在该目录。
// 在插件创建 variant 任务后覆盖输入，避免其默认 src/... 路径随后覆盖自定义值。
// 用户要求 Google Play 渠道仅在 GitHub Actions 构建，本机不创建其编译/打包/安装任务。
androidComponents.beforeVariants(androidComponents.selector().withFlavor("distribution" to "google")) {
    it.enable = providers.environmentVariable("GITHUB_ACTIONS").orNull == "true"
}

androidComponents.onVariants(androidComponents.selector().withFlavor("distribution" to "google").withBuildType("release")) {
    it.signingConfig?.setConfig(android.signingConfigs.getByName("googleRelease"))
}

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

// 仅输出元数据，不依赖任何渠道的编译任务；CI 与 CLI 用同一渠道版本生成产物名。
val googleVersionConfig = channelConfig("google")
val googleVersionName = googleVersionConfig.getProperty("versionName")
val googleVersionCode = googleVersionConfig.getProperty("versionCode")
require(googleVersionName.matches(Regex("[A-Za-z0-9._-]+"))) { "google versionName 不适合用作产物文件名" }
tasks.register("printGoogleReleaseVersionName") {
    group = "help"
    inputs.property("value", googleVersionName)
    doLast { println(inputs.properties.getValue("value")) }
}
tasks.register("printGoogleReleaseAabName") {
    group = "help"
    inputs.property("value", "aiclean_google_release_${googleVersionName}_${googleVersionCode}.aab")
    doLast { println(inputs.properties.getValue("value")) }
}
