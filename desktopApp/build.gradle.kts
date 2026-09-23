import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

// jpackage lives in a full JDK; the JDK 21 that Gradle provisions may be a JBR without it.
fun systemJdk(): String {
    val process = ProcessBuilder("/usr/libexec/java_home").start()
    return process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
}

compose.desktop {
    application {
        mainClass = "repertoire.desktop.MainKt"
        javaHome = systemJdk()
        // ./gradlew :desktopApp:run -PdataDir=/some/folder keeps test runs away from the real progress.
        (findProperty("dataDir") as String?)?.let { jvmArgs += "-Drepertoire.dataDir=$it" }
        (findProperty("windowSize") as String?)?.let { jvmArgs += "-Dwindow.size=$it" }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "EroeffnungsPuzzle"
            packageVersion = property("app.version") as String
            macOS {
                bundleID = "de.privat.eroeffnungspuzzle"
                iconFile.set(project.file("icon.icns"))
            }
        }
    }
}

tasks.register<JavaExec>("renderScreens") {
    description = "Renders the main screens at phone and desktop size into build/screens."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "repertoire.desktop.RenderScreensKt"
    workingDir = projectDir
}
