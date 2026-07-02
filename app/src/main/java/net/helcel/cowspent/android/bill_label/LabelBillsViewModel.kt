package net.helcel.cowspent.android.bill_label

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper

class LabelBillsViewModel : ViewModel() {
    var billsToLabel by mutableStateOf<List<DBBill>>(emptyList())
        internal set
    var currentBillIndex by mutableIntStateOf(0)
        private set
    var categories by mutableStateOf<List<DBCategory>>(emptyList())
        internal set
    var suggestedCategories by mutableStateOf<List<DBCategory>>(emptyList())
        private set

    internal var categoriesMap: Map<Long, DBCategory> = emptyMap()
    internal var allCategorizedBills: List<DBBill> = emptyList()
    var onBillProcessed: ((Long) -> Unit)? = null
    
    val currentBill: DBBill?
        get() = if (currentBillIndex < billsToLabel.size) billsToLabel[currentBillIndex] else null

    fun updateSuggestions() {
        val bill = currentBill
        if (bill == null) {
            suggestedCategories = emptyList()
            return
        }
        val name = bill.what.lowercase().trim()
        if (name.isEmpty()) {
            suggestedCategories = emptyList()
            return
        }

        val matches = allCategorizedBills.filter {
            val otherName = it.what.lowercase().trim()
            otherName == name || (name.length > 3 && otherName.contains(name)) || (otherName.length > 3 && name.contains(otherName))
        }

        val counts = matches.groupBy { it.categoryId }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(2)

        suggestedCategories = counts.mapNotNull { (catId, _) ->
            categoriesMap[catId]
        }
    }

    fun labelCurrentBill(db: CowspentSQLiteOpenHelper, categoryId: Long) {
        currentBill?.let { bill ->
            db.updateBillAndSync(
                bill = bill,
                newPayerId = bill.payerId,
                newAmount = bill.amount,
                newTimestamp = bill.timestamp,
                newWhat = bill.what,
                newOwersIds = bill.billOwersIds,
                newRepeat = bill.repeat,
                newPaymentMode = bill.paymentMode,
                newPaymentModeId = bill.paymentModeId,
                newCategoryId = categoryId,
                newComment = bill.comment
            )
            bill.categoryId = categoryId
            onBillProcessed?.invoke(bill.id)
            moveToNext()
        }
    }

    fun skipCurrentBill() {
        currentBill?.let { bill ->
            onBillProcessed?.invoke(bill.id)
        }
        moveToNext()
    }

    private fun moveToNext() {
        if (billsToLabel.isEmpty()) return

        val start = currentBillIndex
        var next = (start + 1) % billsToLabel.size
        while (next != start && billsToLabel[next].categoryId != 0L) {
            next = (next + 1) % billsToLabel.size
        }

        currentBillIndex = if (billsToLabel[next].categoryId == 0L) {
            next
        } else {
            billsToLabel.size
        }
        updateSuggestions()
    }
}
