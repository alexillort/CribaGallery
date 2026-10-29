package app.criba.galeria.droid

import android.app.Activity
import android.content.Context
import app.criba.galeria.BuildConfig
import app.criba.galeria.core.AuthProvider
import app.criba.galeria.core.GRAPH_SCOPES
import app.criba.galeria.core.NeedLoginException
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Inicio de sesión con MSAL para Android (cuentas personales y de trabajo/escuela). */
class AndroidAuth(private val ctx: Context) : AuthProvider {
    private var activityRef: WeakReference<Activity>? = null
    var activity: Activity?
        get() = activityRef?.get()
        set(value) { activityRef = value?.let { WeakReference(it) } }

    @Volatile private var pca: ISingleAccountPublicClientApplication? = null
    @Volatile private var cachedToken: String? = null
    @Volatile private var expiresAt = 0L
    private val createLock = Mutex()

    override val configured: Boolean = BuildConfig.AZURE_CLIENT_ID.isNotBlank()

    private suspend fun client(): ISingleAccountPublicClientApplication = createLock.withLock {
        pca?.let { return@withLock it }
        val cfg = File(ctx.filesDir, "msal_config.json")
        cfg.writeText(
            JSONObject()
                .put("client_id", BuildConfig.AZURE_CLIENT_ID)
                .put("authorization_user_agent", "DEFAULT")
                .put("redirect_uri", BuildConfig.MSAL_REDIRECT)
                .put("broker_redirect_uri_registered", true)
                .put("account_mode", "SINGLE")
                .put(
                    "authorities",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "AAD")
                            .put("audience", JSONObject().put("type", "AzureADandPersonalMicrosoftAccount").put("tenant_id", "common"))
                    )
                )
                .toString()
        )
        val created = suspendCancellableCoroutine<ISingleAccountPublicClientApplication> { cont ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                ctx,
                cfg,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        cont.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }
                },
            )
        }
        pca = created
        created
    }

    override suspend fun restore(): Boolean {
        val c = client()
        return withContext(Dispatchers.IO) {
            val acct = c.currentAccount.currentAccount ?: return@withContext false
            runCatching { token() }.isSuccess || acct.id.isNotEmpty()
        }
    }

    override suspend fun signIn(): Boolean {
        val act = activity ?: throw IllegalStateException("La ventana no está lista")
        val c = client()
        val hasAccount = withContext(Dispatchers.IO) { c.currentAccount.currentAccount != null }
        return suspendCancellableCoroutine { cont ->
            val cb = object : AuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) {
                    keep(result)
                    cont.resume(true)
                }

                override fun onError(exception: MsalException) {
                    cont.resumeWithException(exception)
                }

                override fun onCancel() {
                    cont.resume(false)
                }
            }
            if (hasAccount) {
                c.acquireToken(
                    AcquireTokenParameters.Builder()
                        .startAuthorizationFromActivity(act)
                        .withScopes(GRAPH_SCOPES)
                        .withCallback(cb)
                        .build()
                )
            } else {
                c.signIn(
                    SignInParameters.builder()
                        .withActivity(act)
                        .withScopes(GRAPH_SCOPES)
                        .withCallback(cb)
                        .build()
                )
            }
        }
    }

    override fun token(forceRefresh: Boolean): String {
        if (!forceRefresh) {
            val t = cachedToken
            if (t != null && System.currentTimeMillis() < expiresAt - 120_000) return t
        }
        val c = pca ?: runBlocking { client() }
        val acct = c.currentAccount.currentAccount ?: throw NeedLoginException()
        try {
            val r = c.acquireTokenSilent(
                AcquireTokenSilentParameters.Builder()
                    .forAccount(acct)
                    .fromAuthority(acct.authority)
                    .withScopes(GRAPH_SCOPES)
                    .build()
            )
            keep(r)
            return r.accessToken
        } catch (e: MsalUiRequiredException) {
            throw NeedLoginException()
        }
    }

    private fun keep(r: IAuthenticationResult) {
        cachedToken = r.accessToken
        expiresAt = r.expiresOn.time
    }

    override suspend fun signOut() {
        val c = client()
        withContext(Dispatchers.IO) { runCatching { c.signOut() } }
        cachedToken = null
        expiresAt = 0
    }
}
