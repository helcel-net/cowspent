package net.helcel.cowspent.android.project.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import net.helcel.cowspent.android.helper.DialogState
import net.helcel.cowspent.model.DBProject

class EditProjectViewModel : ViewModel() {
    var name by mutableStateOf("")
    var password by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var email by mutableStateOf("")
    var isLocal by mutableStateOf(false)

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

    fun initFromProject(project: DBProject) {
        name = if (project.name == "null") "" else project.name
        password = project.password
        newPassword = project.password
        email = project.email?.let { if (it == "null") "" else it } ?: ""
        isLocal = project.isLocal
    }
}
