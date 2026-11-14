plugins {
    alias(libs.plugins.kotlinMultiplatform)
    `maven-publish`
}

group = "com.example.swift_codegen"
version = "1.0.0"

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
}

publishing {
    repositories {
        mavenLocal()
    }
}
