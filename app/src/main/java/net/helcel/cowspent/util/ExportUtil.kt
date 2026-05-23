package net.helcel.cowspent.util

import net.helcel.cowspent.model.*
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper

object ExportUtil {

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

        // write header
        fileContent += "what,amount,date,timestamp,payer_name,payer_weight,payer_active,owers,repeat,categoryid,paymentmode\n"

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
            val billOwers = b.billOwers
            var owersTxt = ""
            for (bo in billOwers) {
                owersTxt += membersById[bo.memberId]?.name + ","
            }
            owersTxt = owersTxt.replace(",$".toRegex(), "")
            fileContent += "\"${b.what}\",${b.amount},${b.date},${b.timestamp},\"$payerName\"," +
                    "$payerWeight,$payerActive,\"$owersTxt\",${b.repeat},${b.categoryRemoteId}," +
                    "${b.paymentMode}\n"
        }

        // write categories
        val cats = db.getCategories(projectId)
        if (cats.isNotEmpty()) {
            fileContent += "\ncategoryname,categoryid,icon,color\n"
            for (cat in cats) {
                fileContent += "\"${cat.name}\",${cat.id},\"${cat.icon}\",\"${cat.color}\"\n"
            }
        }

        // write currencies
        val curs = db.getCurrencies(projectId)
        if (curs.isNotEmpty() && project.currencyName != null &&
            project.currencyName!!.isNotEmpty() && project.currencyName != "null"
        ) {
            fileContent += "\ncurrencyname,exchange_rate\n"
            fileContent += "\"${project.currencyName}\",1\n"
            for (cur in curs) {
                fileContent += "\"${cur.name}\",${cur.exchangeRate}\n"
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
