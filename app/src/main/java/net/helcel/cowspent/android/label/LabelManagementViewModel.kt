package net.helcel.cowspent.android.label

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.android.helper.DialogState
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBPaymentMode
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper

class LabelManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val db = CowspentSQLiteOpenHelper.getInstance(application)

    var projectId by mutableLongStateOf(0L)
    var categories by mutableStateOf<List<DBCategory>>(emptyList())
    var paymentModes by mutableStateOf<List<DBPaymentMode>>(emptyList())

    var dialogState by mutableStateOf<DialogState?>(null)

    fun loadLabels(projId: Long) {
        projectId = projId
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) { db.getProject(projId) }
            if (project != null) {
                withContext(Dispatchers.IO) {
                    db.ensureDefaultLabels(projId, project.type)
                }
            }
            categories = withContext(Dispatchers.IO) { db.getCategories(projId) }
            paymentModes = withContext(Dispatchers.IO) { db.getPaymentModes(projId) }
        }
    }

    fun addCategory(name: String, icon: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.addCategoryAndSync(DBCategory(0, 0, projectId, name, icon, color, DBBill.STATE_ADDED))
            }
            loadLabels(projectId)
        }
    }

    fun updateCategory(cat: DBCategory, name: String, icon: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.updateCategoryAndSync(cat, name, icon, color)
            }
            loadLabels(projectId)
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.deleteCategoryAndSync(id)
            }
            loadLabels(projectId)
        }
    }

    fun addPaymentMode(name: String, icon: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.addPaymentModeAndSync(DBPaymentMode(0, 0, projectId, name, icon, color, DBBill.STATE_ADDED))
            }
            loadLabels(projectId)
        }
    }

    fun updatePaymentMode(pm: DBPaymentMode, name: String, icon: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.updatePaymentModeAndSync(pm, name, icon, color)
            }
            loadLabels(projectId)
        }
    }

    fun deletePaymentMode(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.deletePaymentModeAndSync(id)
            }
            loadLabels(projectId)
        }
    }
}
