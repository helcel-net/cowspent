package net.helcel.cowspent.model

import android.util.Log
import java.io.Serializable
import java.util.Calendar
import java.util.Locale

open class DBBill(
    var id: Long,
    var remoteId: Long,
    var projectId: Long,
    var payerId: Long,
    var amount: Double,
    var timestamp: Long,
    var what: String,
    var state: Int,
    var repeat: String?,
    var paymentMode: String?,
    var categoryRemoteId: Int,
    var comment: String?,
    var paymentModeRemoteId: Int
) : Item, Serializable {

    var formattedWhat: String = ""
    var formattedSubtitle: String = ""

    var billOwers: List<DBBillOwer> = ArrayList()

    val billOwersIds: List<Long>
        get() {
            val result: MutableList<Long> = ArrayList()
            for (bo in billOwers) {
                result.add(bo.memberId)
            }
            return result
        }

    val date: String
        get() {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timestamp * 1000
            Log.v("ll", "[$what] get date ts $timestamp year ${cal[Calendar.YEAR]}")
            val month = cal[Calendar.MONTH] + 1
            val day = cal[Calendar.DAY_OF_MONTH]
            return "${cal[Calendar.YEAR]}-${String.format(Locale.ROOT, "%02d", month)}-${
                String.format(Locale.ROOT, "%02d", day)
            }"
        }

    val time: String
        get() {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timestamp * 1000
            return "${String.format(Locale.ROOT, "%02d", cal[Calendar.HOUR_OF_DAY])}:${
                String.format(Locale.ROOT, "%02d", cal[Calendar.MINUTE])
            }"
        }


    override fun toString(): String {
        return "#DBBill$id/$remoteId,$projectId, $payerId, $amount, $timestamp, $what, $state, $repeat, $paymentMode, $categoryRemoteId"
    }

    override fun isSection(): Boolean {
        return false
    }

    companion object {
        const val PAYMODE_NONE = "n"
        const val PAYMODE_CARD = "c"
        const val PAYMODE_CASH = "b"
        const val PAYMODE_CHECK = "f"
        const val PAYMODE_TRANSFER = "t"
        const val PAYMODE_ONLINE_SERVICE = "o"

        const val PAYMODE_ID_NONE = 0
        const val PAYMODE_ID_CARD = -1
        const val PAYMODE_ID_CASH = -2
        const val PAYMODE_ID_CHECK = -3
        const val PAYMODE_ID_TRANSFER = -4
        const val PAYMODE_ID_ONLINE_SERVICE = -5

        @JvmField
        val oldPmIdToNew: Map<String, Int> = object : HashMap<String, Int>() {
            init {
                put(PAYMODE_NONE, PAYMODE_ID_NONE)
                put(PAYMODE_CARD, PAYMODE_ID_CARD)
                put(PAYMODE_CASH, PAYMODE_ID_CASH)
                put(PAYMODE_CHECK, PAYMODE_ID_CHECK)
                put(PAYMODE_TRANSFER, PAYMODE_ID_TRANSFER)
                put(PAYMODE_ONLINE_SERVICE, PAYMODE_ID_ONLINE_SERVICE)
            }
        }

        const val CATEGORY_NONE = 0
        const val CATEGORY_GROCERIES = -1
        const val CATEGORY_LEISURE = -2
        const val CATEGORY_RENT = -3
        const val CATEGORY_BILLS = -4
        const val CATEGORY_CULTURE = -5
        const val CATEGORY_HEALTH = -6
        const val CATEGORY_SHOPPING = -10
        const val CATEGORY_REIMBURSEMENT = -11
        const val CATEGORY_RESTAURANT = -12
        const val CATEGORY_ACCOMMODATION = -13
        const val CATEGORY_TRANSPORT = -14
        const val CATEGORY_SPORT = -15

        const val STATE_OK = 0
        const val STATE_ADDED = 1
        const val STATE_EDITED = 2
        const val STATE_DELETED = 3

        const val NON_REPEATED = "n"
        const val REPEAT_DAY = "d"
        const val REPEAT_WEEK = "w"
        const val REPEAT_FORTNIGHT = "b"
        const val REPEAT_MONTH = "m"
        const val REPEAT_YEAR = "y"
    }
}
