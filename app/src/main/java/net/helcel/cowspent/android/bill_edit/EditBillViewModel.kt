package net.helcel.cowspent.android.bill_edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.android.helper.DialogState
import net.helcel.cowspent.android.helper.parseAmount
import net.helcel.cowspent.util.SupportUtil
import net.helcel.cowspent.util.evalMath

import net.helcel.cowspent.model.DBCurrency
import androidx.compose.ui.graphics.vector.ImageVector

class EditBillViewModel : ViewModel() {
    var what by mutableStateOf("")
    var amount by mutableStateOf("")
    var comment by mutableStateOf("")
    var timestamp by mutableLongStateOf(0L)
    var payerId by mutableLongStateOf(0L)
    var repeat by mutableStateOf(DBBill.NON_REPEATED)
    var paymentModeRemoteId by mutableIntStateOf(0)
    var categoryRemoteId by mutableIntStateOf(0)
    var isNewBill by mutableStateOf(false)

    var currencies by mutableStateOf<List<DBCurrency>>(emptyList())
    var mainCurrencyName by mutableStateOf("")
    var selectedCurrencyName by mutableStateOf("")
    var selectedCurrencyRate by mutableDoubleStateOf(1.0)
    var members by mutableStateOf<List<DBMember>>(emptyList())

    var owersSelection = mutableStateMapOf<Long, Boolean>()
    var isCustomSplit by mutableStateOf(false)
    var owersCustomSplit = mutableStateMapOf<Long, String>()

    var dialogState by mutableStateOf<DialogState?>(null)

    val amountAsDouble: Double
        get() {
            return parseAmount(amount) ?: try {
                evalMath(amount.replace(',', '.'))
            } catch (_: Exception) {
                0.0
            }
        }

    fun getEvenSplit(): Double {
        val selectedOwersCount = owersSelection.count { it.value }
        return if (selectedOwersCount > 0) amountAsDouble / selectedOwersCount else 0.0
    }

    fun updateSplits() {
        if (!isCustomSplit) {
            val even = getEvenSplit()
            val evenStr = if (even == 0.0) "" else SupportUtil.round2(even).toString()
            members.forEach { m ->
                if (owersSelection[m.id] == true) {
                    owersCustomSplit[m.id] = evenStr
                } else {
                    owersCustomSplit.remove(m.id)
                }
            }
        }
    }

    fun toggleMember(id: Long, selected: Boolean) {
        owersSelection[id] = selected
        if (isCustomSplit) {
            if (selected) {
                if (owersCustomSplit[id].isNullOrEmpty()) {
                    owersCustomSplit[id] = "0"
                }
            } else {
                owersCustomSplit.remove(id)
            }
        } else {
            updateSplits()
        }
    }

    fun getDiffSplit(): Double {
        val customTotal = owersCustomSplit.entries
            .filter { owersSelection[it.key] == true }
            .sumOf { it.value.replace(',', '.').toDoubleOrNull() ?: 0.0 }
        return amountAsDouble - customTotal
    }

    fun getOwersIds(): List<Long> {
        return owersSelection.filter { it.value }.keys.toList()
    }

    fun showDialog(
        title: String? = null,
        message: String? = null,
        icon: ImageVector? = null,
        items: List<CharSequence>? = null,
        positiveText: String? = null,
        negativeText: String? = null,
        neutralText: String? = null,
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
        onNeutral: (() -> Unit)? = null,
        onItemSelected: ((Int) -> Unit)? = null
    ) {
        dialogState = DialogState(
            title = title,
            message = message,
            icon = icon,
            items = items,
            positiveText = positiveText,
            negativeText = negativeText,
            neutralText = neutralText,
            onConfirm = onConfirm,
            onCancel = onCancel,
            onNeutral = onNeutral,
            onItemSelected = onItemSelected
        )
    }

    fun dismissDialog() {
        dialogState = null
    }

    fun convertCurrency(currency: DBCurrency) {
        selectedCurrencyName = currency.name ?: ""
        selectedCurrencyRate = currency.exchangeRate
        
        // We don't change 'amount' here anymore as per request.
        // It stays as the original amount.
        
        updateSplits()
    }

    fun resetCurrency() {
        selectedCurrencyName = ""
        selectedCurrencyRate = 1.0
        updateSplits()
    }

    fun getFinalAmount(): Double {
        return SupportUtil.round2(amountAsDouble / selectedCurrencyRate)
    }

