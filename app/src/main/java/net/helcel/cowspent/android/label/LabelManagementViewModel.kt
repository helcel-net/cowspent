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
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.CategoryUtils

class LabelManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val db = CowspentSQLiteOpenHelper.getInstance(application)

    var projectId by mutableLongStateOf(0L)
    var categories by mutableStateOf<List<DBCategory>>(emptyList())
    var paymentModes by mutableStateOf<List<DBPaymentMode>>(emptyList())

    var dialogState by mutableStateOf<DialogState?>(null)

    fun loadLabels(projId: Long) {
        projectId = projId
        viewModelScope.launch {
            val cats = withContext(Dispatchers.IO) { db.getCategories(projId) }
            val pms = withContext(Dispatchers.IO) { db.getPaymentModes(projId) }

            if (cats.isEmpty()) {
                val defaults = CategoryUtils.getDefaultCategories(getApplication(), projId)
                withContext(Dispatchers.IO) {
                    defaults.forEach { db.addCategory(it) }
                }
                categories = withContext(Dispatchers.IO) { db.getCategories(projId) }
            } else {
                categories = cats
            }

            if (pms.isEmpty()) {
                val defaults = CategoryUtils.getDefaultPaymentModes(getApplication(), projId)
                withContext(Dispatchers.IO) {
                    defaults.forEach { db.addPaymentMode(it) }
                }
                paymentModes = withContext(Dispatchers.IO) { db.getPaymentModes(projId) }
            } else {
                paymentModes = pms
            }
        }
    }

    fun addCategory(name: String, icon: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.addCategory(DBCategory(0, 0, projectId, name, icon, color))
            }
            loadLabels(projectId)
        }
    }

    fun updateCategory(id: Long, name: String, icon: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.updateCategory(id, name, icon, color)
            }
            loadLabels(projectId)
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.deleteCategory(id)
            }
            loadLabels(projectId)
        }
    }

    fun addPaymentMode(name: String, icon: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.addPaymentMode(DBPaymentMode(0, 0, projectId, name, icon, color))
            }
            loadLabels(projectId)
        }
    }

    fun updatePaymentMode(id: Long, name: String, icon: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.updatePaymentMode(id, name, icon, color)
            }
            loadLabels(projectId)
        }
    }

    fun deletePaymentMode(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.deletePaymentMode(id)
            }
            loadLabels(projectId)
        }
    }
}
