package net.helcel.cowspent.android.account

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.CospendClientUtil
import net.helcel.cowspent.util.SecureStorage

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "AccountViewModel"
    }

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

    /** Non-null while the logout confirmation is up, carrying what it would cost. */
    var logoutImpact by mutableStateOf<LogoutImpact?>(null)
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

        if (!url.isNullOrEmpty() && !username.isNullOrEmpty()) {
            viewModelScope.launch {
                val password = if (useSso) {
                    ""
                } else {
                    SecureStorage.getPassword(getApplication(), AccountActivity.SETTINGS_PASSWORD)
                }

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

    private fun projectsTheAccountProvided(db: CowspentSQLiteOpenHelper): List<DBProject> {
        val offered = db.accountProjects
        val cospendPath = "/index.php/apps/cospend"
        return db.projects.filter { project ->
            offered.any {
                it.remoteId == project.remoteId &&
                        project.serverUrl?.replace("/+$".toRegex(), "") ==
                        it.ncUrl.replace("/+$".toRegex(), "") + cospendPath
            }
        }
    }
    data class LogoutImpact(val projects: Int, val unsyncedBills: Int)

    private fun measureLogoutImpact(): LogoutImpact {
        val db = CowspentSQLiteOpenHelper.getInstance(getApplication())
        val projects = projectsTheAccountProvided(db)
        val unsynced = projects.sumOf { db.countUnsyncedBills(it.id) }
        return LogoutImpact(projects.size, unsynced)
    }

    fun requestLogout() {
        viewModelScope.launch {
            logoutImpact = withContext(Dispatchers.IO) {
                try {
                    measureLogoutImpact()
                } catch (e: Exception) {
                    // Never let a failed count block signing out - just ask without the detail.
                    Log.e(TAG, "Could not measure what logging out would remove", e)
                    LogoutImpact(0, 0)
                }
            }
        }
    }

    fun cancelLogout() {
        logoutImpact = null
    }

    fun confirmLogout() {
        logoutImpact = null
        logout()
    }

    private fun forgetProjectsTheAccountProvided() {
        try {
            val db = CowspentSQLiteOpenHelper.getInstance(getApplication())
            projectsTheAccountProvided(db).forEach { db.deleteProject(it.id) }
            db.clearAccountProjects()
        } catch (e: Exception) {
            Log.e(TAG, "Could not remove the account's projects on logout", e)
        }
    }

    @VisibleForTesting
    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { forgetProjectsTheAccountProvided() }
            SecureStorage.removePassword(getApplication(), AccountActivity.SETTINGS_PASSWORD)
        }
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
