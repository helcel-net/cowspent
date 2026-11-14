package net.helcel.cowspent.util

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import net.helcel.cowspent.R
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Where a new bill takes its payer, owers, category, payment mode and repeat from. */
enum class PrefillMode(val id: String) {
    /** Start from an empty form (the project's last payer is still suggested). */
    OFF("off"),

    /** The most recent bill of the project, whoever created it and on whichever device. */
    LATEST_IN_PROJECT("project"),

    /**
     * The last bill created on this device. When entering a batch of older bills, the project's
     * newest bill keeps changing under you as other members sync theirs, while this one only
     * follows what you typed.
     */
    LATEST_ON_DEVICE("device"),

    /** Values the user saved once for the project and wants every time. */
    PRESET("preset");

    companion object {
        fun fromId(id: String?): PrefillMode? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The part of a bill that pre-filling carries over. Amount, description, date and comment are
 * deliberately absent: those describe the one expense, not the habit.
 */
data class BillTemplate(
    val payerId: Long,
    val owerIds: List<Long>,
    val categoryId: Long,
    val paymentModeId: Long,
    val paymentMode: String?,
    val repeat: String
) {
    fun toJson(): String = JSONObject()
        .put(KEY_PAYER, payerId)
        .put(KEY_OWERS, JSONArray(owerIds))
        .put(KEY_CATEGORY, categoryId)
        .put(KEY_PAYMENT_MODE_ID, paymentModeId)
        .put(KEY_PAYMENT_MODE, paymentMode ?: JSONObject.NULL)
        .put(KEY_REPEAT, repeat)
        .toString()

    companion object {
        private const val KEY_PAYER = "payer"
        private const val KEY_OWERS = "owers"
        private const val KEY_CATEGORY = "category"
        private const val KEY_PAYMENT_MODE_ID = "paymentModeId"
        private const val KEY_PAYMENT_MODE = "paymentMode"
        private const val KEY_REPEAT = "repeat"

        fun of(bill: DBBill) = BillTemplate(
            payerId = bill.payerId,
            owerIds = bill.billOwersIds,
            categoryId = bill.categoryId,
            paymentModeId = bill.paymentModeId,
            paymentMode = bill.paymentMode,
            repeat = bill.repeat ?: DBBill.NON_REPEATED
        )

        /** Null for a missing or unreadable value, so a corrupt entry acts as "nothing saved". */
        fun fromJson(json: String?): BillTemplate? {
            if (json.isNullOrEmpty()) return null
            return try {
                val o = JSONObject(json)
                val owers = o.getJSONArray(KEY_OWERS)
                BillTemplate(
                    payerId = o.getLong(KEY_PAYER),
                    owerIds = List(owers.length()) { owers.getLong(it) },
                    categoryId = o.optLong(KEY_CATEGORY, DBBill.CATEGORY_NONE),
                    paymentModeId = o.optLong(KEY_PAYMENT_MODE_ID, DBBill.PAYMODE_ID_NONE),
                    paymentMode = if (o.isNull(KEY_PAYMENT_MODE)) null else o.getString(KEY_PAYMENT_MODE),
                    repeat = o.optString(KEY_REPEAT, DBBill.NON_REPEATED)
                )
            } catch (_: JSONException) {
                null
            }
        }
    }
}

/**
 * Per-project pre-fill choice and the templates behind it. Everything lives in this device's
 * preferences: "latest on this device" is device-local by definition, and the mode and preset
 * describe how this user enters bills, not something the other members should get.
 */
object BillPrefill {

    private fun modeKey(projectId: Long) = "prefill_mode_$projectId"
    private fun deviceKey(projectId: Long) = "prefill_device_$projectId"
    private fun presetKey(projectId: Long) = "prefill_preset_$projectId"

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun mode(context: Context, projectId: Long): PrefillMode {
        val prefs = prefs(context)
        PrefillMode.fromId(prefs.getString(modeKey(projectId), null))?.let { return it }
        // This used to be one global on/off switch. Projects that never picked a mode keep
        // behaving the way that switch said.
        val legacyOn = prefs.getBoolean(context.getString(R.string.pref_key_fill_new_bill_from_last), false)
        return if (legacyOn) PrefillMode.LATEST_IN_PROJECT else PrefillMode.OFF
    }

    fun setMode(context: Context, projectId: Long, mode: PrefillMode) {
        prefs(context).edit { putString(modeKey(projectId), mode.id) }
    }

    fun deviceTemplate(context: Context, projectId: Long): BillTemplate? =
        BillTemplate.fromJson(prefs(context).getString(deviceKey(projectId), null))

    /** Called for every bill created here, whatever the mode, so switching to it later works. */
    fun recordCreatedOnDevice(context: Context, projectId: Long, template: BillTemplate) {
        prefs(context).edit { putString(deviceKey(projectId), template.toJson()) }
    }

    fun preset(context: Context, projectId: Long): BillTemplate? =
        BillTemplate.fromJson(prefs(context).getString(presetKey(projectId), null))

    fun savePreset(context: Context, projectId: Long, template: BillTemplate) {
        prefs(context).edit { putString(presetKey(projectId), template.toJson()) }
    }

    /** Forget everything stored for a project, e.g. once it is removed from the device. */
    fun clear(context: Context, projectId: Long) {
        prefs(context).edit {
            remove(modeKey(projectId))
            remove(deviceKey(projectId))
            remove(presetKey(projectId))
        }
    }

    /**
     * The template [mode] points at, or null when there is nothing to fill from: the mode is
     * off, the project has no bill yet, nothing was created on this device, or no preset was
     * saved. Callers then fall back to an empty form.
     */
    @WorkerThread
    fun templateFor(
        context: Context,
        db: CowspentSQLiteOpenHelper,
        projectId: Long,
        mode: PrefillMode = mode(context, projectId)
    ): BillTemplate? = when (mode) {
        PrefillMode.OFF -> null
        PrefillMode.LATEST_IN_PROJECT -> db.getLastBillOfProject(projectId)?.let(BillTemplate::of)
        PrefillMode.LATEST_ON_DEVICE -> deviceTemplate(context, projectId)
        PrefillMode.PRESET -> preset(context, projectId)
    }
}
