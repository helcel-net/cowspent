package net.helcel.cowspent.android.currencies

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import net.helcel.cowspent.android.helper.DialogState
import net.helcel.cowspent.model.DBCurrency

class ManageCurrenciesViewModel : ViewModel() {
    var mainCurrencyName by mutableStateOf("")
    var newCurrencyName by mutableStateOf("")
    var newCurrencyRate by mutableStateOf("")

    var currencies by mutableStateOf<List<DBCurrency>>(emptyList())

    var dialogState by mutableStateOf<DialogState?>(null)

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

    fun isAddEnabled(): Boolean {
        return newCurrencyName.isNotEmpty() && newCurrencyRate.isNotEmpty()
    }
}
