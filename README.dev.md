> 本文档记录示例工程结构、测试命令与发布流程。项目是什么、怎么集成见 [README.md](./README.md)。

This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/sharedLogic](./sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/sharedUI](./sharedUI/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./sharedUI/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./sharedUI/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./sharedUI/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest`
- iOS tests: `./gradlew :sharedLogic:iosSimulatorArm64Test`

### 发布到 GitHub Packages

三个库产物（`swift_codegen`、`swift_codegen_annotations`、`swift_codegen_gradle_plugin`）
都发布到 `https://maven.pkg.github.com/puyunfeng/Swift_codegen_test`。仓库地址写在三个模块各自的
`publishing` 配置里。

版本号只有一处来源：[gradle.properties](./gradle.properties) 的 `swiftCodegenVersion`。
插件构建时会把该版本写进 `version.properties` 资源，所以插件注入的 processor/注解依赖版本
永远和插件自身一致，升级时只改这一个属性即可。

**发布（CI）**：打 tag 即发布，版本号取自 tag（`v1.2.0` → `1.2.0`）。
[.github/workflows/publish.yml](./.github/workflows/publish.yml) 使用仓库自带的
`GITHUB_TOKEN`（`packages: write`）发布，runner 为 `macos-latest`（iOS klib 需要 macOS）。
也可以在 Actions 页面手动 `Run workflow` 并填版本号。

```bash
git tag v1.0.0 && git push origin v1.0.0
```

**发布（本机）**：需要 `write:packages` 权限的 PAT，写在 `~/.gradle/gradle.properties`
（不要提交到仓库），或改用 `GITHUB_ACTOR` / `GITHUB_TOKEN` 环境变量：

```properties
gpr.user=puyunfeng
gpr.key=<PAT>
```

```bash
./gradlew --configure-on-demand \
  :swift-codegen:publishAllPublicationsToGitHubPackagesRepository \
  :swift_codegen_annotations:publishAllPublicationsToGitHubPackagesRepository \
  :swift_codegen_gradle_plugin:publishAllPublicationsToGitHubPackagesRepository
```

本机自用可直接 `./gradlew :swift_codegen_gradle_plugin:publishToMavenLocal`（示例工程
`sharedLogic` 就是从 mavenLocal 解析这个插件的）。

`--configure-on-demand` 是必需的：示例模块 `sharedLogic` 依赖 mavenLocal 里的插件，
在干净的机器上（CI）全量配置会解析失败。

GitHub Packages 不允许覆盖已发布的版本，重复发布会返回 409，需要换个版本号。

### 在其他项目中使用

`pluginManagement` 与 `dependencyResolutionManagement` 都需要加上 GitHub Packages
仓库（前者用于解析插件标记，后者用于解析插件自动注入的依赖）：

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/puyunfeng/Swift_codegen_test")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/puyunfeng/Swift_codegen_test")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
        mavenCentral()
    }
}
```

然后应用插件即可，注解 `@GenerateSwiftEnum` 和 KSP processor 会自动加到
`commonMainImplementation` 和 `kspIos*` 配置上：

```kotlin
plugins {
    id("com.example.swift_codegen") version "1.0.0"
}

swiftCodegen {
    swiftModuleName.set("SharedLogic")
    outputSubDir.set("iosApp/iosApp/Generated")
}
```

GitHub Packages 的包即使是公开的，读取也需要凭据（`read:packages` 的 PAT），
所以上面的 `credentials` 不能省略。

安装完成后再 `./gradlew generateSwiftEnums` 生成 Swift 枚举。

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…