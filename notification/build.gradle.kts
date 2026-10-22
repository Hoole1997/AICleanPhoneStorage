import java.util.Properties
import java.io.InputStreamReader

plugins { alias(libs.plugins.android.library) }

fun channelConfig(name: String) = Properties().apply {
    rootProject.file("app/src/$name/config.properties").inputStream().use {
        load(InputStreamReader(it, Charsets.UTF_8))
    }
}

// 配置值写入 Java 字符串常量前转义，避免渠道值中的引号、反斜杠破坏 BuildConfig。
fun buildString(value: String) = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    namespace = "io.docview.push"
    compileSdk { version = release(37) }
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 所有渠道/构建类型均导出规则，防止混淆宿主依赖 debug AAR 时丢失反射契约。
        consumerProguardFiles("consumer-rules.pro")
    }
    flavorDimensions += "distribution"
    productFlavors {
        listOf("local", "google").forEach { channel ->
            create(channel) {
                dimension = "distribution"
                val config = channelConfig(channel)
                // 独立 flavor 的 BuildConfig 避免同一 Gradle 调用中混用 token 上报地址和包名。
                buildConfigField("String", "FCM_URL", "\"${config.getProperty("fcmUrl")}\"")
                buildConfigField("String", "FCM_PKG", "\"${config.getProperty("fcmPkg")}\"")
                // 协议由 app 渠道配置拥有；notification 读取同名 flavor，无需反向依赖 app。
                mapOf(
                    "fcmPath" to "FCM_PATH",
                    "fcmSecretKey" to "FCM_SECRET_KEY",
                    "fcmParamToken" to "FCM_PARAM_TOKEN",
                    "fcmParamUid" to "FCM_PARAM_UID",
                    "fcmParamPack" to "FCM_PARAM_PACK",
                    "fcmHeaderSig" to "FCM_HEADER_SIG",
                ).forEach { (property, field) ->
                    val value = config.getProperty(property)
                    require(!value.isNullOrBlank()) { "app/src/$channel/config.properties 缺少 $property" }
                    buildConfigField("String", field, buildString(value))
                }
                buildConfigField("boolean", "REMOTE_PUSH_ENABLED", config.getProperty("remotePushEnabled"))
                buildConfigField("String", "USER_CHANNEL", "\"${config.getProperty("userChannel")}\"")
            }
        }
    }
    buildTypes {
        create("riskDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            // APK 联调包未注册 Firebase；防止 FCM 入口访问缺失的默认 FirebaseApp。
            // 仅此 buildType 关闭远程推送，本地常驻通知和原有渠道配置不变。
            buildConfigField("boolean", "REMOTE_PUSH_ENABLED", "false")
        }
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 与 app 使用相同构建边界，避免本机聚合任务间接编译 Google 渠道库。
androidComponents.beforeVariants(androidComponents.selector().withFlavor("distribution" to "google")) {
    it.enable = providers.environmentVariable("GITHUB_ACTIONS").orNull == "true"
}

// 仅 localRiskDebug 与联调 APK 匹配，不生成 Google 联调库。
androidComponents.beforeVariants(androidComponents.selector().withFlavor("distribution" to "google").withBuildType("riskDebug")) {
    it.enable = false
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
    // 与 app 已使用的 Gson 版本一致，让场景配置可进行严格 JSON 校验，避免编译/运行版本不一致。
    implementation("com.google.code.gson:gson:2.13.1")
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
}
