package com.example.viewmodel_kmp.codegen

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

private const val ANNOTATION = "com.example.swift_codegen.GenerateSwiftEnum"

private const val OPTION_SWIFT_MODULE_NAME = "swiftEnumModuleName"
private const val OPTION_OUTPUT_SUB_DIR = "swiftEnumOutputSubDir"
private const val OPTION_MODE = "swiftEnumMode"
private const val OPTION_SWIFT_ENUM_SUFFIX = "swiftEnumSuffix"
private const val OPTION_SWIFT_FORMAT_PATH = "swiftFormatPath"

class SwiftEnumProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SwiftEnumProcessor(environment)
}

private enum class GenerationMode {
    SEQUENTIAL,
    CONCURRENT,
}

private data class SwiftParameter(
    val name: String,
    val swiftType: String,
)

private sealed interface SwiftMember {
    val memberName: String
    val swiftTypeName: String

    data class ObjectMember(
        override val memberName: String,
        override val swiftTypeName: String,
    ) : SwiftMember

    data class ClassMember(
        override val memberName: String,
        override val swiftTypeName: String,
        val parameters: List<SwiftParameter>,
    ) : SwiftMember
}

private data class SwiftEnumModel(
    val swiftEnumName: String,
    val swiftModuleName: String,
    val outputSubDir: String,
    val sourceName: String,
    val interfaceName: String,
    val members: List<SwiftMember>,
)

