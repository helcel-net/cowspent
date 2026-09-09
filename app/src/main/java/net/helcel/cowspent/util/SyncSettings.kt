package net.helcel.cowspent.util

import android.content.Context
import androidx.preference.PreferenceManager
import net.helcel.cowspent.R

/**
 * The two independent delays that govern syncing.
 *
 * They used to be one setting, which meant the app-open throttle and the full-sync delay could
 * not be tuned apart even though they answer different questions. The choices and defaults live
 * here rather than in the settings screen so the screen and the sync triggers cannot disagree
 * about what is in effect.
 */
object SyncSettings {

    /**
     * How long opening the app waits before refreshing the account and every project again, so
     * that resuming within the interval does not repeat the work. Not user-facing: it governs
     * background catch-up, not anything the user asked for.
     */
    const val OPEN_SYNC_INTERVAL_MINUTES = 10

    /**
     * Steps offered for the full sync delay, in minutes. 0 means every manual refresh is a full
     * sync; anything else lets a refresh inside the window settle for fetching just the changes.
     */
    val FULL_SYNC_DELAY_CHOICES_MINUTES = listOf(0, 60, 1440, 10080)

    /**
     * Only consulted once Extra Features is on - with it off every pull-to-refresh is a full
     * sync, whatever is stored here.
     */
    const val DEFAULT_FULL_SYNC_DELAY_MINUTES = 1440

    /**
     * The configured delay in minutes. A stored value that is not one of the offered steps - from
     * a restored backup, or a build that changed the steps - falls back to the default rather
     * than being displayed as one step while a different value drives the sync.
     */
    fun fullSyncDelayMinutes(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val stored = prefs.getInt(
            context.getString(R.string.pref_key_full_sync_delay),
            DEFAULT_FULL_SYNC_DELAY_MINUTES
        )
        return if (stored in FULL_SYNC_DELAY_CHOICES_MINUTES) stored else DEFAULT_FULL_SYNC_DELAY_MINUTES
    }
}
