import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    `maven-publish`
}

group = "com.example.swift_codegen"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.ksp.api)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // Published as com.example.swift_codegen:swift_codegen:1.0.0
            artifactId = "swift_codegen"
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
