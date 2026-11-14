import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
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
    // KSP is applied by the consumer; only the extension API is needed at compile time.
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
}

gradlePlugin {
    plugins {
        create("swiftCodegen") {
            id = "com.example.swift_codegen"
            implementationClass = "com.example.swift_codegen.gradle.SwiftCodegenPlugin"
            displayName = "Swift codegen for @GenerateSwiftEnum"
            description = "Adds the KSP processor and configures Swift enum generation."
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
