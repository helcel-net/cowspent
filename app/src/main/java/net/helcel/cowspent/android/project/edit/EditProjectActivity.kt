package net.helcel.cowspent.android.project.edit

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.showToast
import net.helcel.cowspent.android.main.MainConstants
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.ICallback


class EditProjectActivity : AppCompatActivity() {

    internal val viewModel: EditProjectViewModel by viewModels()
    private lateinit var db: CowspentSQLiteOpenHelper
    private lateinit var project: DBProject

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        db = CowspentSQLiteOpenHelper.getInstance(this)
        
        val id = intent.getLongExtra(PARAM_PROJECT_ID, 0)
        if (id <= 0) {
            finish()
            return
        }
        
        lifecycleScope.launch {
            project = withContext(Dispatchers.IO) { db.getProject(id) }!!
            viewModel.initFromProject(project)

            setContent {
                ThemeUtils.CowspentTheme {
                    EditProjectScreen(
                        viewModel = viewModel,
                        onSave = { onSave() },
                        onDeleteRemote = { onDeleteRemote() },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun onSave() {
        when (viewModel.validate()) {
            EditProjectViewModel.ValidationError.EMPTY_NAME -> {
                showToast(this, getString(R.string.error_invalid_project_name), Toast.LENGTH_LONG)
                return
            }
            EditProjectViewModel.ValidationError.INVALID_EMAIL -> {
                showToast(this, getString(R.string.error_invalid_email), Toast.LENGTH_LONG)
                return
            }
            null -> Unit
        }

        val changes = viewModel.changesFrom(project)
        if (!changes.any) {
            showToast(this, getString(R.string.project_edition_no_change), Toast.LENGTH_LONG)
            return
        }

        val currentPwd = viewModel.password
        val newPwd = viewModel.newPassword
        val newName = viewModel.name
        val newEmail = viewModel.email

        val nameChanged = changes.name
        val emailChanged = changes.email
        val pwdChanged = changes.newPassword
        val currentPwdChanged = changes.currentPassword

        if (project.isLocal) {
            val targetPwd = if (pwdChanged) newPwd else currentPwd
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.updateProject(
                        projId = project.id,
                        newName = newName,
                        newEmail = newEmail,
                        newPassword = targetPwd,
                        projectType = project.type
                    )
                }
                closeOnEdit(project.id)
            }
            return
        }

        // Remote project
        if (nameChanged || emailChanged || pwdChanged) {
            // Update local password first if currentPwd was changed (user fixing credentials)
            if (currentPwdChanged) {
                project.password = currentPwd
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.updateProject(
                            projId = project.id,
                            newPassword = currentPwd,
                            projectType = project.type
                        )
                    }
                }
            }

            if (!db.cowspentServerSyncHelper.editRemoteProject(
                    projId = project.id,
                    newName = newName,
                    newEmail = newEmail,
                    newPassword = if (pwdChanged) newPwd else null,
                    callback = editCallBack
                )
            ) {
                showToast(this, getString(R.string.remote_project_operation_no_network), Toast.LENGTH_LONG)
            }
        } else {
            // Only current password changed locally (fixing credentials)
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.updateProject(
                        projId = project.id,
                        newPassword = currentPwd,
                        projectType = project.type
                    )
                }
                closeOnEdit(project.id)
            }
        }
    }

    private fun onDeleteRemote() {
        viewModel.showDialog(
            message = getString(R.string.title_confirm),
            positiveText = getString(R.string.simple_yes),
            onConfirm = {
                if (!db.cowspentServerSyncHelper.deleteRemoteProject(project.id, deleteCallBack)) {
                    showToast(this, getString(R.string.remote_project_operation_no_network), Toast.LENGTH_LONG)
                }
            },
            negativeText = getString(R.string.simple_no)
        )
    }

    private val editCallBack = object : ICallback {
        override fun onFinish() {}
        override fun onFinish(result: String, message: String) {
            if (message.isEmpty()) {
                closeOnEdit(project.id)
            } else {
                showToast(this@EditProjectActivity, getString(R.string.error_edit_remote_project_helper, message), Toast.LENGTH_LONG)
            }
        }
        override fun onScheduled() {}
    }

    private val deleteCallBack = object : ICallback {
        override fun onFinish() {}
        override fun onFinish(result: String, message: String) {
            if (message.isEmpty()) {
                closeOnDelete(result.toLong())
            } else {
                showToast(this@EditProjectActivity, getString(R.string.error_edit_remote_project_helper, message), Toast.LENGTH_LONG)
            }
        }
        override fun onScheduled() {}
    }

    private fun closeOnDelete(projId: Long) {
        val data = Intent()
        data.putExtra(MainConstants.DELETED_PROJECT, projId)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun closeOnEdit(projId: Long) {
        val data = Intent()
        data.putExtra(MainConstants.EDITED_PROJECT, projId)
        setResult(RESULT_OK, data)
        finish()
    }



    companion object {
        const val PARAM_PROJECT_ID = "projectId"
    }
}
