plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
}

// ID de la app registrada en Azure (no es secreto). Se toma de -PazureClientId o de la variable AZURE_CLIENT_ID.
extra["azureClientId"] = (findProperty("azureClientId") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("AZURE_CLIENT_ID")?.takeIf { it.isNotBlank() }
    ?: ""
