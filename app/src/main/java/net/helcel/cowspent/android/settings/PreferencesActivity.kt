package net.helcel.cowspent.android.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NavUtils
import androidx.preference.PreferenceManager
import net.helcel.cowspent.android.about.AboutActivity
import net.helcel.cowspent.android.account.AccountActivity
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.ColorUtils

/**
 * Allows to change application settings.
 */
class PreferencesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavUtils.navigateUpFromSameTask(this@PreferencesActivity)
            }
        })

        setResult(RESULT_CANCELED)

        setContent {
            val context = LocalContext.current
            val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
            val nightModeKey = stringResource(net.helcel.cowspent.R.string.pref_key_night_mode)

            var appColor by remember {
                mutableIntStateOf(ColorUtils.primaryColor(context))
            }
            var nightMode by remember {
                mutableStateOf(sharedPreferences.getString(nightModeKey, "-1") ?: "-1")
            }

            val isDarkTheme = when (nightMode) {
                "1" -> false
                "2" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ThemeUtils.CowspentTheme(accentColor = appColor, darkTheme = isDarkTheme) {
                SettingsScreen(
                    onBack = { NavUtils.navigateUpFromSameTask(this) },
                    onAccountSettingsClick = {
                        startActivity(Intent(this, AccountActivity::class.java))
                    },
                    onAboutClick = {
                        startActivity(Intent(this, AboutActivity::class.java))
                    },
                    onColorSelected = { appColor = it },
                    onNightModeChanged = { nightMode = it }
                )
            }
        }
    }
}
