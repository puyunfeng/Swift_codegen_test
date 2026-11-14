import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    `maven-publish`
}

group = "com.example.swift_codegen"
version = providers.gradleProperty("swiftCodegenVersion").getOrElse("1.0.0")

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
            // Published as com.example.swift_codegen:swift_codegen:<version>
            artifactId = "swift_codegen"
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
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
