package net.helcel.cowspent.android.account

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.R
import net.helcel.cowspent.android.main.MainConstants
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.CospendClientUtil
import net.helcel.cowspent.util.CospendClientUtil.LoginStatus
import net.helcel.cowspent.util.SecureStorage
import java.net.URLDecoder
import java.util.Locale


class AccountActivity : AppCompatActivity() {

    private val viewModel: AccountViewModel by viewModels()

    companion object {
        private val TAG = AccountActivity::class.java.simpleName

        const val SETTINGS_USE_SSO = "settingsUseSSO"
        const val SETTINGS_SSO_URL = "settingsSSOUrl"
        const val SETTINGS_SSO_USERNAME = "settingsSSOUsername"
        const val SETTINGS_URL = "settingsUrl"
        const val SETTINGS_USERNAME = "settingsUsername"
        const val SETTINGS_PASSWORD = "settingsPassword"
        const val SETTINGS_KEY_ETAG = "sessions_last_etag"
        const val SETTINGS_KEY_LAST_MODIFIED = "sessions_last_modified"
        const val DEFAULT_SETTINGS = ""
        const val CREDENTIALS_CHANGED = 3

        const val LOGIN_URL_DATA_KEY_VALUE_SEPARATOR = ":"
        const val WEBDAV_PATH_4_0_AND_LATER = "/remote.php/webdav"
    }

