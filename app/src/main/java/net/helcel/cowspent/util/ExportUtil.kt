package net.helcel.cowspent.util

import net.helcel.cowspent.model.*
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper

object ExportUtil {

    /** Quotes a field the way RFC4180 (and opencsv, and Cospend) expect. */
    private fun q(value: String?): String = "\"" + (value ?: "").replace("\"", "\"\"") + "\""

    @JvmStatic
    fun createExportContent(db: CowspentSQLiteOpenHelper, projectId: Long): String {
        var fileContent = ""

        // get information
        val project = db.getProject(projectId) ?: return ""
        val membersById: MutableMap<Long, DBMember> = HashMap()
        val members = db.getMembersOfProject(projectId, null)
        for (m in members) {
            membersById[m.id] = m
        }
        val bills = db.getBillsOfProject(projectId).toMutableList()

        // write header.
        fileContent += "what,amount,date,timestamp,payer_name,payer_weight,payer_active,owers,repeat,categoryid,paymentmode,paymentmodeid,comment\n"

        // write members
        for (m in members) {
            val fakeBill = DBBill(
                0, 0, projectId, m.id, 1.0, 666,
                "deleteMeIfYouWant", DBBill.STATE_OK, DBBill.NON_REPEATED,
                DBBill.PAYMODE_NONE, 0, "", 0
            )
            val fakeBillOwers: MutableList<DBBillOwer> = ArrayList()
            fakeBillOwers.add(DBBillOwer(0, 0, m.id))
            fakeBill.billOwers = fakeBillOwers
            bills.add(0, fakeBill)
        }

        // write bills
        for (b in bills) {
            val payerId = b.payerId
            val payer = membersById[payerId] ?: continue
            val payerName = payer.name
            val payerWeight = payer.weight
            val payerActive = if (payer.isActivated) 1 else 0
            val owersTxt = b.billOwers.mapNotNull { membersById[it.memberId]?.name }.joinToString(",")
            fileContent += "${q(b.what)},${b.amount},${b.date},${b.timestamp},${q(payerName)}," +
                    "$payerWeight,$payerActive,${q(owersTxt)},${b.repeat ?: DBBill.NON_REPEATED}," +
                    "${b.categoryId},${b.paymentMode ?: DBBill.PAYMODE_NONE},${b.paymentModeId}," +
                    "${q(b.comment)}\n"
        }

        // write categories
        val cats = db.getCategories(projectId)
        if (cats.isNotEmpty()) {
            fileContent += "\ncategoryname,categoryid,icon,color\n"
            for (cat in cats) {
                fileContent += "${q(cat.name)},${cat.id},${q(cat.icon)},${q(cat.color)}\n"
            }
        }

        // write payment modes
        val pms = db.getPaymentModes(projectId)
        if (pms.isNotEmpty()) {
            fileContent += "\npaymentmodename,paymentmodeid,icon,color\n"
            for (pm in pms) {
                fileContent += "${q(pm.name)},${pm.id},${q(pm.icon)},${q(pm.color)}\n"
            }
        }

        // write currencies
        val curs = db.getCurrencies(projectId)
        if (curs.isNotEmpty() && project.currencyName != null &&
            project.currencyName!!.isNotEmpty() && project.currencyName != "null"
        ) {
            fileContent += "\ncurrencyname,exchange_rate\n"
            fileContent += "${q(project.currencyName)},1\n"
            for (cur in curs) {
                fileContent += "${q(cur.name)},${cur.exchangeRate}\n"
            }
        }

        return fileContent
    }

    @JvmStatic
    fun createExportFileName(db: CowspentSQLiteOpenHelper, projectId: Long): String {
        val project = db.getProject(projectId) ?: return "export.csv"
        return if (project.name.isEmpty()) {
            project.remoteId + ".csv"
        } else {
            project.name + ".csv"
        }
    }
}
