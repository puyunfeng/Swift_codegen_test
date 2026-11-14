plugins {
    alias(libs.plugins.kotlinMultiplatform)
    `maven-publish`
}

group = "com.example.swift_codegen"
version = providers.gradleProperty("swiftCodegenVersion").getOrElse("1.0.0")

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
}

publishing {
    repositories {
        mavenLocal()
        // GitHub Packages registry shared by the codegen artifacts.
        // Credentials: gpr.user / gpr.key in ~/.gradle/gradle.properties,
        // or the GITHUB_ACTOR / GITHUB_TOKEN environment variables.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/puyunfeng/Swift_codegen_test")
            val user = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR"))
            val key = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN"))
            if (user.isPresent && key.isPresent) {
                credentials {
                    username = user.get()
                    password = key.get()
                }
            }
        }
    }
}
