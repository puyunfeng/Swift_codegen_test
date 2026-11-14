package com.example.swift_codegen.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

private const val GROUP = "com.example.swift_codegen"
private const val PROCESSOR_ARTIFACT = "swift_codegen"
private const val ANNOTATIONS_ARTIFACT = "swift_codegen_annotations"
private const val VERSION = "1.0.0"

/**
 * Configures KSP so [com.example.swift_codegen.GenerateSwiftEnum] declarations
 * produce Swift enums.
 *
 * The consumer must apply `com.google.devtools.ksp`; this plugin:
 * - adds the processor to every `kspIos*` configuration;
 * - adds the annotations artifact to `commonMainImplementation`;
 * - forwards the extension values as KSP arguments;
 * - registers the `generateSwiftEnums` task.
 */
abstract class SwiftCodegenExtension {
    abstract val swiftModuleName: Property<String>
    abstract val outputSubDir: Property<String>
    abstract val swiftEnumMode: Property<String>
    abstract val swiftEnumSuffix: Property<String>
    abstract val swiftFormatPath: Property<String>
}

class SwiftCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("swiftCodegen", SwiftCodegenExtension::class.java).apply {
            swiftModuleName.convention("App")
            outputSubDir.convention("iosApp/Sources/Generated")
            swiftEnumMode.convention("CONCURRENT")
            swiftEnumSuffix.convention("Enum")
            swiftFormatPath.convention("")
        }

        // The annotation lives in the published KMP annotations artifact, so the
        // consumer can use @GenerateSwiftEnum from commonMain.
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.dependencies.add(
                "commonMainImplementation",
                "$GROUP:$ANNOTATIONS_ARTIFACT:$VERSION",
            )
        }

        project.pluginManager.withPlugin("com.google.devtools.ksp") {
            project.configurations.matching { it.name.startsWith("kspIos") }.configureEach { configuration ->
                project.dependencies.add(configuration.name, "$GROUP:$PROCESSOR_ARTIFACT:$VERSION")
            }

            val ksp = project.extensions.getByType(KspExtension::class.java)
            ksp.arg("projectRoot", project.rootProject.projectDir.absolutePath)
            ksp.arg("swiftEnumModuleName", extension.swiftModuleName)
            ksp.arg("swiftEnumOutputSubDir", extension.outputSubDir)
            ksp.arg("swiftEnumMode", extension.swiftEnumMode)
            ksp.arg("swiftEnumSuffix", extension.swiftEnumSuffix)
            ksp.arg("swiftFormatPath", extension.swiftFormatPath)

            project.tasks.register("generateSwiftEnums") { task ->
                task.group = "codegen"
                task.description = "Generates Swift enums for @GenerateSwiftEnum declarations."
            }

            // Pick exactly one available iOS KSP task, preferring the simulator.
            project.afterEvaluate {
                val selected = listOf(
                    "kspKotlinIosSimulatorArm64",
                    "kspKotlinIosArm64",
                    "kspKotlinIosX64",
                ).firstOrNull { project.tasks.findByName(it) != null }
                    ?: throw GradleException(
                        "generateSwiftEnums requires an iOS target. " +
                            "Declare iosSimulatorArm64(), iosArm64(), or iosX64() in kotlin { }.",
                    )

                project.logger.info("generateSwiftEnums will depend on $selected")
                project.tasks.named("generateSwiftEnums") {
                    it.dependsOn(selected)
                }
            }
        }

        project.afterEvaluate {
            if (!project.pluginManager.hasPlugin("com.google.devtools.ksp")) {
                project.logger.warn(
                    "The swift codegen plugin requires com.google.devtools.ksp; " +
                        "Swift enums will not be generated.",
                )
            }
        }
    }
}
