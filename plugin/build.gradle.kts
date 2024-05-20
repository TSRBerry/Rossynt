import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.gson)
    implementation(libs.ktor.serialization.gson)
    implementation(project(":backend"))
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName = properties("pluginName")
    version = properties("platformVersion")
    type = properties("platformType")

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins = properties("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
    path = File(projectDir, "../CHANGELOG.md").absolutePath
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
koverReport {
    defaults {
        xml {
            onCheck = true
        }
    }
}

tasks {
    clean {
        dependsOn("cleanCopyBackend")
    }

    processResources {
        dependsOn("copyBackend")
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription = providers.fileContents(layout.projectDirectory.file("../README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with (it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                        (getOrNull(pluginVersion) ?: getUnreleased())
                                .withHeader(false)
                                .withEmptySections(false),
                        Changelog.OutputType.HTML,
                )
            }
        }
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    // Exclude Kotlin packages from the plugin in order to use the ones packed in IntelliJ platform 2022.1.
    // Note: to see list of packages included, go to folder: build/idea-sandbox/plugins/Rossynt/lib
    //
    // References:
    // https://youtrack.jetbrains.com/issue/IDEA-285839
    // https://youtrack.jetbrains.com/issue/KTIJ-20529
    //
    buildPlugin {
        exclude {
            it.name.startsWith("kotlinx-coroutines-") || it.name.startsWith("kotlin-stdlib-") || it.name.startsWith("kotlin-reflect-") || it.name.startsWith("slf4j-api-")
        }
    }
    prepareSandbox {
        exclude {
            it.name.startsWith("kotlinx-coroutines-") || it.name.startsWith("kotlin-stdlib-") || it.name.startsWith("kotlin-reflect-") || it.name.startsWith("slf4j-api-")
        }
    }

    signPlugin {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = properties("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }
}

val backendTargetDir = "${projectDir}/src/main/resources/raw/RossyntBackend"
val backendPublishDir = rootProject.project("backend").ext["publishDir"] as String
val backendVersions = rootProject.project("backend").ext["versions"] as Array<*>

interface Injected {
    @get:Inject val fs: FileSystemOperations
}

tasks.register("copyBackend") {
    dependsOn(":backend:build")

    inputs.dir(backendPublishDir)
    outputs.dir(backendTargetDir)

    val injected = project.objects.newInstance<Injected>()

    doLast {
        backendVersions.forEach { version ->
            injected.fs.copy {
                from("${backendPublishDir}/release_${version}")
                into("${backendTargetDir}/${version}")
                exclude("RossyntBackend")
                duplicatesStrategy = DuplicatesStrategy.FAIL
            }

            val targetFiles = file("${backendTargetDir}/${version}").listFiles()?.filter {
                it.name != "FileList.txt"
            }
            val fileList = file("${backendTargetDir}/${version}/FileList.txt")
            fileList.createNewFile()
            if (targetFiles != null) {
                fileList.writeText(targetFiles.joinToString("\n") {
                    "./${it.name}"
                })
            }
            fileList.appendText("\n")
        }

    }
}

tasks.register<Delete>("cleanCopyBackend") {
    delete = setOf(backendTargetDir)
}