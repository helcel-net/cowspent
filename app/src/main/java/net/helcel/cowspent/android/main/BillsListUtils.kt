package net.helcel.cowspent.android.main

import android.content.Context
import android.content.Intent
import net.helcel.cowspent.R
import net.helcel.cowspent.model.*
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.IRefreshBillsListCallback
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.round

object BillsListUtils {
    fun groupAndSectionBills(
        bills: List<DBBill>,
        membersMap: Map<Long, DBMember>,
        sdf: SimpleDateFormat,
        context: Context
    ): List<Item> {
        val groupedBillsList = mutableListOf<DBBill>()
        val groups = bills.groupBy { "${it.what}|${it.date}|${it.time}|${it.payerId}" }
        val seenGroups = mutableSetOf<String>()
        
        for (bill in bills) {
            val groupKey = "${bill.what}|${bill.date}|${bill.time}|${bill.payerId}"
            if (groupKey !in seenGroups) {
                val group = groups[groupKey]!!
                if (group.size == 1) {
                    groupedBillsList.add(bill)
                } else {
                    val groupedBill = GroupedBill(group)
                    val payerName = membersMap[groupedBill.payerId]?.name ?: groupedBill.payerId.toString()
                    val allOwerIds = group.flatMap { it.billOwersIds }.distinct()
                    val owersNames = allOwerIds.joinToString(", ") { id ->
                        membersMap[id]?.name ?: id.toString()
                    }
                    groupedBill.formattedSubtitle = "$payerName \u2192 $owersNames"
                    groupedBillsList.add(groupedBill)
                }
                seenGroups.add(groupKey)
            }
        }

        val itemList: MutableList<Item> = ArrayList()
        var lastDate = ""
        val androidDateFormat = android.text.format.DateFormat.getDateFormat(context)
        
        for (bill in groupedBillsList) {
            val billDate = bill.date
            if (billDate != lastDate) {
                val date = try { sdf.parse(billDate) } catch (_: Exception) { null }
                val formattedDate = date?.let { androidDateFormat.format(it) } ?: billDate
                itemList.add(SectionItem(formattedDate))
                lastDate = billDate
            }
            itemList.add(bill)
        }
        return itemList
    }

    fun shareSettlement(
        context: Context,
        proj: DBProject,
        transactions: List<Transaction>,
        memberIdToName: Map<Long, String>
    ) {
        val projectName = proj.name.ifEmpty { proj.remoteId }
        var text = context.getString(R.string.share_settle_intro, projectName) + "\n"
        for (t in transactions) {
            val amount = round(t.amount * 100.0) / 100.0
            text += "\n" + context.getString(
                R.string.share_settle_sentence,
                memberIdToName[t.owerMemberId],
                memberIdToName[t.receiverMemberId],
                amount
            )
        }
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "text/plain"
        shareIntent.putExtra(
            Intent.EXTRA_SUBJECT,
            context.getString(R.string.share_settle_title, projectName)
        )
        shareIntent.putExtra(Intent.EXTRA_TEXT, text)
        val chooserIntent = Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_settle_title, projectName)
        )
        context.startActivity(chooserIntent)
    }

    fun createBillsFromTransactions(
        db: CowspentSQLiteOpenHelper,
        projectId: Long,
        transactions: List<Transaction>,
        refreshCallback: IRefreshBillsListCallback,
        context: Context
    ) {
        val timestamp = System.currentTimeMillis() / 1000
        for (t in transactions) {
            val owerId = t.owerMemberId
            val receiverId = t.receiverMemberId
            val amount = t.amount
            val bill = DBBill(
                0, 0, projectId, owerId, amount,
                timestamp, context.getString(R.string.settle_bill_what),
                DBBill.STATE_ADDED, DBBill.NON_REPEATED,
                DBBill.PAYMODE_NONE, DBBill.CATEGORY_NONE,
                "", DBBill.PAYMODE_ID_NONE
            )
            bill.billOwers += DBBillOwer(0, 0, receiverId)
            db.addBill(bill)
        }
        refreshCallback.refreshLists(true)
    }
}
