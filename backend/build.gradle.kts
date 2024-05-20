import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    id("base")
}

// Configuration properties
val dotnetBin = if (DefaultNativePlatform.getCurrentOperatingSystem().isWindows) "dotnet.exe" else "dotnet"
val configuration = "Release"
val dotnetVersions = arrayOf("net6.0", "net7.0", "net8.0")
val publishProject = "RossyntBackend"
val testProjects = arrayOf("RossyntBackendUnitTest", "RossyntBackendIntegrationTest")

val solutionFiles = fileTree(projectDir) {
    include("*/**/*.cs")
    include("*/**/*.csproj")
    include("*.sln")
    exclude("**/bin")
    exclude("**/obj")
    exclude(layout.buildDirectory.asFile.get().name)
}

// Extra variables

ext["versions"] = dotnetVersions
ext["publishDir"] = "${layout.buildDirectory.asFile.get().path}/publish/${publishProject}"

// Tasks

dotnetVersions.forEach { version ->
    val friendlyVersion = version.replace(".", "")
        .replaceFirstChar {
            if (it.isLowerCase()) it.uppercase() else it.toString()
        }

    tasks.register<Exec>("publish${friendlyVersion}") {
        group = "dotnet"

        inputs.files(solutionFiles)
            .withPropertyName("sourceFiles")
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .ignoreEmptyDirectories()

        outputs.dirs(
            "${layout.buildDirectory.asFile.get().path}/bin/${publishProject}/${configuration.lowercase()}_${version}",
            "${layout.buildDirectory.asFile.get().path}/publish/${publishProject}/${configuration.lowercase()}_${version}"
        )

        executable = dotnetBin
        args(
            "publish",
            "--artifacts-path", layout.buildDirectory.asFile.get().path,
            "-c", configuration,
            "--framework", version,
            publishProject
        )
    }

    tasks.register<Exec>("test${friendlyVersion}") {
        group = "dotnet"

        inputs.files(solutionFiles)
            .withPropertyName("sourceFiles")
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .ignoreEmptyDirectories()

        outputs.dirs(testProjects.map {
            "${layout.buildDirectory.asFile.get().path}/bin/${it}/${configuration.lowercase()}_${version}"
        })

        executable = dotnetBin
        args(
            "test",
            "--artifacts-path", layout.buildDirectory.asFile.get().path,
            "-c", configuration,
            "--framework", version
        )
    }
}

tasks.register("publish") {
    group = "dotnet"

    dependsOn(dotnetVersions.map { version ->
        val friendlyVersion = version.replace(".", "")
            .replaceFirstChar {
                if (it.isLowerCase()) it.uppercase() else it.toString()
            }
        "publish${friendlyVersion}"
    })
}

tasks.register("test") {
    group = "dotnet"

    dependsOn(dotnetVersions.map { version ->
        val friendlyVersion = version.replace(".", "")
            .replaceFirstChar {
                if (it.isLowerCase()) it.uppercase() else it.toString()
            }
        "test${friendlyVersion}"
    })
}

// Register tasks as standard lifecycle tasks

tasks.assemble {
    dependsOn("publish")
}
tasks.check {
    dependsOn("test")
}