private class SwiftEnumProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private val projectRoot = environment.options["projectRoot"] ?: System.getProperty("user.dir")

    private val globalSwiftModuleName = environment.options[OPTION_SWIFT_MODULE_NAME]
        ?.takeIf { it.isNotBlank() }
        ?: "App"

    private val globalOutputSubDir = environment.options[OPTION_OUTPUT_SUB_DIR]
        ?.takeIf { it.isNotBlank() }
        ?: "iosApp/Sources/Generated"

    private val globalSwiftEnumSuffix = environment.options[OPTION_SWIFT_ENUM_SUFFIX]
        ?.takeIf { it.isNotBlank() }
        ?: "Enum"

    private val swiftFormatExecutable: String? by lazy {
        val candidates = listOfNotNull(
            environment.options[OPTION_SWIFT_FORMAT_PATH]?.takeIf { it.isNotBlank() },
            "swiftformat",
            "/opt/homebrew/bin/swiftformat",
            "/usr/local/bin/swiftformat",
        ).distinct()
        val executable = candidates.firstOrNull { candidate ->
            runCatching {
                val process = ProcessBuilder(candidate, "--version")
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.readBytes()
                process.waitFor() == 0
            }.getOrDefault(false)
        }
        if (executable == null) {
            environment.logger.warn(
                "SwiftFormat was not found (PATH, /opt/homebrew/bin, /usr/local/bin); " +
                    "generated Swift files will not be formatted",
            )
        }
        executable
    }

    private val globalMode: GenerationMode by lazy {
        when (val value = environment.options[OPTION_MODE]?.trim()?.uppercase()) {
            null, "" -> GenerationMode.CONCURRENT
            "SEQUENTIAL" -> GenerationMode.SEQUENTIAL
            "CONCURRENT" -> GenerationMode.CONCURRENT
            else -> {
                environment.logger.warn(
                    "Unknown $OPTION_MODE '$value'; using CONCURRENT",
                )
                GenerationMode.CONCURRENT
            }
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val prepared = mutableListOf<Pair<GenerationMode, SwiftEnumModel>>()

        // KSP symbol analysis must run on the processor thread. Only the
        // rendering/writing phase is parallelized for CONCURRENT declarations.
        for (symbol in resolver.getSymbolsWithAnnotation(ANNOTATION)) {
            if (symbol !is KSClassDeclaration) {
                environment.logger.error(
                    "@GenerateSwiftEnum can only be applied to a sealed class/interface",
                    symbol,
                )
                continue
            }
            if (!symbol.validate(enableNewFeatures = true)) {
                deferred += symbol
                continue
            }
            val mode = generationMode(symbol)
            val model = prepareModel(symbol) ?: continue
            prepared += mode to model
        }

        val (sequential, concurrent) = prepared.partition { (mode, _) ->
            mode == GenerationMode.SEQUENTIAL
        }
        sequential.forEach { (_, model) -> writeSafely(model) }
        writeConcurrently(concurrent.map { (_, model) -> model })

        return deferred
    }

    private fun generationMode(declaration: KSClassDeclaration): GenerationMode {
        val annotation = declaration.annotations.firstOrNull { it.isGenerateSwiftEnum() }
            ?: return globalMode

        // KSP puts defaulted annotation arguments into both `arguments` and
        // `defaultArguments` as the same instance; an explicitly provided
        // argument is a distinct instance. This distinguishes an omitted mode
        // from an explicit `mode = CONCURRENT` without a GLOBAL sentinel.
        val argument = annotation.arguments.firstOrNull { it.name?.asString() == "mode" }
        val defaultArgument = annotation.defaultArguments.firstOrNull { it.name?.asString() == "mode" }
        val isExplicit = argument != null && (defaultArgument == null || argument !== defaultArgument)
        if (!isExplicit) return globalMode

        return when (annotation.enumEntryName("mode")?.uppercase()) {
            "SEQUENTIAL" -> GenerationMode.SEQUENTIAL
            "CONCURRENT" -> GenerationMode.CONCURRENT
            else -> {
                environment.logger.warn(
                    "Unknown mode for ${declaration.qualifiedName?.asString()}; using the global mode",
                    declaration,
                )
                globalMode
            }
        }
    }

    private fun prepareModel(declaration: KSClassDeclaration): SwiftEnumModel? {
        val annotation = declaration.annotations.firstOrNull { it.isGenerateSwiftEnum() } ?: return null
        val swiftModuleName = annotation.stringArgument("swiftModuleName")
            ?.takeIf { it.isNotBlank() }
            ?: globalSwiftModuleName
        val outputSubDir = annotation.stringArgument("outputSubDir")
            ?.takeIf { it.isNotBlank() }
            ?: globalOutputSubDir
        val swiftEnumName = annotation.stringArgument("swiftEnumName")
            ?.takeIf { it.isNotBlank() }
            ?: "${declaration.simpleName.asString()}$globalSwiftEnumSuffix"
        val interfaceName = declaration.simpleName.asString()
        val subtypes = declaration.getSealedSubclasses()
            .sortedBy { it.simpleName.asString() }
            .toList()

        if (subtypes.isEmpty()) {
            environment.logger.warn(
                "@GenerateSwiftEnum found no sealed subtypes for $interfaceName",
                declaration,
            )
            return null
        }

        val members = subtypes.mapNotNull { subtype -> subtype.toSwiftMember(declaration) }
        if (members.size != subtypes.size) {
            environment.logger.warn(
                "Skipped ${subtypes.size - members.size} subtype(s) of $interfaceName; the generated Swift file is incomplete",
                declaration,
            )
        }

        return SwiftEnumModel(
            swiftEnumName = swiftEnumName,
            swiftModuleName = swiftModuleName,
            outputSubDir = outputSubDir,
            sourceName = declaration.qualifiedName?.asString() ?: interfaceName,
            interfaceName = interfaceName,
            members = members,
        )
    }

    private fun KSClassDeclaration.toSwiftMember(root: KSClassDeclaration): SwiftMember? {
        val swiftTypeName = swiftTypeName(root)
        val memberName = simpleName.asString().memberName()

        return when (classKind) {
            ClassKind.OBJECT -> SwiftMember.ObjectMember(
                memberName = memberName,
                swiftTypeName = swiftTypeName,
            )
            ClassKind.CLASS -> {
                val constructor = primaryConstructor ?: run {
                    environment.logger.error(
                        "Sealed subtype ${qualifiedName?.asString()} needs a primary constructor",
                        this,
                    )
                    return null
                }
                val parameters = mutableListOf<SwiftParameter>()
                for (parameter in constructor.parameters) {
                    val parameterName = parameter.name?.asString() ?: continue
                    val swiftType = parameter.type.resolve().swiftType() ?: run {
                        environment.logger.error(
                            "Unsupported parameter type for $parameterName in ${qualifiedName?.asString()}",
                            this,
                        )
                        return null
                    }
                    parameters += SwiftParameter(name = parameterName, swiftType = swiftType)
                }
                SwiftMember.ClassMember(
                    memberName = memberName,
                    swiftTypeName = swiftTypeName,
                    parameters = parameters,
                )
            }
            else -> {
                environment.logger.error(
                    "Unsupported sealed subtype ${qualifiedName?.asString()}: $classKind",
                    this,
                )
                null
            }
        }
    }

    private fun KSClassDeclaration.swiftTypeName(root: KSClassDeclaration): String {
        val objcName = annotations
            .firstOrNull { it.shortName.asString() == "ObjCName" }
            ?.stringArgument("name")
        val baseName = objcName ?: simpleName.asString()
        return if (parentDeclaration == root) {
            root.simpleName.asString() + baseName
        } else {
            baseName
        }
    }

    private fun render(model: SwiftEnumModel): String = buildString {
        appendLine("// Generated by KSP from ${model.sourceName}. Do not edit.")
        appendLine("// Regenerate with: ./gradlew generateSwiftEnums")
        appendLine("import ${model.swiftModuleName}")
        appendLine()
        appendLine("public enum ${model.swiftEnumName} {")
        model.members.forEach { member ->
            when (member) {
                is SwiftMember.ObjectMember -> {
                    appendLine("    case ${member.memberName.swiftIdentifier()}")
                }
                is SwiftMember.ClassMember -> {
                    val associatedValues = member.parameters.joinToString(", ") {
                        "${it.name.swiftIdentifier()}: ${it.swiftType}"
                    }
                    appendLine("    case ${member.memberName.swiftIdentifier()}($associatedValues)")
                }
            }
        }
        appendLine()
        appendLine("    public func to${model.interfaceName}() -> ${model.interfaceName} {")
        appendLine("        switch self {")
        model.members.forEach { member ->
            when (member) {
                is SwiftMember.ObjectMember -> {
                    appendLine("        case .${member.memberName.swiftIdentifier()}:")
                    appendLine("            return ${member.swiftTypeName}.shared")
                }
                is SwiftMember.ClassMember -> {
                    val bindings = member.parameters.joinToString(", ") {
                        "let ${it.name.swiftIdentifier()}"
                    }
                    val arguments = member.parameters.joinToString(", ") {
                        "${it.name.swiftIdentifier()}: ${it.name.swiftIdentifier()}"
                    }
                    appendLine("        case .${member.memberName.swiftIdentifier()}($bindings):")
                    appendLine("            return ${member.swiftTypeName}($arguments)")
                }
            }
        }
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }

    private fun formatGeneratedSwift(content: String, target: File): String {
        val executable = swiftFormatExecutable ?: return content
        return try {
            val process = ProcessBuilder(
                executable,
                "--quiet",
                "--swiftversion", "6",
                "--stdinpath", target.absolutePath,
            ).start()
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            val formatted = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val error = process.errorStream.bufferedReader(Charsets.UTF_8).readText()
            val exitCode = process.waitFor()
            if (exitCode == 0 && formatted.isNotBlank()) {
                formatted
            } else {
                environment.logger.warn(
                    "SwiftFormat failed for ${target.name} (exit $exitCode): ${error.trim()}",
                )
                content
            }
        } catch (e: Exception) {
            environment.logger.warn("SwiftFormat could not format ${target.name}: ${e.message}")
            content
        }
    }

    private fun writeSafely(model: SwiftEnumModel) {
        try {
            write(model)
        } catch (e: Exception) {
            environment.logger.error(
                "Failed to generate Swift for ${model.sourceName}: ${e.message}",
            )
        }
    }

    private fun writeConcurrently(models: List<SwiftEnumModel>) {
        if (models.isEmpty()) return

        val threadCount = minOf(models.size, Runtime.getRuntime().availableProcessors())
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val tasks = models.map { model ->
                executor.submit { writeSafely(model) }
            }
            for (task in tasks) {
                try {
                    task.get()
                } catch (e: ExecutionException) {
                    environment.logger.error(
                        "Concurrent Swift generation failed: ${e.cause?.message}",
                    )
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    environment.logger.error("Concurrent Swift generation was interrupted")
                }
            }
        } finally {
            executor.shutdown()
        }
    }

    private fun write(model: SwiftEnumModel) {
        val outputDir = File(projectRoot, model.outputSubDir)
        val target = File(outputDir, "${model.swiftEnumName}.swift")
        val content = formatGeneratedSwift(render(model), target)
        val lockFile = File(System.getProperty("java.io.tmpdir"), "ksp-swift-${model.swiftEnumName}.lock")

        val changed = FileChannel.open(
            lockFile.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use {
                // Do not touch the file when the generated content is unchanged.
                // Xcode uses the file timestamp for incremental Swift compilation,
                // so a no-op rewrite would trigger an unnecessary recompile.
                if (target.exists() && target.readText() == content) {
                    false
                } else {
                    outputDir.mkdirs()
                    val temporary = File.createTempFile("${model.swiftEnumName}-", ".swift.tmp", outputDir)
                    try {
                        temporary.writeText(content)
                        try {
                            Files.move(
                                temporary.toPath(),
                                target.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE,
                            )
                        } catch (_: AtomicMoveNotSupportedException) {
                            Files.move(
                                temporary.toPath(),
                                target.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                        }
                    } finally {
                        temporary.delete()
                    }
                    true
                }
            }
        }

        if (changed) {
            environment.logger.info("Generated ${target.path}")
        } else {
            environment.logger.info("Swift enum is already up to date: ${target.path}")
        }
    }

}


