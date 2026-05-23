package net.helcel.cowspent.util

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import net.helcel.cowspent.R

class Cowspent : Application() {

    override fun onCreate() {
        setAppTheme(getAppTheme(applicationContext))
        super.onCreate()
    }

    companion object {

        fun setAppTheme(mode: Int) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        fun getAppTheme(context: Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val strValue = prefs.getString(
                context.getString(R.string.pref_key_night_mode),
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString()
            )
            return strValue?.toInt() ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
