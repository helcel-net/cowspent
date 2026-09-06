package net.helcel.cowspent.android.currencies

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.showToast
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.ICallback


class ManageCurrenciesActivity : AppCompatActivity() {

    internal val viewModel: ManageCurrenciesViewModel by viewModels()
    private var db: CowspentSQLiteOpenHelper? = null

    private val editMainCurrencyCallBack: ICallback = object : ICallback {
        override fun onFinish() {}
        override fun onFinish(result: String, message: String) {
            if (message.isEmpty()) {
                showToast(this@ManageCurrenciesActivity,getString(R.string.currency_saved_success), Toast.LENGTH_LONG)
            } else {
                viewModel.showDialog(
                    title = getString(R.string.dialog_sync_error_title),
                    message = getString(R.string.error_edit_remote_project_helper, message),
                    positiveText = getString(android.R.string.ok)
                )
            }
        }
        override fun onScheduled() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val selectedProjectID = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
        if (selectedProjectID == -1L) {
            Log.e(TAG, "Missing project id")
            finish()
            return
        }

        viewModel.projectId = selectedProjectID
        viewModel.loadCurrencies()

        db = CowspentSQLiteOpenHelper.getInstance(this)

        setContent {
            ThemeUtils.CowspentTheme {
                ManageCurrenciesScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onSaveMain = { saveMainCurrency() },
                    onAdd = { viewModel.addCurrency() },
                    onDelete = { viewModel.deleteCurrency(it.id) },
                    onEdit = { viewModel.startEditing(it) },
                    onCancelEdit = { viewModel.cancelEditing() }
                )
            }
        }
    }

    private fun saveMainCurrency() {
        val project = db?.getProject(viewModel.projectId)
        if (project != null) {
            if (project.type == ProjectType.COSPEND) {
                if (!db!!.cowspentServerSyncHelper.isSyncPossible) {
                    showToast(this, getString(R.string.remote_project_operation_no_network), Toast.LENGTH_LONG)
                }
            } else {
                showToast(this, getString(R.string.currency_saved_success), Toast.LENGTH_LONG)
            }
        }
        viewModel.saveMainCurrency(editMainCurrencyCallBack)
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
