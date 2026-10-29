package app.criba.galeria.core

/** Inicio de sesión con Microsoft. Android usa MSAL y Windows usa MSAL4J. */
interface AuthProvider {
    /** false si la app se compiló sin el ID de Azure. */
    val configured: Boolean

    /** Recupera la sesión guardada. true si hay una cuenta lista. */
    suspend fun restore(): Boolean

    /** Abre la pantalla de inicio de sesión de Microsoft. */
    suspend fun signIn(): Boolean

    /** Token de acceso para Microsoft Graph. Bloqueante: llamar fuera del hilo principal. */
    fun token(forceRefresh: Boolean = false): String

    suspend fun signOut()
}

class NeedLoginException(message: String = "Hace falta iniciar sesión de nuevo") : Exception(message)

val GRAPH_SCOPES = listOf("Files.ReadWrite")
