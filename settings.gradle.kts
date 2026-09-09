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
            }
        }
    }
}

rootProject.name = "AIClean&PhoneStorage"
include(":app")

include(":notification")
