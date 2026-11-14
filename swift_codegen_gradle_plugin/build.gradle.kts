import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
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
    // KSP is applied by the consumer; only the extension API is needed at compile time.
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
}

// The plugin injects the processor and annotations artifacts at the version it was
// built with, so bake this build's version into a resource instead of hardcoding it.
val generateVersionResource = tasks.register("generateVersionResource") {
    val pluginVersion = project.version.toString()
    val outputDir = layout.buildDirectory.dir("generated/versionResource")
    inputs.property("version", pluginVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/example/swift_codegen/gradle/version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$pluginVersion\n")
    }
}

sourceSets.main {
    resources.srcDir(generateVersionResource)
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
