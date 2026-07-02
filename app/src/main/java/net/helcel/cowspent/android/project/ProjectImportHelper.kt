package net.helcel.cowspent.android.project

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.opencsv.CSVReader
import net.helcel.cowspent.R
import net.helcel.cowspent.model.*
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object ProjectImportHelper {

    @SuppressLint("Range")
    fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { p ->
                val cut = p.lastIndexOf('/')
                if (cut != -1) p.substring(cut + 1) else p
            }
        }
        return result ?: "project.csv"
    }

    fun importFromFile(
        context: Context,
        db: CowspentSQLiteOpenHelper,
        fileUri: Uri,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        val contentResolver = context.contentResolver
        try {
            val projectRemoteId = getFileName(contentResolver, fileUri).replace("\\.csv$".toRegex(), "")
            val inputStream = contentResolver.openInputStream(fileUri) ?: return
            val reader = CSVReader(InputStreamReader(inputStream))
            
            var previousLineEmpty = false
            var currentSection: String? = null
            var row = 0
            var mainCurrencyName: String? = null
            val columns = mutableMapOf<String, Int>()
            val paymentModes = mutableListOf<DBPaymentMode>()
            val categories = mutableListOf<DBCategory>()
            val currencies = mutableListOf<DBCurrency>()
            val bills = mutableListOf<DBBill>()
            val membersActive = mutableMapOf<String, Boolean>()
            val membersWeight = mutableMapOf<String, Double>()
            val billRemoteIdToPayerName = mutableMapOf<Long, String>()
            val billRemoteIdToOwerStr = mutableMapOf<Long, String>()
            
            var nextLine: Array<String>?
            while (reader.readNext().also { nextLine = it } != null) {
                val line = nextLine!!
                val allFieldsEmpty = line.all { it.isEmpty() }
                
                if (allFieldsEmpty) {
                    previousLineEmpty = true
                } else if (row == 0 || previousLineEmpty) {
                    previousLineEmpty = false
                    columns.clear()
                    line.forEachIndexed { index, s -> columns[s] = index }
                    
                    currentSection = when {
                        columns.containsKey("what") && columns.containsKey("amount") -> "bills"
                        columns.containsKey("categoryid") && columns.containsKey("categoryname") -> "categories"
                        columns.containsKey("exchange_rate") && columns.containsKey("currencyname") -> "currencies"
                        else -> {
                            onError(context.getString(R.string.import_error_header, row))
                            return
                        }
                    }
                } else {
                    previousLineEmpty = false
                    when (currentSection) {
                        "categories" -> {
                            categories.add(DBCategory(0, line[columns["categoryid"]!!].toLong(), 0, line[columns["categoryname"]!!], line[columns["icon"]!!], line[columns["color"]!!]))
                        }
                        "paymentmodes" -> {
                            paymentModes.add(DBPaymentMode(0, line[columns["categoryid"]!!].toLong(), 0, line[columns["categoryname"]!!], line[columns["icon"]!!], line[columns["color"]!!]))
                        }
                        "currencies" -> {
                            val name = line[columns["currencyname"]!!]
                            val rate = line[columns["exchange_rate"]!!].toDouble()
                            if (rate == 1.0) mainCurrencyName = name
                            currencies.add(DBCurrency(0, 0, 0, name, rate, DBBill.STATE_OK))
                        }
                        "bills" -> {
                            val what = if (columns.containsKey("what")) line[columns["what"]!!] else ""
                            val comment = if (columns.containsKey("comment")) line[columns["comment"]!!] else ""
                            val amount = if (columns.containsKey("amount")) line[columns["amount"]!!].toDouble() else 0.0
                            val timestamp: Long = when {
                                columns.containsKey("timestamp") -> line[columns["timestamp"]!!].toLong()
                                columns.containsKey("date") -> {
                                    try {
                                        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(line[columns["date"]!!])!!.time / 1000
                                    } catch (_: Exception) {
                                        onError(context.getString(R.string.import_error_date, row))
                                        return
                                    }
                                }
                                else -> 0
                            }
                            val payerName = if (columns.containsKey("payer_name")) line[columns["payer_name"]!!] else ""
                            val payerWeight = if (columns.containsKey("payer_weight")) line[columns["payer_weight"]!!].toDouble() else 1.0
                            val owersStr = if (columns.containsKey("owers")) line[columns["owers"]!!] else ""
                            val payerActive = columns.containsKey("payer_active") && line[columns["payer_active"]!!] == "1"
                            val catId = if (columns.containsKey("categoryid") && line[columns["categoryid"]!!].isNotEmpty()) line[columns["categoryid"]!!].toLong() else 0L
                            val pmId = if (columns.containsKey("paymentmodeid") && line[columns["paymentmodeid"]!!].isNotEmpty()) line[columns["paymentmodeid"]!!].toLong() else 0L
                            val pm = if (columns.containsKey("paymentmode")) line[columns["paymentmode"]!!] else null
                            
                            membersActive[payerName] = payerActive
                            membersWeight[payerName] = payerWeight
                            
                            if (owersStr.trim().isEmpty()) {
                                onError(context.getString(R.string.import_error_owers, row))
                                return
                            }
                            
                            if (what != "deleteMeIfYouWant") {
                                billRemoteIdToOwerStr[row.toLong()] = owersStr
                                val owersArray = owersStr.split(", ").filter { it.isNotEmpty() }
                                for (ower in owersArray) {
                                    if (!membersWeight.containsKey(ower.trim())) {
                                        membersWeight[ower.trim()] = 1.0
                                    }
                                }
                                bills.add(DBBill(0, row.toLong(), 0, 0, amount, timestamp, what, DBBill.STATE_OK, "n", pm, catId, comment, pmId))
                                billRemoteIdToPayerName[row.toLong()] = payerName
                            }
                        }
                    }
                }
                row++
            }
            
            val memberNameToId = mutableMapOf<String, Long>()
            val pid = db.addProject(DBProject(0, projectRemoteId, "", projectRemoteId, null, null, null, ProjectType.LOCAL, 0L, mainCurrencyName, false, DBProject.ACCESS_LEVEL_UNKNOWN, null))
            
            val pmRemoteToLocal = mutableMapOf<Long, Long>()
            paymentModes.forEach { pm ->
                val localId = db.addPaymentMode(DBPaymentMode(0, pm.remoteId, pid, pm.name, pm.icon, pm.color))
                pmRemoteToLocal[pm.remoteId] = localId
            }
            val catRemoteToLocal = mutableMapOf<Long, Long>()
            categories.forEach { cat ->
                val localId = db.addCategory(DBCategory(0, cat.remoteId, pid, cat.name, cat.icon, cat.color))
                catRemoteToLocal[cat.remoteId] = localId
            }
            currencies.forEach { db.addCurrency(DBCurrency(0, 0, pid, it.name, it.exchangeRate, DBBill.STATE_OK)) }
            
            membersWeight.keys.forEach { mName ->
                memberNameToId[mName] = db.addMember(DBMember(0, 0, pid, mName, membersActive[mName] ?: true, membersWeight[mName] ?: 1.0, DBBill.STATE_OK, null, null, null, null, null))
            }
            
            bills.forEach { b ->
                val payerId = memberNameToId[billRemoteIdToPayerName[b.remoteId]] ?: 0L
                val localCatId = catRemoteToLocal[b.categoryId] ?: b.categoryId
                val localPmId = pmRemoteToLocal[b.paymentModeId] ?: b.paymentModeId
                val billId = db.addBill(DBBill(0, 0, pid, payerId, b.amount, b.timestamp, b.what, DBBill.STATE_OK, b.repeat, b.paymentMode, localCatId, b.comment, localPmId))
                billRemoteIdToOwerStr[b.remoteId]?.split(", ")?.filter { it.isNotEmpty() }?.forEach { ower ->
                    memberNameToId[ower.trim()]?.let { owerId -> db.addBillower(billId, owerId) }
                }
            }
            onSuccess(pid)
            
        } catch (e: Exception) {
            Log.e("Import", "Error importing", e)
            onError("Import failed: ${e.message}")
        }
    }
}