    fun getFinalComment(): String {
        var baseComment = comment
        val regex = "\\s*#[^:]+:[\\d.,]+@[\\d.,]+".toRegex()
        baseComment = baseComment.replace(regex, "").trim()
        
        return if (selectedCurrencyName.isNotEmpty() && selectedCurrencyRate != 1.0) {
            val metadata = "#$selectedCurrencyName:$amount@$selectedCurrencyRate"
            if (baseComment.isEmpty()) metadata else "$baseComment $metadata"
        } else {
            baseComment
        }
    }

    fun initFromBill(bill: DBBill, members: List<DBMember>, customSplits: Map<Long, Double>? = null) {
        this.members = members
        what = bill.what
        timestamp = bill.timestamp
        payerId = bill.payerId
        repeat = bill.repeat ?: DBBill.NON_REPEATED
        paymentModeRemoteId = bill.paymentModeRemoteId
        categoryRemoteId = bill.categoryRemoteId
        
        val rawComment = bill.comment ?: ""
        
        // Try to parse existing conversion metadata #CURR:ORIG@RATE
        // Using [^:]+ for currency name to support symbols and names with spaces
        val regex = "#([^:]+):([\\d.,]+)@([\\d.,]+)".toRegex()
        val match = regex.find(rawComment)
        
        if (match != null) {
            val (currName, origAmount, rate) = match.destructured
            selectedCurrencyName = currName
            selectedCurrencyRate = rate.replace(',', '.').toDoubleOrNull() ?: 1.0
            amount = origAmount
            comment = rawComment.replace(match.value, "").trim()
            
            // Check if latest rate is different
            val latestCurrency = currencies.find { it.name == selectedCurrencyName }
            if (latestCurrency != null && latestCurrency.exchangeRate != selectedCurrencyRate) {
                showDialog(
                    title = "Update Exchange Rate?",
                    message = "The exchange rate for $selectedCurrencyName has changed from $selectedCurrencyRate to ${latestCurrency.exchangeRate}. Do you want to update the conversion for the saved total?",
                    positiveText = "Update Rate",
                    negativeText = "Keep Old",
                    onConfirm = {
                        selectedCurrencyRate = latestCurrency.exchangeRate
                        updateSplits()
                    }
                )
            }
        } else {
            selectedCurrencyName = ""
            selectedCurrencyRate = 1.0
            amount = if (bill.amount == 0.0) "" else bill.amount.toString()
            comment = rawComment
        }

        owersSelection.clear()
        owersCustomSplit.clear()

        if (customSplits != null) {
            isCustomSplit = true
            for (member in members) {
                val selected = customSplits.containsKey(member.id)
                owersSelection[member.id] = selected
                if (selected) {
                    // If we have metadata, the custom splits from DB are also converted. 
                    // We should show them as "Original" if possible? 
                    // Actually, if we use metadata, we should probably store original splits too, 
                    // but for now let's just reverse the rate for display.
                    val dbPart = customSplits[member.id]!!
                    val uiPart = if (selectedCurrencyRate != 1.0) dbPart * selectedCurrencyRate else dbPart
                    owersCustomSplit[member.id] = SupportUtil.round2(uiPart).toString()
                }
            }
        } else {
            // Even split logic
            val billOwerIds = bill.billOwersIds
            val selectedCount = billOwerIds.size
            
            // Use UI amount for even split calculation
            val uiAmount = amountAsDouble
            val evenSplit = if (selectedCount > 0) uiAmount / selectedCount else 0.0
            val evenSplitStr = if (evenSplit == 0.0) "" else SupportUtil.round2(evenSplit).toString()

            for (member in members) {
                val selected = billOwerIds.contains(member.id)
                owersSelection[member.id] = selected
                if (selected) {
                    owersCustomSplit[member.id] = evenSplitStr
                }
            }
        }
    }

    fun isFormValid(): Boolean {
        return what.isNotEmpty() && !what.contains(",") &&
                timestamp != 0L &&
                payerId != 0L &&
                owersSelection.any { it.value }
    }

    fun getValidationError(
        errorWhat: String,
        errorDate: String,
        errorPayer: String,
        errorOwers: String,
        errorInvalidForm: String
    ): String? {
        return when {
            what.isEmpty() || what.contains(",") -> errorWhat
            timestamp == 0L -> errorDate
            payerId == 0L -> errorPayer
            owersSelection.none { it.value } -> errorOwers
            !isFormValid() -> errorInvalidForm
            else -> null
        }
    }
}

