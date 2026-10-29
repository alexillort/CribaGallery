import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Llave de firma fija: el inicio de sesión de Microsoft va amarrado a su huella.
val keystoreFile = rootProject.file("firma/criba.keystore")
val keystorePass = System.getenv("KEYSTORE_PASSWORD") ?: "criba-firma"
val msalHash: String = run {
    val ks = KeyStore.getInstance(keystoreFile, keystorePass.toCharArray())
    val cert = ks.getCertificate("criba")
    Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(cert.encoded))
}
val clientId = rootProject.extra["azureClientId"] as String
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
println("Criba · hash de firma para Azure (Android): $msalHash")

android {
    namespace = "app.criba.galeria"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.criba.galeria"
        minSdk = 26
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        manifestPlaceholders["msalHash"] = msalHash
        buildConfigField("String", "AZURE_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "MSAL_HASH", "\"$msalHash\"")
        buildConfigField(
            "String", "MSAL_REDIRECT",
            "\"msauth://app.criba.galeria/${URLEncoder.encode(msalHash, "UTF-8")}\""
        )
    }

    signingConfigs {
        create("criba") {
            storeFile = keystoreFile
            storePassword = keystorePass
            keyAlias = "criba"
            keyPassword = keystorePass
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("criba")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("criba")
        }
    }

    sourceSets.getByName("main").java.srcDir("../shared/src")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/io.netty.versions.properties",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.microsoft.identity.client:msal:5.4.0")
}
