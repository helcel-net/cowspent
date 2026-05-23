package net.helcel.cowspent.android.account

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.util.CospendClientUtil

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    var useSso by mutableStateOf(preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false))
    var serverUrl by mutableStateOf(
        if (preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)) {
            preferences.getString(AccountActivity.SETTINGS_SSO_URL, "") ?: ""
        } else {
            preferences.getString(AccountActivity.SETTINGS_URL, "") ?: ""
        }
    )
    var username by mutableStateOf(
        if (preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)) {
            preferences.getString(AccountActivity.SETTINGS_SSO_USERNAME, "") ?: ""
        } else {
            preferences.getString(AccountActivity.SETTINGS_USERNAME, "") ?: ""
        }
    )
    var password by mutableStateOf("")

    var isUrlValid by mutableStateOf(false)
    var isSubmitting by mutableStateOf(false)
    var showUrlWarning by mutableStateOf(false)
    var showWebView by mutableStateOf(false)

    var isLoggedIn by mutableStateOf(false)
        private set

    var isValidatingLogin by mutableStateOf(false)
        private set

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        val url = if (useSso) {
            preferences.getString(AccountActivity.SETTINGS_SSO_URL, "")
        } else {
            preferences.getString(AccountActivity.SETTINGS_URL, "")
        }
        val username = if (useSso) {
            preferences.getString(AccountActivity.SETTINGS_SSO_USERNAME, "")
        } else {
            preferences.getString(AccountActivity.SETTINGS_USERNAME, "")
        }
        val password = if (useSso) {
            ""
        } else {
            preferences.getString(AccountActivity.SETTINGS_PASSWORD, "")
        }

        if (!url.isNullOrEmpty() && !username.isNullOrEmpty()) {
            viewModelScope.launch {
                isValidatingLogin = true
                isLoggedIn = withContext(Dispatchers.IO) {
                    if (useSso) {
                        true
                    } else {
                        !password.isNullOrEmpty() &&
                                CospendClientUtil.isValidLogin(
                                    url,
                                    username,
                                    password
                                ) == CospendClientUtil.LoginStatus.OK
                    }
                }
                isValidatingLogin = false
            }
        }
    }

    fun logout() {
        preferences.edit {
            remove(AccountActivity.SETTINGS_USE_SSO)
            remove(AccountActivity.SETTINGS_SSO_URL)
            remove(AccountActivity.SETTINGS_SSO_USERNAME)
            remove(AccountActivity.SETTINGS_URL)
            remove(AccountActivity.SETTINGS_USERNAME)
            remove(AccountActivity.SETTINGS_PASSWORD)
            remove(AccountActivity.SETTINGS_KEY_ETAG)
            remove(AccountActivity.SETTINGS_KEY_LAST_MODIFIED)
        }

        useSso = false
        serverUrl = ""
        username = ""
        password = ""
        isLoggedIn = false
    }

    fun validateUrl() {
        val formattedUrl = CospendClientUtil.formatURL(serverUrl)
        showUrlWarning = CospendClientUtil.isHttp(formattedUrl) && !useSso

        viewModelScope.launch {
            val valid = withContext(Dispatchers.IO) {
                CospendClientUtil.isValidURL(formattedUrl)
            }
            isUrlValid = valid
        }
    }

    val isFormValid: Boolean
        get() = useSso || (isUrlValid && username.isNotEmpty())
}
