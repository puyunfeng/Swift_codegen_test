package com.example.swift_codegen

/**
 * Controls how the KSP processor handles multiple annotated declarations.
 */
enum class GenerateSwiftEnumMode {
    /** Generate the Swift files one after another. */
    SEQUENTIAL,

    /** Generate the Swift files concurrently (they must be independent). */
    CONCURRENT,
}

/**
 * Generates a native Swift enum for a sealed interface/class.
 *
 * The generated enum has one case per sealed subtype and a `to<InterfaceName>()`
 * method that maps each case to the corresponding Kotlin implementation.
 *
 * Global defaults are configured through KSP processor arguments:
 *
 * ```kotlin
 * ksp {
 *     arg("swiftEnumModuleName", "SharedLogic")
 *     arg("swiftEnumOutputSubDir", "iosApp/iosApp/Generated")
 *     arg("swiftEnumMode", "CONCURRENT")
 *     arg("swiftEnumSuffix", "Enum")
 * }
 * ```
 *
 * A non-empty annotation argument overrides the corresponding global value;
 * empty strings mean "use the global value".
 *
 * @property swiftModuleName Swift module that exposes the Kotlin types (used in
 * the generated `import` statement). Empty uses `swiftEnumModuleName`.
 * @property outputSubDir directory for the generated Swift file, relative to the
 * repository root. Empty uses `swiftEnumOutputSubDir`.
 * @property swiftEnumName full name of the generated Swift enum. When blank, the
 * name is `<sealed interface name><swiftEnumSuffix>`; if no suffix is configured
 * either, the suffix defaults to `Enum`.
 * @property mode explicit local generation mode. When explicitly set it wins;
 * otherwise `swiftEnumMode` is used; if that is missing too,
 * [GenerateSwiftEnumMode.CONCURRENT] is the default. Only use
 * [GenerateSwiftEnumMode.CONCURRENT] when the annotated declarations write
 * independent files (they must not share an output file).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateSwiftEnum(
    val swiftModuleName: String = "",
    val outputSubDir: String = "",
    val swiftEnumName: String = "",
    val mode: GenerateSwiftEnumMode = GenerateSwiftEnumMode.CONCURRENT,
)
