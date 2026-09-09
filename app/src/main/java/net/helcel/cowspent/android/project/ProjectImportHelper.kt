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
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

object ProjectImportHelper {

    private fun optional(line: Array<String>, columns: Map<String, Int>, name: String): String =
        columns[name]?.takeIf { it < line.size }?.let { line[it] } ?: ""

    /** Parses the `#rrggbb` member colour Cospend writes in its members section. */
    private fun parseHexColor(value: String): Triple<Int, Int, Int>? {
        val hex = value.trim().removePrefix("#")
        if (hex.length != 6) return null
        return try {
            Triple(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16)
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

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
            val membersColor = mutableMapOf<String, Triple<Int, Int, Int>>()
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
                        columns.containsKey("name") && columns.containsKey("weight") &&
                                columns.containsKey("active") -> "members"
                        columns.containsKey("categoryid") && columns.containsKey("categoryname") -> "categories"
                        columns.containsKey("paymentmodeid") && columns.containsKey("paymentmodename") -> "paymentmodes"
                        columns.containsKey("exchange_rate") && columns.containsKey("currencyname") -> "currencies"
                        else -> {
                            onError(context.getString(R.string.import_error_header, row))
                            return
                        }
                    }
                } else {
                    previousLineEmpty = false
                    when (currentSection) {
                        "members" -> {
                            val name = line[columns["name"]!!].trim()
                            if (name.isNotEmpty()) {
                                membersWeight[name] = optional(line, columns, "weight").toDoubleOrNull() ?: 1.0
                                membersActive[name] = optional(line, columns, "active") != "0"
                                parseHexColor(optional(line, columns, "color"))?.let { membersColor[name] = it }
                            }
                        }
                        "categories" -> {
                            categories.add(DBCategory(0, line[columns["categoryid"]!!].toLong(), 0, line[columns["categoryname"]!!], optional(line, columns, "icon"), optional(line, columns, "color")))
                        }
                        "paymentmodes" -> {
                            paymentModes.add(DBPaymentMode(0, line[columns["paymentmodeid"]!!].toLong(), 0, line[columns["paymentmodename"]!!], optional(line, columns, "icon"), optional(line, columns, "color")))
                        }
                        "currencies" -> {
                            val name = line[columns["currencyname"]!!]
                            val rate = line[columns["exchange_rate"]!!].toDouble()
                            if (rate == 1.0) mainCurrencyName = name
                            currencies.add(DBCurrency(0, 0, 0, name, rate, DBBill.STATE_OK))
                        }
                        "bills" -> {
                            val what = if (columns.containsKey("what")) line[columns["what"]!!] else ""
                            // Cospend url-encodes bill comments on export and marks trashed
                            // bills with a "deleted" column no other dialect has, so that column
                            // doubles as the marker for which comment encoding to expect.
                            val cospendDialect = columns.containsKey("deleted")
                            val comment = if (columns.containsKey("comment")) {
                                val raw = line[columns["comment"]!!]
                                if (cospendDialect) {
                                    try {
                                        URLDecoder.decode(raw, "UTF-8")
                                    } catch (_: Exception) {
                                        raw
                                    }
                                } else raw
                            } else ""
                            val deleted = cospendDialect &&
                                    line[columns["deleted"]!!].trim().let { it.isNotEmpty() && it != "0" }
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
                            val payerName = if (columns.containsKey("payer_name")) line[columns["payer_name"]!!].trim() else ""
                            val payerWeight = if (columns.containsKey("payer_weight")) line[columns["payer_weight"]!!].toDouble() else 1.0
                            val owersStr = if (columns.containsKey("owers")) line[columns["owers"]!!] else ""
                            val payerActive = columns.containsKey("payer_active") && line[columns["payer_active"]!!] == "1"
                            val catId = if (columns.containsKey("categoryid") && line[columns["categoryid"]!!].isNotEmpty()) line[columns["categoryid"]!!].toLong() else 0L
                            val pmId = if (columns.containsKey("paymentmodeid") && line[columns["paymentmodeid"]!!].isNotEmpty()) line[columns["paymentmodeid"]!!].toLong() else 0L
                            val pm = if (columns.containsKey("paymentmode")) line[columns["paymentmode"]!!] else null
                            // MoneyBuster's export only carries the legacy one-letter payment
                            // mode. Everything downstream keys off paymentModeId, so translate
                            // it back the same way the sync parser does.
                            val effectivePmId =
                                if (pmId != 0L) pmId else DBBill.oldPmIdToNew[pm] ?: DBBill.PAYMODE_ID_NONE

                            if (payerName.isNotEmpty()) {
                                membersActive[payerName] = payerActive
                                membersWeight[payerName] = payerWeight
                            }
                            
                            if (owersStr.trim().isEmpty()) {
                                onError(context.getString(R.string.import_error_owers, row))
                                return
                            }
                            
                            if (what != "deleteMeIfYouWant" && !deleted) {
                                billRemoteIdToOwerStr[row.toLong()] = owersStr
                                val owersArray = owersStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                for (ower in owersArray) {
                                    if (!membersWeight.containsKey(ower)) {
                                        membersWeight[ower] = 1.0
                                    }
                                }
                                bills.add(DBBill(0, row.toLong(), 0, 0, amount, timestamp, what, DBBill.STATE_OK, "n", pm, catId, comment, effectivePmId))
                                billRemoteIdToPayerName[row.toLong()] = payerName
                            }
                        }
                    }
                }
                row++
            }
            
            val memberNameToId = mutableMapOf<String, Long>()
            val pid = db.addProject(DBProject(0, projectRemoteId, "", projectRemoteId, null, null, null, ProjectType.LOCAL, 0L, mainCurrencyName, false, DBProject.ACCESS_LEVEL_UNKNOWN, null))
            // addProject only inserts a subset of the row, currency not among it, so the main
            // currency the file declared has to be written separately or it is lost.
            if (mainCurrencyName != null) db.updateProject(pid, newCurrencyName = mainCurrencyName)

            val pmRemoteToLocal = mutableMapOf<Long, Long>()
            paymentModes.forEach {
                val newId = db.addPaymentMode(DBPaymentMode(0, it.remoteId, pid, it.name, it.icon, it.color))
                pmRemoteToLocal[it.remoteId] = newId
            }
            val catRemoteToLocal = mutableMapOf<Long, Long>()
            categories.forEach {
                val newId = db.addCategory(DBCategory(0, it.remoteId, pid, it.name, it.icon, it.color))
                catRemoteToLocal[it.remoteId] = newId
            }
            currencies.forEach { db.addCurrency(DBCurrency(0, 0, pid, it.name, it.exchangeRate, DBBill.STATE_OK)) }
            
            membersWeight.keys.forEach { mName ->
                val c = membersColor[mName]
                memberNameToId[mName] = db.addMember(DBMember(0, 0, pid, mName, membersActive[mName] ?: true, membersWeight[mName] ?: 1.0, DBBill.STATE_OK, c?.first, c?.second, c?.third, null, null))
            }
            
            bills.forEach { b ->
                val payerId = memberNameToId[billRemoteIdToPayerName[b.remoteId]] ?: 0L
                // Only custom labels are listed in the categories/paymentmodes sections. Built-in
                // ones are referenced by their (negative) constant, which the UI resolves on its
                // own, so those have to be kept rather than reset to "none". Unmapped positive
                // ids are dropped instead, as they would collide with local ids.
                val localCatId = catRemoteToLocal[b.categoryId]
                    ?: b.categoryId.takeIf { it < 0 } ?: 0L
                val localPmId = pmRemoteToLocal[b.paymentModeId]
                    ?: b.paymentModeId.takeIf { it < 0 } ?: 0L
                val billId = db.addBill(DBBill(0, 0, pid, payerId, b.amount, b.timestamp, b.what, DBBill.STATE_OK, b.repeat, b.paymentMode, localCatId, b.comment, localPmId))
                billRemoteIdToOwerStr[b.remoteId]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { ower ->
                    memberNameToId[ower]?.let { owerId -> db.addBillower(billId, owerId) }
                }
            }
            onSuccess(pid)
            
        } catch (e: Exception) {
            Log.e("Import", "Error importing", e)
            onError("Import failed: ${e.message}")
        }
    }
}
