package app.criba.galeria.desktop

import app.criba.galeria.core.AuthProvider
import app.criba.galeria.core.GRAPH_SCOPES
import app.criba.galeria.core.NeedLoginException
import com.microsoft.aad.msal4j.IAccount
import com.microsoft.aad.msal4j.IAuthenticationResult
import com.microsoft.aad.msal4j.ITokenCacheAccessAspect
import com.microsoft.aad.msal4j.ITokenCacheAccessContext
import com.microsoft.aad.msal4j.InteractiveRequestParameters
import com.microsoft.aad.msal4j.PublicClientApplication
import com.microsoft.aad.msal4j.SilentParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/** Inicio de sesión en Windows: abre el navegador con la página de Microsoft (MSAL4J). */
class DesktopAuth(private val clientId: String, dir: File) : AuthProvider {
    private val scopes = GRAPH_SCOPES.toSet()
    private val cacheFile = File(dir, "msal_cache.json")

    private val pca: PublicClientApplication by lazy {
        PublicClientApplication.builder(clientId)
            .authority("https://login.microsoftonline.com/common/")
            .setTokenCacheAccessAspect(object : ITokenCacheAccessAspect {
                override fun beforeCacheAccess(context: ITokenCacheAccessContext) {
                    if (cacheFile.exists()) context.tokenCache().deserialize(cacheFile.readText())
                }

                override fun afterCacheAccess(context: ITokenCacheAccessContext) {
                    if (context.hasCacheChanged()) {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(context.tokenCache().serialize())
                    }
                }
            })
            .build()
    }

    @Volatile private var account: IAccount? = null
    @Volatile private var cachedToken: String? = null
    @Volatile private var expiresAt = 0L

    override val configured: Boolean = clientId.isNotBlank()

    override suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        account = runCatching { pca.accounts.join().firstOrNull() }.getOrNull()
        account != null && runCatching { token() }.isSuccess
    }

    override suspend fun signIn(): Boolean = withContext(Dispatchers.IO) {
        val r = pca.acquireToken(
            InteractiveRequestParameters.builder(URI("http://localhost"))
                .scopes(scopes)
                .build()
        ).join()
        account = r.account()
        keep(r)
        true
    }

    override fun token(forceRefresh: Boolean): String {
        if (!forceRefresh) {
            val t = cachedToken
            if (t != null && System.currentTimeMillis() < expiresAt - 120_000) return t
        }
        val a = account ?: throw NeedLoginException()
        try {
            val r = pca.acquireTokenSilently(
                SilentParameters.builder(scopes, a).forceRefresh(forceRefresh).build()
            ).join()
            keep(r)
            return r.accessToken()
        } catch (e: Exception) {
            throw NeedLoginException()
        }
    }

    private fun keep(r: IAuthenticationResult) {
        cachedToken = r.accessToken()
        expiresAt = r.expiresOnDate().time
    }

    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            account?.let { runCatching { pca.removeAccount(it).join() } }
        }
        account = null
        cachedToken = null
        expiresAt = 0
    }
}
