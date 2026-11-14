rootProject.name = "Swift_codegen_test"

pluginManagement {
    repositories {
        mavenLocal()
        // GitHub Packages：插件标记（com.example.swift_codegen.gradle.plugin）从这里解析。
        // 无凭据时 Gradle 拿到 401 会继续尝试后面的仓库，所以放在最前面也安全。
        maven {
            url = uri("https://maven.pkg.github.com/puyunfeng/Swift_codegen_test")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        // 同上：插件注入的注解/处理器依赖（com.example.swift_codegen:*）从这里解析。
        maven {
            url = uri("https://maven.pkg.github.com/puyunfeng/Swift_codegen_test")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
        // 这个仓库只认 androidx / com.android / com.google 三组，
        // 本来也不会命中 com.example.swift_codegen，所以放在 GitHub Packages 之后。
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":sharedLogic")
include(":sharedUI")
include(":swift-codegen")
include(":swift_codegen_annotations")
include(":swift_codegen_gradle_plugin")