    private lateinit var preferences: SharedPreferences
    private var oldPassword = ""
    private var useWebLogin = true
    private var showLoginDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.showWebView) {
                    viewModel.showWebView = false
                } else {
                    finish()
                }
            }
        })

        setContent {
            ThemeUtils.CowspentTheme {
                if (viewModel.showWebView) {
                    val serverUrl = CospendClientUtil.formatURL(viewModel.serverUrl)
                    WebLoginScreen(
                        url = normalizeUrlSuffix(serverUrl) + "index.php/login/flow",
                        onLoginUrlDetected = { parseAndLoginFromWebView(it) }
                    )
                } else {
                    AccountScreen(
                        viewModel = viewModel,
                        onBack = { finish() },
                        onConnect = { login() },
                        onSsoClick = { isChecked ->
                            if (isChecked) {
                                showLoginDialog = true
                            } else {
                                viewModel.useSso = false
                                preferences.edit { putBoolean(SETTINGS_USE_SSO, false) }
                            }
                        },
                        onLogout = { viewModel.logout() }
                    )
                }

                LoginDialog(
                    showDialog = showLoginDialog,
                    onDismissRequest = { showLoginDialog = false },
                    onInitiateSsoLogin = {
                        showLoginDialog = false
                        try {
                            AccountImporter.pickNewAccount(this@AccountActivity)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to initiate SSO login", e)
                            Toast.makeText(this@AccountActivity, "SSO login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
        viewModel.validateUrl()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun WebLoginScreen(url: String, onLoginUrlDetected: (String) -> Unit) {
        var isLoading by remember { mutableStateOf(true) }
        
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            allowFileAccess = false
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = getWebLoginUserAgent(context)
                        }
                        webViewClient = object : WebViewClient() {
                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                if (url?.startsWith("nc://login/") == true) {
                                    onLoginUrlDetected(url)
                                    return true
                                }
                                return false
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString()
                                if (url?.startsWith("nc://login/") == true) {
                                    onLoginUrlDetected(url)
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                        }
                        
                        val headers = HashMap<String, String>()
                        headers["OCS-APIREQUEST"] = "true"
                        loadUrl(url, headers)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, this
            ) { ssoAccount ->
                lifecycleScope.launch {
                    SingleAccountHelper.commitCurrentAccount(applicationContext, ssoAccount.name)

                    preferences.edit {
                        putBoolean(SETTINGS_USE_SSO, true)
                        putString(SETTINGS_SSO_URL, ssoAccount.url)
                        putString(SETTINGS_SSO_USERNAME, ssoAccount.userId)
                    }

                    viewModel.useSso = true
                    viewModel.serverUrl = ssoAccount.url
                    viewModel.username = ssoAccount.userId

                    val resultData = Intent()
                    resultData.putExtra(MainConstants.CREDENTIALS_CHANGED, CREDENTIALS_CHANGED)
                    setResult(RESULT_OK, resultData)
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSO account selection failed", e)
        }
    }

    private fun legacyLogin() {
        val url = CospendClientUtil.formatURL(viewModel.serverUrl.trim())
        val username = viewModel.username
        var password = viewModel.password

        if (password.isEmpty()) {
            password = oldPassword
        }

        performLogin(url, username, password)
    }

    private fun login() {
        if (useWebLogin) {
            viewModel.showWebView = true
        } else {
            legacyLogin()
        }
    }

    private fun getWebLoginUserAgent(context: Context): String {
        val defaultUA = try {
            android.webkit.WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            Build.MANUFACTURER + " " + Build.MODEL
        }
        return "$defaultUA Cowspent/Android"
    }

    private fun parseAndLoginFromWebView(dataString: String) {
        try {
            val loginUrlInfo = parseLoginDataUrl(dataString)
            val url = normalizeUrlSuffix(loginUrlInfo.serverAddress)
            performLogin(url, loginUrlInfo.username, loginUrlInfo.password)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid login URL", e)
        }
    }

    private fun parseLoginDataUrl(dataString: String): LoginUrlInfo {
        val prefix = "nc://login/"
        if (dataString.length < prefix.length) {
            throw IllegalArgumentException("Invalid login URL detected")
        }
        val loginUrlInfo = LoginUrlInfo()
        val data = dataString.substring(prefix.length)
        val values = data.split("&")

        if (values.size !in 1..3) {
            throw IllegalArgumentException("Illegal number of login URL elements detected: ${values.size}")
        }

        for (value in values) {
            when {
                value.startsWith("user$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR") -> {
                    loginUrlInfo.username = URLDecoder.decode(value.substring(("user$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR").length), "UTF-8")
                }
                value.startsWith("password$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR") -> {
                    loginUrlInfo.password = URLDecoder.decode(value.substring(("password$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR").length), "UTF-8")
                }
                value.startsWith("server$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR") -> {
                    loginUrlInfo.serverAddress = URLDecoder.decode(value.substring(("server$LOGIN_URL_DATA_KEY_VALUE_SEPARATOR").length), "UTF-8")
                }
                else -> throw IllegalArgumentException("Illegal magic login URL element detected: $value")
            }
        }
        return loginUrlInfo
    }

    private fun normalizeUrlSuffix(url: String): String {
        return when {
            url.lowercase(Locale.ROOT).endsWith(WEBDAV_PATH_4_0_AND_LATER) -> {
                url.substring(0, url.length - WEBDAV_PATH_4_0_AND_LATER.length)
            }
            !url.endsWith("/") -> "$url/"
            else -> url
        }
    }


    private fun performLogin(url: String, username: String, password: String) {
        viewModel.isSubmitting = true
        viewModel.showWebView = false

        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                CospendClientUtil.isValidLogin(url, username, password)
            }

            if (status == LoginStatus.OK) {
                SecureStorage.savePassword(applicationContext, SETTINGS_PASSWORD, password)
                preferences.edit {
                    putString(SETTINGS_URL, url)
                    putString(SETTINGS_USERNAME, username)
                    remove(SETTINGS_KEY_ETAG)
                    remove(SETTINGS_KEY_LAST_MODIFIED)
                }

                val data = Intent()
                data.putExtra(MainConstants.CREDENTIALS_CHANGED, CREDENTIALS_CHANGED)
                setResult(RESULT_OK, data)
                finish()
            } else {
                Log.e("Cowspent", "invalid login")
                viewModel.isSubmitting = false
                Toast.makeText(applicationContext, getString(R.string.error_invalid_login, getString(status.str)), Toast.LENGTH_LONG).show()
            }
        }
    }


    class LoginUrlInfo {
        var serverAddress: String = ""
        var username: String = ""
        var password: String = ""
    }

}
