package net.helcel.cowspent.util

import android.content.Context
import androidx.preference.PreferenceManager
import net.helcel.cowspent.R

/**
 * The SyncOnOpen preference: how often opening the app refreshes the account and every project.
 *
 * The choices and the default live here rather than in the settings screen so that the screen and
 * the sync trigger cannot disagree about what is in effect.
 */
object SyncSettings {

    /** Steps offered by the slider, in minutes. */
    val INTERVAL_CHOICES_MINUTES = listOf(1, 10, 60, 1440)

    const val DEFAULT_INTERVAL_MINUTES = 10

    /**
     * The configured interval in minutes. A stored value that is not one of the offered steps —
     * from a restored backup, or a build that changed the steps — falls back to the default
     * rather than being displayed as the first step while a different value drives the sync.
     */
    fun intervalMinutes(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val stored = prefs.getInt(
            context.getString(R.string.pref_key_auto_sync_on_open),
            DEFAULT_INTERVAL_MINUTES
        )
        return if (stored in INTERVAL_CHOICES_MINUTES) stored else DEFAULT_INTERVAL_MINUTES
    }
}
