import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // 顺序其实无关（codegen 插件用 withPlugin 惰性挂接），习惯上把 KSP 写在前面。
    alias(libs.plugins.ksp)
    alias(libs.plugins.swiftCodegen)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedLogic"
            isStatic = true
        }
    }
    android {
       namespace = "com.example.swift_codegen_test.sharedLogic"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
swiftCodegen {
    // 必须等于 iOS framework 的 baseName，因为生成文件里会 `import` 它
    swiftModuleName.set("SharedLogic")
    // 相对"仓库根目录"；落在 Xcode 16 的同步文件夹里，生成后自动参与编译
    outputSubDir.set("iosApp/iosApp/Generated")
}