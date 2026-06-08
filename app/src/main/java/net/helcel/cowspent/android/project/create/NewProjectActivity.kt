package net.helcel.cowspent.android.project.create

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.helcel.cowspent.R
import net.helcel.cowspent.model.*
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.*
import androidx.core.net.toUri
import androidx.core.content.edit
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.android.main.MainConstants
import net.helcel.cowspent.android.helper.QrCodeScannerActivity
import net.helcel.cowspent.android.project.ProjectImportHelper

class NewProjectActivity : AppCompatActivity() {

    private val viewModel: NewProjectViewModel by viewModels()
    private lateinit var db: CowspentSQLiteOpenHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        db = CowspentSQLiteOpenHelper.getInstance(this)

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            ThemeUtils.CowspentTheme {
                NewProjectScreen(
                    viewModel = viewModel,
                    onScanQrCode = {
                        val createIntent = Intent(this, QrCodeScannerActivity::class.java)
                        scanQRCodeLauncher.launch(createIntent)
                    },
                    onImportFile = {
                        val intent = Intent()
                            .setType("*/*")
                            .setAction(Intent.ACTION_GET_CONTENT)
                        importFileLauncher.launch(Intent.createChooser(intent, "Select a file"))
                    },
                    onChooseFromNextcloud = {
                        chooseFromNextcloud()
                    },
                    onOkPressed = { onPressOk() },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val defaultTypeId = intent.getStringExtra(PARAM_DEFAULT_PROJECT_TYPE)
        if (defaultTypeId != null) {
            viewModel.projectType = ProjectType.getTypeById(defaultTypeId) ?: ProjectType.LOCAL
        }

        val defaultNcUrl = intent.getStringExtra(PARAM_DEFAULT_NC_URL)
        if (defaultNcUrl != null) {
            viewModel.defaultNcUrl = defaultNcUrl
            if (viewModel.projectType != ProjectType.IHATEMONEY) {
                viewModel.projectUrl = defaultNcUrl
            }
        }

        val defaultProjectId = intent.getStringExtra(PARAM_DEFAULT_PROJECT_ID)
        if (defaultProjectId != null) {
            viewModel.projectId = defaultProjectId
        }

        val defaultPassword = intent.getStringExtra(PARAM_DEFAULT_PROJECT_PASSWORD)
        if (defaultPassword != null) {
            viewModel.projectPassword = defaultPassword
        }

        if (Intent.ACTION_VIEW == intent.action) {
            val data = intent.data
            if (data != null) {
                viewModel.updateFromUri(data)
                if (viewModel.isFormValid()) {
                    onPressOk()
                }
            }
        }
    }

    private val scanQRCodeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(MainConstants.KEY_QR_CODE)?.let { scannedUrl ->
                    viewModel.updateFromUri(scannedUrl.toUri())
                    if (viewModel.isFormValid()) {
                        onPressOk()
                    }
                }
            }
        }

    private val importFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { importFromFile(it) }
            }
        }

    private fun chooseFromNextcloud() {
        lifecycleScope.launch {
            val accountProjects = withContext(Dispatchers.IO) { db.accountProjects }
            if (accountProjects.isEmpty()) {
                showToast(getString(R.string.choose_account_project_dialog_impossible), Toast.LENGTH_LONG)
                return@launch
            }

            viewModel.nextcloudProjects = accountProjects
            viewModel.showNextcloudProjectDialog = true
        }
    }

    private fun onPressOk() {
        val type = viewModel.projectType
        val todoCreate = viewModel.whatTodoIsCreate
        val url = getFormattedUrl()

        val fakeProj = DBProject(
            0, "", "", "", url,
            "", 0L, type, 0L,
            null, false, DBProject.ACCESS_LEVEL_UNKNOWN,
            ""
        )

        if (isValidUrl(url) && todoCreate && ProjectType.COSPEND == type &&
            db.cowspentServerSyncHelper.canCreateAuthenticatedProject(fakeProj) &&
            !viewModel.showAuthWarningDialog // Avoid infinite loop
        ) {
            viewModel.showAuthWarningDialog = true
        } else {
            createProject()
        }
    }

    private fun getFormattedUrl(): String {
        var url = viewModel.projectUrl.trim()
        if (viewModel.projectType == ProjectType.COSPEND && !isCospendSchemeLink(url)) {
            url = url.replace("/+$".toRegex(), "") + "/index.php/apps/cospend"
        }
        if (viewModel.projectType == ProjectType.IHATEMONEY) {
            url = url.replace("/+$".toRegex(), "")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://") && isValidUrl("https://$url")) {
            url = "https://$url"
        }
        return url
    }

    private fun isValidUrl(url: String): Boolean = Patterns.WEB_URL.matcher(url).matches()
    
    private fun isCospendSchemeLink(url: String): Boolean {
        val data = url.toUri()
        return (("cospend" == data.scheme || "cospend+http" == data.scheme ||
                "cowspent" == data.scheme || "cowspent+http" == data.scheme ||
                "ihatemoney" == data.scheme || "ihatemoney+http" == data.scheme)
                && data.pathSegments.size >= 2)
    }

    private fun createProject() {
        val isCospendScheme = isCospendSchemeLink(getFormattedUrl())
        val rid = viewModel.projectId
        if (!isCospendScheme && (rid == "" || rid.contains(",") || rid.contains("/"))) {
            showToast(getString(R.string.error_invalid_project_remote_id), Toast.LENGTH_LONG)
            return
        }

        if (viewModel.projectType != ProjectType.LOCAL && !isCospendScheme) {
            if (!isValidUrl(getFormattedUrl())) {
                showToast("Invalid URL", Toast.LENGTH_SHORT)
                return
            }
            val passwordRequired = !viewModel.whatTodoIsCreate || viewModel.projectType == ProjectType.IHATEMONEY
            if (passwordRequired && viewModel.projectPassword.isEmpty()) {
                showToast("Invalid password", Toast.LENGTH_SHORT)
                return
            }
        }

        if (!viewModel.whatTodoIsCreate || viewModel.projectType == ProjectType.LOCAL) {
            if (viewModel.projectType == ProjectType.LOCAL) {
                val pid = saveLocalProject()
                close(pid, false)
            } else {
                saveRemoteProject(false)
            }
        } else {
            if (viewModel.projectName.isEmpty()) {
                showToast("Invalid project name", Toast.LENGTH_SHORT)
                return
            }
            if (!SupportUtil.isValidEmail(viewModel.projectEmail)) {
                showToast("Invalid email", Toast.LENGTH_SHORT)
                return
            }
            
            viewModel.isCreatingRemoteProject = true
            
            if (!db.cowspentServerSyncHelper.createRemoteProject(
                    viewModel.projectId, viewModel.projectName,
                    viewModel.projectEmail, viewModel.projectPassword, getFormattedUrl(), viewModel.projectType, createRemoteCallBack
                )
            ) {
                viewModel.isCreatingRemoteProject = false
            }
        }
    }

    private fun saveLocalProject(): Long {
        val newProject = DBProject(
            0, viewModel.projectId, "", viewModel.projectId, null,
            null, null, viewModel.projectType, 0L,
            null, false, DBProject.ACCESS_LEVEL_UNKNOWN,
            null
        )
        return addProjectToDb(newProject)
    }

    private fun saveRemoteProject(ignorePassword: Boolean) {
        val newProject = getProjectFromFields(ignorePassword)
        if (!db.cowspentServerSyncHelper.getRemoteProjectInfo(newProject, getRemoteInfoCallBack)) {
            showToast(getString(R.string.error_no_network), Toast.LENGTH_LONG)
        }
    }

    private fun getProjectFromFields(ignorePassword: Boolean): DBProject {
        var remoteId = viewModel.projectId
        var url = getFormattedUrl()
        var password = if (ignorePassword) "" else viewModel.projectPassword

        if (isCospendSchemeLink(url)) {
            val data = url.toUri()
            password = if (ignorePassword) "" else (data.lastPathSegment ?: "")
            remoteId = data.pathSegments[data.pathSegments.size - 2]
            val protocol = if (data.scheme?.endsWith("+http") == true) "http" else "https"
            var path = protocol + "://" + data.host + (data.path ?: "").replace(("/$remoteId/$password$").toRegex(), "")
            if (viewModel.projectType == ProjectType.COSPEND) {
                path = path.replace("/+$".toRegex(), "") + "/index.php/apps/cospend"
            }
            url = path
        }

        return DBProject(
            0, remoteId, password, viewModel.projectName, url,
            viewModel.projectEmail, null, viewModel.projectType, 0L,
            null, false, DBProject.ACCESS_LEVEL_UNKNOWN,
            null
        )
    }

    private fun addProjectToDb(project: DBProject): Long {
        var pid = 0L
        runBlocking {
            pid = withContext(Dispatchers.IO) { db.addProject(project) }
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putLong(
                "selected_project",
                pid
            )
        }
        showToast(getString(R.string.project_added_success), Toast.LENGTH_LONG)
        return pid
    }

    private val getRemoteInfoCallBack = object : ICallback {
        override fun onFinish() {}
        override fun onFinish(result: String, message: String) {
            if (message.isEmpty()) {
                val pid = addProjectToDb(getProjectFromFields(false))
                close(pid, true)
            } else {
                viewModel.errorDialogMessage = getString(R.string.error_project_connect_check, message)
            }
        }
        override fun onScheduled() {}
    }

    private val createRemoteCallBack = object : IProjectCreationCallback {
        override fun onFinish(result: String, message: String, usePrivateApi: Boolean) {
            if (message.isEmpty()) {
                saveRemoteProject(usePrivateApi)
            } else {
                viewModel.errorDialogMessage = getString(R.string.error_create_remote_project_helper, message)
                viewModel.isCreatingRemoteProject = false
            }
        }
    }

    private fun close(pid: Long, justAdded: Boolean) {
        val data = Intent()
        if (justAdded) {
            data.putExtra(MainConstants.ADDED_PROJECT, pid)
        } else {
            data.putExtra(MainConstants.CREATED_PROJECT, pid)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun showToast(text: CharSequence?, duration: Int) {
        Toast.makeText(this, text, duration).show()
    }

    private fun importFromFile(fileUri: Uri) {
        ProjectImportHelper.importFromFile(
            this,
            db,
            fileUri,
            onSuccess = { pid -> close(pid, false) },
            onError = { message -> showToast(message, Toast.LENGTH_LONG) }
        )
    }

    companion object {
        const val PARAM_DEFAULT_NC_URL = "defaultNcUrl"
        const val PARAM_DEFAULT_PROJECT_ID = "defaultProjectId"
        const val PARAM_DEFAULT_PROJECT_PASSWORD = "defaultProjectPassword"
        const val PARAM_DEFAULT_PROJECT_TYPE = "defaultProjectType"
    }
}
