package net.helcel.cowspent.android.bill_label

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.CategoryUtils
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.ProjectType

class LabelBillsActivity : AppCompatActivity() {
    private val viewModel: LabelBillsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
        if (projectId == -1L) {
            finish()
            return
        }

        val db = CowspentSQLiteOpenHelper.getInstance(this)
        
        lifecycleScope.launch {
            val (members, billsToLabel, categories, allCategorized) = withContext(Dispatchers.IO) {
                val project = db.getProject(projectId)
                val projectType = project?.type ?: ProjectType.LOCAL

                val members = db.getMembersOfProject(projectId, null)
                val allBills = db.getBillsOfProject(projectId)
                val billsToLabel = allBills.filter { it.categoryRemoteId == 0 && it.state != DBBill.STATE_DELETED }
                val allCategorized = allBills.filter { it.categoryRemoteId != 0 && it.state != DBBill.STATE_DELETED }
                
                val syncedCategories = db.getCategories(projectId)
                val defaultCategories = CategoryUtils.getDefaultCategories(this@LabelBillsActivity, projectId)
                val hardcoded = if (projectType == ProjectType.LOCAL) {
                    defaultCategories
                } else {
                    listOfNotNull(defaultCategories.find { it.remoteId.toInt() == DBBill.CATEGORY_REIMBURSEMENT })
                }
                val categories = syncedCategories + hardcoded
                
                Quadruple(members, billsToLabel, categories, allCategorized)
            }

            viewModel.billsToLabel = billsToLabel
            viewModel.categories = categories
            viewModel.categoriesMap = categories.associateBy { it.remoteId }
            viewModel.allCategorizedBills = allCategorized
            viewModel.updateSuggestions()

            setContent {
                ThemeUtils.CowspentTheme {
                    LabelBillsScreen(
                        viewModel = viewModel,
                        members = members,
                        db = db,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        const val EXTRA_PROJECT_ID = "extra_project_id"

        fun createIntent(context: Context, projectId: Long): Intent {
            return Intent(context, LabelBillsActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
        }
    }
}
