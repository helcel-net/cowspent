package net.helcel.cowspent.util

import android.content.Context
import net.helcel.cowspent.R
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBPaymentMode

object CategoryUtils {

    fun getDefaultCategories(context: Context, projectId: Long): List<DBCategory> {
        return listOf(
            DBCategory(0,
                DBBill.CATEGORY_GROCERIES, projectId, context.getString(R.string.category_groceries), "\uD83D\uDED2", "#ffaa00"),
            DBCategory(0, DBBill.CATEGORY_LEISURE, projectId, context.getString(R.string.category_leisure), "\uD83C\uDF89", "#aa55ff"),
            DBCategory(0, DBBill.CATEGORY_RENT, projectId, context.getString(R.string.category_rent), "\uD83C\uDFE0", "#da8733"),
            DBCategory(0, DBBill.CATEGORY_BILLS, projectId, context.getString(R.string.category_bills), "\uD83C\uDF29", "#4aa6b0"),
            DBCategory(0, DBBill.CATEGORY_CULTURE, projectId, context.getString(R.string.category_excursion), "\uD83D\uDEB8", "#0055ff"),
            DBCategory(0, DBBill.CATEGORY_HEALTH, projectId, context.getString(R.string.category_health), "\uD83D\uDC9A", "#bf090c"),
            DBCategory(0, DBBill.CATEGORY_SHOPPING, projectId, context.getString(R.string.category_shopping), "\uD83D\uDECD", "#e167d1"),
            DBCategory(0, DBBill.CATEGORY_REIMBURSEMENT, projectId, context.getString(R.string.category_reimbursement), "\uD83D\uDCB0", "#00ced1"),
            DBCategory(0, DBBill.CATEGORY_RESTAURANT, projectId, context.getString(R.string.category_restaurant), "\uD83C\uDF74", "#d0d5e1"),
            DBCategory(0, DBBill.CATEGORY_ACCOMMODATION, projectId, context.getString(R.string.category_accomodation), "\uD83D\uDECC", "#5de1a3"),
            DBCategory(0, DBBill.CATEGORY_TRANSPORT, projectId, context.getString(R.string.category_transport), "\uD83D\uDE8C", "#6f2ee1"),
            DBCategory(0, DBBill.CATEGORY_SPORT, projectId, context.getString(R.string.category_sport), "\uD83C\uDFBE", "#69e177")
        )
    }

    fun getDefaultPaymentModes(context: Context, projectId: Long): List<DBPaymentMode> {
        return listOf(
            DBPaymentMode(0, DBBill.PAYMODE_ID_CARD, projectId, context.getString(R.string.payment_mode_credit_card), "\uD83D\uDCB3", "#ff7f50"),
            DBPaymentMode(0, DBBill.PAYMODE_ID_CASH, projectId, context.getString(R.string.payment_mode_cash), "\uD83D\uDCB5", "#556b2f"),
            DBPaymentMode(0, DBBill.PAYMODE_ID_CHECK, projectId, context.getString(R.string.payment_mode_check), "\uD83C\uDFAB", "#a9a9a9"),
            DBPaymentMode(0, DBBill.PAYMODE_ID_TRANSFER, projectId, context.getString(R.string.payment_mode_transfer), "⇄", "#00ced1"),
            DBPaymentMode(0, DBBill.PAYMODE_ID_ONLINE_SERVICE, projectId, context.getString(R.string.payment_mode_online), "\uD83C\uDF0E", "#9932cc")
        )
    }
}
