package net.helcel.cowspent.android.project.member

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
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper

class MemberManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val db = CowspentSQLiteOpenHelper.getInstance(application)

    var projectId by mutableLongStateOf(0L)
    var members by mutableStateOf<List<DBMember>>(emptyList())

    var dialogState by mutableStateOf<DialogState?>(null)

    fun loadMembers(projId: Long) {
        projectId = projId
        viewModelScope.launch {
            members = withContext(Dispatchers.IO) { db.getMembersOfProject(projId, null) }
        }
    }

    fun deleteMember(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.deleteMember(id)
            }
            loadMembers(projectId)
        }
    }
}