private fun KSAnnotation.isGenerateSwiftEnum(): Boolean =
    annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION

private fun KSAnnotation.enumEntryName(name: String): String? =
    when (val value = arguments.firstOrNull { it.name?.asString() == name }?.value) {
        is KSClassDeclaration -> value.simpleName.asString()
        is KSType -> value.declaration.simpleName.asString()
        is String -> value
        else -> null
    }

private fun KSAnnotation.stringArgument(name: String): String? =
    arguments.firstOrNull { it.name?.asString() == name }?.value as? String

private fun KSType.swiftType(): String? {
    val declaration = declaration
    val qualifiedName = declaration.qualifiedName?.asString()
    val base = when (qualifiedName) {
        "kotlin.String" -> "String"
        "kotlin.Int" -> "Int32"
        "kotlin.Long" -> "Int64"
        "kotlin.Short" -> "Int16"
        "kotlin.Byte" -> "Int8"
        "kotlin.Boolean" -> "Bool"
        "kotlin.Double" -> "Double"
        "kotlin.Float" -> "Float"
        "kotlin.Char" -> "Character"
        "kotlin.collections.List", "kotlin.collections.MutableList",
        "kotlin.collections.Collection", "kotlin.collections.Iterable" -> {
            val element = typeArgument(0) ?: return null
            "[$element]"
        }
        "kotlin.collections.Set", "kotlin.collections.MutableSet" -> {
            val element = typeArgument(0) ?: return null
            "Set<$element>"
        }
        "kotlin.collections.Map", "kotlin.collections.MutableMap" -> {
            val key = typeArgument(0) ?: return null
            val value = typeArgument(1) ?: return null
            "[$key: $value]"
        }
        "kotlin.Array" -> {
            val element = typeArgument(0) ?: return null
            "[$element]"
        }
        else -> {
            if (declaration is KSClassDeclaration) {
                declaration.referencedSwiftTypeName()
            } else {
                return null
            }
        }
    }
    return if (isMarkedNullable) "$base?" else base
}

