package net.helcel.cowspent.android.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.model.Item
import net.helcel.cowspent.android.helper.DialogState

import androidx.compose.ui.graphics.vector.ImageVector

class BillsListViewModel : ViewModel() {
    var projects by mutableStateOf<List<DBProject>>(emptyList())
    var members by mutableStateOf<List<DBMember>>(emptyList())
    var memberBalances by mutableStateOf<Map<Long, Double>>(emptyMap())
    var selectedProjectId by mutableLongStateOf(0L)
    var selectedMemberId by mutableStateOf<Long?>(null)
    var bills by mutableStateOf<List<Item>>(emptyList())
    var isRefreshing by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var title by mutableStateOf("")
    var accountName by mutableStateOf("")
    var userAvatar by mutableStateOf<android.graphics.Bitmap?>(null)
    var lastSyncText by mutableStateOf("")
    
    var showNoProjects by mutableStateOf(false)
    var showNoMembers by mutableStateOf(false)
    var showNoBills by mutableStateOf(false)
    var hasUnlabeledBills by mutableStateOf(false)

    var dialogState by mutableStateOf<DialogState?>(null)

    var showProjectOptionsDialogByProjectId by mutableStateOf<Long?>(null)
    var showSettlementDialogByProjectId by mutableStateOf<Long?>(null)
    var showStatisticsDialogByProjectId by mutableStateOf<Long?>(null)
    var showMemberManagementDialogByProjectId by mutableStateOf<Long?>(null)
    var showAddMemberDialogByProjectId by mutableStateOf<Long?>(null)
    var showEditMemberDialogByProjectId by mutableStateOf<Long?>(null)
    var showShareDialogByProjectId by mutableStateOf<Long?>(null)

    fun showDialog(
        title: String? = null,
        message: String? = null,
        icon: ImageVector? = null,
        items: List<CharSequence>? = null,
        itemIcons: List<ImageVector>? = null,
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
            itemIcons = itemIcons,
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
}
