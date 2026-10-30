import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val appVersion = System.getenv("PACKAGE_VERSION") ?: "1.0.0"

kotlin {
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    // Lee fechas, cámara y orientación (EXIF) de fotos y videos, incluidos HEIC y MP4.
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
}

compose.desktop {
    application {
        mainClass = "app.criba.pc.MainKt"
        jvmArgs += listOf("-Xmx3g", "-Dsun.java2d.d3d=true")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Criba"
            packageVersion = appVersion
            description = "Organiza las fotos y documentos de tu PC"
            vendor = "Criba"
            includeAllModules = true
            windows {
                menu = true
                menuGroup = "Criba"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6c1f2f7e-6a43-4f0e-9a3c-0b8c2d6a91e4"
            }
        }
    }
}