private fun KSType.typeArgument(index: Int): String? =
    arguments.getOrNull(index)?.type?.resolve()?.swiftType()

private fun KSClassDeclaration.referencedSwiftTypeName(): String {
    val objcName = annotations
        .firstOrNull { it.shortName.asString() == "ObjCName" }
        ?.stringArgument("name")
    val baseName = objcName ?: simpleName.asString()
    val parent = parentDeclaration
    return if (parent is KSClassDeclaration) {
        parent.referencedSwiftTypeName() + baseName
    } else {
        baseName
    }
}

private fun String.memberName(): String {
    val base = removeSuffix("Intent").ifEmpty { this }
    return base.replaceFirstChar { it.lowercase() }
}

private val SWIFT_KEYWORDS = setOf(
    "associatedtype", "class", "deinit", "enum", "extension", "fileprivate",
    "func", "import", "init", "inout", "internal", "let", "open", "operator",
    "private", "protocol", "public", "rethrows", "static", "struct", "subscript",
    "typealias", "var", "break", "case", "continue", "default", "defer", "do",
    "else", "fallthrough", "for", "guard", "if", "in", "repeat", "return",
    "switch", "where", "while", "as", "catch", "false", "is", "nil", "super",
    "self", "Self", "throw", "throws", "true", "try", "get", "set", "willSet",
    "didSet",
)

private fun String.swiftIdentifier(): String =
    if (this in SWIFT_KEYWORDS) "`$this`" else this
