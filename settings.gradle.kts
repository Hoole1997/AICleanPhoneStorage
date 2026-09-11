import java.util.Properties

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// 私有依赖凭据仅从被 Git 忽略的本机配置或 CI 环境读取。
val privateRepositoryProperties = Properties().apply {
    val propertiesFile = file("local.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 仅这两项依赖从官方示例使用的 JitPack 解析，避免影响其他库的来源。
        maven("https://jitpack.io") {
            content {
                includeModule("com.github.getActivity", "XXPermissions")
                includeModule("com.github.getActivity", "DeviceCompat")
                includeGroup("com.github.li-xiaojun")
            }
        }
        maven("https://artifact.bytedance.com/repository/pangle/")
        maven("https://repo.dgtverse.cn/repository/maven-public/")
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        maven("https://android-sdk.is.com/")
        maven("https://jfrog.anythinktech.com/artifactory/overseas_sdk")
        maven("https://artifacts.applovin.com/android")
        exclusiveContent {
            forRepository {
                maven {
                    name = "RemaxPrivatePackages"
                    url = uri("https://maven.pkg.github.com/toukaRemax/remax_sdk")
                    credentials {
                        username = privateRepositoryProperties.getProperty("github.user")
                            ?: System.getenv("GH_PACKAGES_USER") ?: System.getenv("GITHUB_ACTOR")
                        password = privateRepositoryProperties.getProperty("github.token")
                            ?: System.getenv("GH_PACKAGES_TOKEN") ?: System.getenv("GITHUB_TOKEN")
                    }
                }
            }
            filter { includeGroup("com.github.toukaremax") }
        }

    }
}

rootProject.name = "AIClean&PhoneStorage"
include(":app")

include(":notification")

include(":metrics")
