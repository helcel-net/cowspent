package net.helcel.cowspent.util

import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.model.DBPaymentMode

object BillFormatter {
    fun formatBills(
        bills: List<DBBill>,
        membersMap: Map<Long, DBMember>,
        categoriesMap: Map<Long, DBCategory>,
        paymentModesMap: Map<Long, DBPaymentMode>
    ) {
        for (bill in bills) {
            var whatPrefix = ""
            val pm = paymentModesMap[bill.paymentModeRemoteId.toLong()]
            if (pm != null) {
                whatPrefix += pm.icon + " "
            } else {
                when (bill.paymentModeRemoteId) {
                    DBBill.PAYMODE_ID_CARD -> whatPrefix += "\uD83D\uDCB3 "
                    DBBill.PAYMODE_ID_CASH -> whatPrefix += "\uD83D\uDCB5 "
                    DBBill.PAYMODE_ID_CHECK -> whatPrefix += "\uD83C\uDFAB "
                    DBBill.PAYMODE_ID_TRANSFER -> whatPrefix += "⇄ "
                    DBBill.PAYMODE_ID_ONLINE_SERVICE -> whatPrefix += "\uD83C\uDF0E "
                }
            }

            val cat = categoriesMap[bill.categoryRemoteId.toLong()]
            if (cat != null) {
                whatPrefix += cat.icon + " "
            } else {
                when (bill.categoryRemoteId) {
                    DBBill.CATEGORY_GROCERIES -> whatPrefix += "\uD83D\uDED2 "
                    DBBill.CATEGORY_LEISURE -> whatPrefix += "\uD83C\uDF89 "
                    DBBill.CATEGORY_RENT -> whatPrefix += "\uD83C\uDFE0 "
                    DBBill.CATEGORY_BILLS -> whatPrefix += "\uD83C\uDF29 "
                    DBBill.CATEGORY_CULTURE -> whatPrefix += "\uD83D\uDEB8 "
                    DBBill.CATEGORY_HEALTH -> whatPrefix += "\uD83D\uDC9A "
                    DBBill.CATEGORY_SHOPPING -> whatPrefix += "\uD83D\uDECD "
                    DBBill.CATEGORY_REIMBURSEMENT -> whatPrefix += "\uD83D\uDCB0 "
                    DBBill.CATEGORY_RESTAURANT -> whatPrefix += "\uD83C\uDF74 "
                    DBBill.CATEGORY_ACCOMMODATION -> whatPrefix += "\uD83D\uDECC "
                    DBBill.CATEGORY_TRANSPORT -> whatPrefix += "\uD83D\uDE8C "
                    DBBill.CATEGORY_SPORT -> whatPrefix += "\uD83C\uDFBE "
                }
            }
            bill.formattedWhat = whatPrefix + bill.what

            val payerName = membersMap[bill.payerId]?.name ?: bill.payerId.toString()
            val owersNames = bill.billOwersIds.joinToString(", ") { id ->
                membersMap[id]?.name ?: id.toString()
            }
            bill.formattedSubtitle = "$payerName \u2192 $owersNames"
        }
    }
}
