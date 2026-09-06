package net.helcel.cowspent.android.project.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import net.helcel.cowspent.android.helper.DialogState
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.util.SupportUtil

class EditProjectViewModel : ViewModel() {
    var name by mutableStateOf("")
    var password by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var email by mutableStateOf("")
    var isLocal by mutableStateOf(false)

    var dialogState by mutableStateOf<DialogState?>(null)

    enum class ValidationError { EMPTY_NAME, INVALID_EMAIL }

    fun validate(): ValidationError? = when {
        name.isBlank() -> ValidationError.EMPTY_NAME
        email.isNotEmpty() && !SupportUtil.isValidEmail(email) -> ValidationError.INVALID_EMAIL
        else -> null
    }
    
    data class Changes(
        val name: Boolean,
        val email: Boolean,
        val newPassword: Boolean,
        val currentPassword: Boolean
    ) {
        val any: Boolean get() = name || email || newPassword || currentPassword
    }

    fun changesFrom(project: DBProject) = Changes(
        name = name != project.name,
        // initFromProject maps a null or "null" email to "", so treat those as unchanged
        email = email != project.email && !(email.isEmpty() && project.email == null),
        newPassword = newPassword != project.password,
        currentPassword = password != project.password
    )

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
