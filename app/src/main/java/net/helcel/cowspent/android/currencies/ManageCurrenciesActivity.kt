package net.helcel.cowspent.android.currencies

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.showToast
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCurrency
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.ICallback


class ManageCurrenciesActivity : AppCompatActivity() {

    private val viewModel: ManageCurrenciesViewModel by viewModels()
    private var db: CowspentSQLiteOpenHelper? = null
    private var selectedProjectID: Long = -1

    private val editMainCurrencyCallBack: ICallback = object : ICallback {
        override fun onFinish() {}
        override fun onFinish(result: String, message: String) {
            if (message.isEmpty()) {
                showToast(this@ManageCurrenciesActivity,getString(R.string.currency_saved_success), Toast.LENGTH_LONG)
            } else {
                viewModel.showDialog(title=getString(R.string.error_edit_remote_project_helper, message),
                    message=getString(R.string.currency_manager),
                    positiveText = getString(android.R.string.ok))
            }
        }
        override fun onScheduled() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        intent.extras?.let {
            selectedProjectID = it.getLong(EXTRA_PROJECT_ID)
        }
        if (selectedProjectID == -1L) {
            Log.e(TAG, "Missing project id")
            finish()
            return
        }

        db = CowspentSQLiteOpenHelper.getInstance(this)
        
        lifecycleScope.launch {
            val project = withContext(Dispatchers.IO) { db!!.getProject(selectedProjectID) }
            viewModel.mainCurrencyName = project?.currencyName?.let { if (it == "null") "" else it } ?: ""
            updateCurrenciesList()

            setContent {
                ThemeUtils.CowspentTheme {
                    ManageCurrenciesScreen(
                        viewModel = viewModel,
                        onBack = { finish() },
                        onSaveMain = { saveMainCurrency() },
                        onAdd = { addOrUpdateCurrency() },
                        onDelete = { deleteCurrency(it) },
                        onEdit = { startEditing(it) },
                        onCancelEdit = { cancelEditing() }
                    )
                }
            }
        }
    }

    private fun saveMainCurrency() {
        val newMainCurrencyName = viewModel.mainCurrencyName
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db!!.updateProject(
                    selectedProjectID, null, null, null,
                    null, null, newMainCurrencyName,
                    null, null, null
                )
                val project = db!!.getProject(selectedProjectID)
                if (project != null) {
                    db!!.syncIfRemote(project)
                    if (project.type == ProjectType.COSPEND) {
                        withContext(Dispatchers.Main) {
                            if (!db!!.cowspentServerSyncHelper
                                    .editRemoteProject(selectedProjectID, project.name, null, null, newMainCurrencyName, editMainCurrencyCallBack)
                            ) {
                                showToast(this@ManageCurrenciesActivity, getString(R.string.remote_project_operation_no_network), Toast.LENGTH_LONG)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showToast(this@ManageCurrenciesActivity, getString(R.string.currency_saved_success), Toast.LENGTH_LONG)
                        }
                    }
                }
            }
        }
    }

    private fun addOrUpdateCurrency() {
        val exchangeRate = try { viewModel.newCurrencyRate.toDouble() } catch (_: Exception) { 0.0 }
        val currencyName = viewModel.newCurrencyName
        val editingId = viewModel.editingCurrencyId

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (editingId != null) {
                    db!!.updateCurrency(editingId, currencyName, exchangeRate)
                    val currency = db!!.getCurrency(editingId)
                    if (currency != null) {
                        db!!.setCurrencyStateSync(editingId, DBBill.STATE_EDITED)
                    }
                } else {
                    val newCurrency = DBCurrency(
                        0, 0, selectedProjectID,
                        currencyName, exchangeRate, DBBill.STATE_ADDED
                    )
                    db!!.addCurrencyAndSync(newCurrency)
                }
            }
            cancelEditing()
            updateCurrenciesList()
        }
    }

    private fun startEditing(currency: DBCurrency) {
        viewModel.editingCurrencyId = currency.id
        viewModel.newCurrencyName = currency.name ?: ""
        viewModel.newCurrencyRate = currency.exchangeRate.toString()
    }

    private fun cancelEditing() {
        viewModel.editingCurrencyId = null
        viewModel.newCurrencyName = ""
        viewModel.newCurrencyRate = ""
    }

    private fun deleteCurrency(currency: DBCurrency) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db!!.setCurrencyStateSync(currency.id, DBBill.STATE_DELETED)
            }
            updateCurrenciesList()
        }
    }

    private suspend fun updateCurrenciesList() {
        val currenciesDB = withContext(Dispatchers.IO) {
            val list = db!!.getCurrenciesOfProjectWithState(selectedProjectID, DBBill.STATE_ADDED).toMutableList()
            list.addAll(db!!.getCurrenciesOfProjectWithState(selectedProjectID, DBBill.STATE_EDITED))
            list.addAll(db!!.getCurrenciesOfProjectWithState(selectedProjectID, DBBill.STATE_OK))
            list
        }
        withContext(Dispatchers.Main) {
            viewModel.currencies = currenciesDB
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private val TAG = ManageCurrenciesActivity::class.java.simpleName
        const val EXTRA_PROJECT_ID = "EXTRA_PROJECT_ID"
    }
}
