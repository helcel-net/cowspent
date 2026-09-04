package net.helcel.cowspent.android.currencies

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.android.helper.DialogState
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCurrency
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.ICallback

class ManageCurrenciesViewModel(application: Application) : AndroidViewModel(application) {
    var projectId: Long = -1
    var mainCurrencyName by mutableStateOf("")
    var newCurrencyName by mutableStateOf("")
    var newCurrencyRate by mutableStateOf("")

    var editingCurrencyId by mutableStateOf<Long?>(null)

    var currencies by mutableStateOf<List<DBCurrency>>(emptyList())

    var dialogState by mutableStateOf<DialogState?>(null)

    private val db = CowspentSQLiteOpenHelper.getInstance(application)

    fun loadCurrencies() {
        if (projectId == -1L) return
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) { db.getProject(projectId) }
            mainCurrencyName = project?.currencyName?.let { if (it == "null") "" else it } ?: ""
            updateCurrenciesList()
        }
    }

    suspend fun updateCurrenciesList() {
        val currenciesDB = withContext(Dispatchers.IO) {
            val list = db.getCurrenciesOfProjectWithState(projectId, DBBill.STATE_ADDED).toMutableList()
            list.addAll(db.getCurrenciesOfProjectWithState(projectId, DBBill.STATE_EDITED))
            list.addAll(db.getCurrenciesOfProjectWithState(projectId, DBBill.STATE_OK))
            list
        }
        withContext(Dispatchers.Main) {
            currencies = currenciesDB
        }
    }

    fun saveMainCurrency(callback: ICallback) {
        val newMainCurrencyName = mainCurrencyName
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.updateProject(
                    projId = projectId,
                    newCurrencyName = newMainCurrencyName
                )
                val project = db.getProject(projectId)
                if (project != null) {
                    db.syncIfRemote(project)
                    if (project.type == ProjectType.COSPEND) {
                        withContext(Dispatchers.Main) {
                            if (!db.cowspentServerSyncHelper
                                    .editRemoteProject(
                                        projId = projectId,
                                        newName = project.name,
                                        newMainCurrencyName = newMainCurrencyName,
                                        callback = callback
                                    )
                            ) {
                                // Handled by activity showing toast
                            }
                        }
                    }
                }
            }
        }
    }

    fun addCurrency() {
        val uiRate = try { newCurrencyRate.toDouble() } catch (_: Exception) { 0.0 }
        val exchangeRate = if (uiRate != 0.0) 1.0 / uiRate else 0.0
        val currencyName = newCurrencyName
        val editingId = editingCurrencyId

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (editingId != null) {
                    db.updateCurrency(editingId, currencyName, exchangeRate)
                    db.setCurrencyStateSync(editingId, DBBill.STATE_EDITED)
                } else {
                    val newCurrency = DBCurrency(
                        0, 0, projectId,
                        currencyName, exchangeRate, DBBill.STATE_ADDED
                    )
                    db.addCurrencyAndSync(newCurrency)
                }
            }
            cancelEditing()
            updateCurrenciesList()
        }
    }

    fun deleteCurrency(currencyId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.setCurrencyStateSync(currencyId, DBBill.STATE_DELETED)
            }
            updateCurrenciesList()
        }
    }

    fun startEditing(currency: DBCurrency) {
        editingCurrencyId = currency.id
        newCurrencyName = currency.name ?: ""
        val uiRate = if (currency.exchangeRate != 0.0) 1.0 / currency.exchangeRate else 0.0
        newCurrencyRate = uiRate.toString()
    }

    fun cancelEditing() {
        editingCurrencyId = null
        newCurrencyName = ""
        newCurrencyRate = ""
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

    fun isAddEnabled(): Boolean {
        return newCurrencyName.isNotEmpty() && newCurrencyRate.isNotEmpty()
    }
}
