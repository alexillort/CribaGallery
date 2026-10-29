import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val clientId = rootProject.extra["azureClientId"] as String
val appVersion = System.getenv("PACKAGE_VERSION") ?: "1.0.0"

// Genera Config.kt con el ID de Azure para la versión de escritorio.
val genDir = layout.buildDirectory.dir("generated/criba")
val generateConfig by tasks.registering {
    val id = clientId
    val out = genDir
    inputs.property("clientId", id)
    outputs.dir(out)
    doLast {
        val f = out.get().file("app/criba/galeria/desktop/Config.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package app.criba.galeria.desktop\n\nobject Config {\n    const val CLIENT_ID = \"$id\"\n}\n")
    }
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir("../shared/src")
    sourceSets.getByName("main").kotlin.srcDir(generateConfig)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.9.1")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    implementation("com.microsoft.azure:msal4j:1.17.2")
}

compose.desktop {
    application {
        mainClass = "app.criba.galeria.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Criba"
            packageVersion = appVersion
            description = "Galería rápida para tus fotos de OneDrive"
            vendor = "Criba"
            includeAllModules = true
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                upgradeUuid = "6c1f2f7e-6a43-4f0e-9a3c-0b8c2d6a91e4"
            }
        }
    }
}
