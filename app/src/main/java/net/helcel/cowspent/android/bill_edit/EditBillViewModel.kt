package net.helcel.cowspent.android.bill_edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.android.helper.DialogState
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

    var currencies by mutableStateOf<List<DBCurrency>>(emptyList())
    var mainCurrencyName by mutableStateOf("")
    var members by mutableStateOf<List<DBMember>>(emptyList())

    var owersSelection = mutableStateMapOf<Long, Boolean>()
    var isCustomSplit by mutableStateOf(false)
    var owersCustomSplit = mutableStateMapOf<Long, String>()

    var dialogState by mutableStateOf<DialogState?>(null)

    val amountAsDouble: Double
        get() {
            val amountStr = amount.replace(',', '.')
            return try {
                if (amountStr.matches("[0-9.]+".toRegex())) {
                    amountStr.toDouble()
                } else {
                    evalMath(amountStr)
                }
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
        val originalAmountStr = amount
        val originalAmount = amountAsDouble
        if (originalAmount == 0.0) return

        val newAmount = originalAmount / currency.exchangeRate
        amount = SupportUtil.round2(newAmount).toString()

        val currencyLabel = currency.name ?: ""
        val conversionNote = "($originalAmountStr $currencyLabel)"
        if (!comment.contains(conversionNote)) {
            if (comment.isNotEmpty() && !comment.endsWith(" ")) {
                comment += " "
            }
            comment += conversionNote
        }

        if (isCustomSplit) {
            owersCustomSplit.keys.toList().forEach { id ->
                val value = owersCustomSplit[id] ?: ""
                val partAmount = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                if (partAmount != 0.0) {
                    val newPartAmount = partAmount / currency.exchangeRate
                    owersCustomSplit[id] = SupportUtil.round2(newPartAmount).toString()
                }
            }
        }

        updateSplits()
    }

    fun initFromBill(bill: DBBill, members: List<DBMember>, customSplits: Map<Long, Double>? = null) {
        this.members = members
        what = bill.what
        amount = if (bill.amount == 0.0) "" else bill.amount.toString()
        comment = bill.comment ?: ""
        timestamp = bill.timestamp
        payerId = bill.payerId
        repeat = bill.repeat ?: DBBill.NON_REPEATED
        paymentModeRemoteId = bill.paymentModeRemoteId
        categoryRemoteId = bill.categoryRemoteId

        owersSelection.clear()
        owersCustomSplit.clear()

        if (customSplits != null) {
            isCustomSplit = true
            for (member in members) {
                val selected = customSplits.containsKey(member.id)
                owersSelection[member.id] = selected
                if (selected) {
                    owersCustomSplit[member.id] = SupportUtil.round2(customSplits[member.id]!!).toString()
                }
            }
        } else {
            val billOwerIds = bill.billOwersIds
            val selectedCount = billOwerIds.size
            val evenSplit = if (selectedCount > 0) bill.amount / selectedCount else 0.0
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

