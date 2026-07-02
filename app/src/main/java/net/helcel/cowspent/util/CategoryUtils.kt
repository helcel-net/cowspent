package net.helcel.cowspent.util

import android.content.Context
import net.helcel.cowspent.R
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBPaymentMode

object CategoryUtils {

    fun getDefaultCategories(context: Context, projectId: Long): List<DBCategory> {
        return listOf(
            DBCategory(DBBill.CATEGORY_GROCERIES,
                DBBill.CATEGORY_GROCERIES, projectId, context.getString(R.string.category_groceries), "\uD83D\uDED2", "#ffaa00"),
            DBCategory(DBBill.CATEGORY_LEISURE,
                DBBill.CATEGORY_LEISURE, projectId, context.getString(R.string.category_leisure), "\uD83C\uDF89", "#aa55ff"),
            DBCategory(DBBill.CATEGORY_RENT,
                DBBill.CATEGORY_RENT, projectId, context.getString(R.string.category_rent), "\uD83C\uDFE0", "#da8733"),
            DBCategory(DBBill.CATEGORY_BILLS,
                DBBill.CATEGORY_BILLS, projectId, context.getString(R.string.category_bills), "\uD83C\uDF29", "#4aa6b0"),
            DBCategory(DBBill.CATEGORY_CULTURE,
                DBBill.CATEGORY_CULTURE, projectId, context.getString(R.string.category_excursion), "\uD83D\uDEB8", "#0055ff"),
            DBCategory(DBBill.CATEGORY_HEALTH,
                DBBill.CATEGORY_HEALTH, projectId, context.getString(R.string.category_health), "\uD83D\uDC9A", "#bf090c"),
            DBCategory(DBBill.CATEGORY_SHOPPING,
                DBBill.CATEGORY_SHOPPING, projectId, context.getString(R.string.category_shopping), "\uD83D\uDECD", "#e167d1"),
            DBCategory(DBBill.CATEGORY_REIMBURSEMENT,
                DBBill.CATEGORY_REIMBURSEMENT, projectId, context.getString(R.string.category_reimbursement), "\uD83D\uDCB0", "#00ced1"),
            DBCategory(DBBill.CATEGORY_RESTAURANT,
                DBBill.CATEGORY_RESTAURANT, projectId, context.getString(R.string.category_restaurant), "\uD83C\uDF74", "#d0d5e1"),
            DBCategory(DBBill.CATEGORY_ACCOMMODATION,
                DBBill.CATEGORY_ACCOMMODATION, projectId, context.getString(R.string.category_accomodation), "\uD83D\uDECC", "#5de1a3"),
            DBCategory(DBBill.CATEGORY_TRANSPORT,
                DBBill.CATEGORY_TRANSPORT, projectId, context.getString(R.string.category_transport), "\uD83D\uDE8C", "#6f2ee1"),
            DBCategory(DBBill.CATEGORY_SPORT,
                DBBill.CATEGORY_SPORT, projectId, context.getString(R.string.category_sport), "\uD83C\uDFBE", "#69e177")
        )
    }

    fun getDefaultPaymentModes(context: Context, projectId: Long): List<DBPaymentMode> {
        return listOf(
            DBPaymentMode(DBBill.PAYMODE_ID_CARD, DBBill.PAYMODE_ID_CARD, projectId, context.getString(R.string.payment_mode_credit_card), "\uD83D\uDCB3", "#ff7f50"),
            DBPaymentMode(DBBill.PAYMODE_ID_CASH, DBBill.PAYMODE_ID_CASH, projectId, context.getString(R.string.payment_mode_cash), "\uD83D\uDCB5", "#556b2f"),
            DBPaymentMode(DBBill.PAYMODE_ID_CHECK, DBBill.PAYMODE_ID_CHECK, projectId, context.getString(R.string.payment_mode_check), "\uD83C\uDFAB", "#a9a9a9"),
            DBPaymentMode(DBBill.PAYMODE_ID_TRANSFER, DBBill.PAYMODE_ID_TRANSFER, projectId, context.getString(R.string.payment_mode_transfer), "⇄", "#00ced1"),
            DBPaymentMode(DBBill.PAYMODE_ID_ONLINE_SERVICE, DBBill.PAYMODE_ID_ONLINE_SERVICE, projectId, context.getString(R.string.payment_mode_online), "\uD83C\uDF0E", "#9932cc")
        )
    }
}
