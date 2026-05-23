package net.helcel.cowspent.android.main

import android.Manifest
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.helcel.cowspent.R
import net.helcel.cowspent.android.account.AccountActivity
import net.helcel.cowspent.android.bill_edit.EditBillActivity
import net.helcel.cowspent.android.bill_label.LabelBillsActivity
import net.helcel.cowspent.android.currencies.ManageCurrenciesActivity
import net.helcel.cowspent.android.helper.showToast
import net.helcel.cowspent.android.project.create.NewProjectActivity
import net.helcel.cowspent.android.project.edit.EditProjectActivity
import net.helcel.cowspent.android.settings.PreferencesActivity
import net.helcel.cowspent.model.Category
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.GroupedBill
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.persistence.CowspentServerSyncHelper
import net.helcel.cowspent.theme.ThemeUtils
import net.helcel.cowspent.util.BillFormatter
import net.helcel.cowspent.util.CospendClientUtil
import net.helcel.cowspent.util.ExportUtil
import net.helcel.cowspent.util.ICallback
import net.helcel.cowspent.util.IRefreshBillsListCallback
import net.helcel.cowspent.util.SupportUtil
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BillsListViewActivity :
    AppCompatActivity(),
    IRefreshBillsListCallback {

    private val viewModel: BillsListViewModel by viewModels()

    companion object {
        var DEBUG = false

        private val TAG = BillsListViewActivity::class.java.simpleName

        private const val SAVED_STATE_NAVIGATION_SELECTION = "navigationSelection"
        private const val SAVED_STATE_NAVIGATION_OPEN = "navigationOpen"

        private var contentToExport = ""
        var isActivityVisible = false
            private set
    }

    private var navigationSelection = Category(null, null)
    private var navigationOpen: String? = ""

    private var mActionMode: ActionMode? = null
    private lateinit var db: CowspentSQLiteOpenHelper
    
    private val syncCallBack = object : ICallback {
        override fun onFinish() {
            mActionMode?.finish()
            refreshLists()
            viewModel.isRefreshing = false
        }

        override fun onFinish(result: String, message: String) {}

        override fun onScheduled() {
            viewModel.isRefreshing = false
        }
    }

    private val addProjectLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            var pid = data.getLongExtra(MainConstants.CREATED_PROJECT, 0)
            var created = true
            if (pid == 0L) {
                created = false
                pid = data.getLongExtra(MainConstants.ADDED_PROJECT, 0)
            }
            if (DEBUG) Log.d(TAG, "BILLS request code : addproject $pid")
            if (pid != 0L) {
                viewModel.selectedMemberId = null
                setSelectedProject(pid)
                Log.d(TAG, "CREATED project id: $pid")
                lifecycleScope.launch {
                    val addedProj = withContext(Dispatchers.IO) { db.getProject(pid) }
                    val message: String
                    val title: String
                    if (created) {
                        Log.e(TAG, "CREATED !!!")
                        title = getString(R.string.project_create_success_title)
                        message = getString(R.string.project_create_success_message, addedProj?.remoteId)
                    } else {
                        Log.e(TAG, "ADDED !!!")
                        title = getString(R.string.project_add_success_title)
                        message = getString(R.string.project_add_success_message, addedProj?.remoteId)
                    }
                    showDialog(message, title, Icons.Default.AddCircleOutline)
                }
            }
        }
        setupDrawerProjects()
    }

    private val serverSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            updateUsernameInDrawer()
            db = CowspentSQLiteOpenHelper.getInstance(this)
            if (CowspentServerSyncHelper.isNextcloudAccountConfigured(applicationContext)) {
                db.cowspentServerSyncHelper.runAccountProjectsSync()
            }
            if (!db.cowspentServerSyncHelper.isSyncPossible) {
                if (CowspentServerSyncHelper.isNextcloudAccountConfigured(applicationContext)) {
                    Toast.makeText(applicationContext, getString(R.string.error_sync, getString(CospendClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val createBillLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    }

    private val editBillLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val billId = data.getLongExtra(MainConstants.BILL_TO_DUPLICATE, 0)
            if (billId != 0L) {
                duplicateBill(billId)
            }
        }
    }

    private val editProjectLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            var pid = data.getLongExtra(MainConstants.DELETED_PROJECT, 0)
            if (pid != 0L) {
                setSelectedProject(0)
            }
            pid = data.getLongExtra(MainConstants.EDITED_PROJECT, 0)
            if (pid != 0L) {
                viewModel.selectedMemberId = null
                setSelectedProject(pid)
            }
        }
        setupDrawerProjects()
    }

    private val labelBillsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshLists()
    }

    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val fileUri = data.data
            fileUri?.let { saveToFileUri(contentToExport, it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActivityVisible = true
        if (savedInstanceState != null) {
            navigationSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable(SAVED_STATE_NAVIGATION_SELECTION, Category::class.java)!!
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getSerializable(SAVED_STATE_NAVIGATION_SELECTION) as Category
            }
            navigationOpen = savedInstanceState.getString(SAVED_STATE_NAVIGATION_OPEN)
        }

        db = CowspentSQLiteOpenHelper.getInstance(this)

        setupDrawerProjects()

        updateUsernameInDrawer()

        setContent {
            ThemeUtils.CowspentTheme {
                BillsListScreen(
                    viewModel = viewModel,
                    db = db,
                    refreshCallback = this,
                    onAddBillClick = {
                        val selectedProjectId = PreferenceManager.getDefaultSharedPreferences(applicationContext).getLong("selected_project", 0)
                        if (selectedProjectId != 0L) {
                            lifecycleScope.launch {
                                val members = withContext(Dispatchers.IO) { db.getActivatedMembersOfProject(selectedProjectId) }
                                if (members.isEmpty()) {
                                    showToast(this@BillsListViewActivity, getString(R.string.add_bill_impossible_no_member))
                                } else {
                                    val proj = withContext(Dispatchers.IO) { db.getProject(selectedProjectId) }
                                    val createIntent = Intent(applicationContext, EditBillActivity::class.java).apply {
                                        putExtra(EditBillActivity.PARAM_PROJECT_ID, selectedProjectId)
                                        putExtra(EditBillActivity.PARAM_PROJECT_TYPE, proj?.type?.id)
                                    }
                                    createBillLauncher.launch(createIntent)
                                }
                            }
                        }
                    },
                    onBillClick = { bill: DBBill ->
                        lifecycleScope.launch {
                            val pid = PreferenceManager.getDefaultSharedPreferences(applicationContext).getLong("selected_project", 0)
                            val proj = withContext(Dispatchers.IO) { db.getProject(pid) }
                            val intent = Intent(applicationContext, EditBillActivity::class.java).apply {
                                if (bill is GroupedBill) {
                                    val ids = bill.sourceBills.map { it.id }.toLongArray()
                                    putExtra(EditBillActivity.PARAM_GROUPED_BILL_IDS, ids)
                                } else {
                                    putExtra(EditBillActivity.PARAM_BILL_ID, bill.id)
                                }
                                putExtra(EditBillActivity.PARAM_PROJECT_TYPE, proj?.type?.id)
                                putExtra(EditBillActivity.PARAM_PROJECT_ID, pid)
                            }
                            editBillLauncher.launch(intent)
                        }
                    },
                    onProjectClick = { pid: Long -> onProjectClick(pid) },
                    onProjectOptionsClick = { pid: Long -> onManageProjectClick(pid) },
                    onProjectAction = { pid, actionIndex ->
                        when (actionIndex) {
                            0 -> onEditProjectClick(pid)
                            1 -> onRemoveProjectClick(pid)
                            2 -> onManageMembersClick(pid)
                            3 -> onManageCurrenciesClick(pid)
                            4 -> onProjectStatisticsClick(pid)
                            5 -> onSettleProjectClick(pid)
                            6 -> onShareProjectClick(pid)
                            7 -> onExportProjectClick(pid)
                        }
                    },
                    onAccountSwitcherClick = {
                        serverSettingsLauncher.launch(Intent(this, AccountActivity::class.java))
                    },
                    onAddProjectClick = { addProject() },
                    onAppSettingsClick = {
                        serverSettingsLauncher.launch(Intent(this, PreferencesActivity::class.java))
                    },
                    onLabelBillsClick = {
                        val selectedProjectId = PreferenceManager.getDefaultSharedPreferences(applicationContext).getLong("selected_project", 0)
                        if (selectedProjectId != 0L) {
                            labelBillsLauncher.launch(LabelBillsActivity.createIntent(this, selectedProjectId))
                        }
                    },
                    onRefresh = { synchronize(true) }
                )
            }
        }

        lifecycleScope.launch {
            val empty = withContext(Dispatchers.IO) { db.projects.isEmpty() }
            if (empty && !CowspentServerSyncHelper.isNextcloudAccountConfigured(this@BillsListViewActivity)) {
                viewModel.showNoProjects = true
            }

            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val selectedProjectId = preferences.getLong("selected_project", 0)
            if (selectedProjectId == 0L) {
                val dbProjects = withContext(Dispatchers.IO) { db.projects }
                if (dbProjects.isNotEmpty()) {
                    setSelectedProject(dbProjects[0].id)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        val projectToSelect = intent.getLongExtra(MainConstants.PARAM_PROJECT_TO_SELECT, 0)
        if (projectToSelect != 0L) {
            setSelectedProject(projectToSelect)
            lifecycleScope.launch {
                val project = withContext(Dispatchers.IO) { db.getProject(projectToSelect) }
                val dialogContent = intent.getStringExtra(MainConstants.PARAM_DIALOG_CONTENT)
                if (dialogContent != null && project != null) {
                    viewModel.showDialog(
                        title = getString(R.string.activity_dialog_title, project.name),
                        message = dialogContent,
                        positiveText = getString(android.R.string.ok),
                        icon = Icons.Default.Sync
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val selectedProjectId = preferences.getLong("selected_project", 0)
        if (selectedProjectId != 0L) {
            refreshLists()
        }
        viewModel.isRefreshing = false

        if (db.cowspentServerSyncHelper.isSyncPossible) {
            db.cowspentServerSyncHelper.addCallbackPull(syncCallBack)
            synchronize()
        }

        registerBroadcastReceiver()
        updateAvatarInDrawer(CowspentServerSyncHelper.isNextcloudAccountConfigured(this))
        isActivityVisible = true
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(mBroadcastReceiver)
        } catch (_: RuntimeException) {
            if (DEBUG) Log.d(TAG, "RECEIVER PROBLEM, let's ignore it...")
        }
        isActivityVisible = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SAVED_STATE_NAVIGATION_SELECTION, navigationSelection)
        outState.putString(SAVED_STATE_NAVIGATION_OPEN, navigationOpen)
    }

    private fun setupDrawerProjects() {
        val selectedProjectId = PreferenceManager.getDefaultSharedPreferences(applicationContext).getLong("selected_project", 0)
        lifecycleScope.launch {
            val projects = withContext(Dispatchers.IO) { db.projects }
            viewModel.projects = projects
            setSelectedProject(selectedProjectId)
        }
    }

    fun onProjectClick(projectId: Long) {
        if (viewModel.selectedProjectId != projectId) {
            viewModel.selectedMemberId = null
        }
        setSelectedProject(projectId)
        navigationSelection = Category(null, null)
        refreshLists(true)

        synchronize()
    }

    fun onManageProjectClick(projectId: Long) {
        viewModel.showProjectOptionsDialogByProjectId = projectId
    }

    private fun onEditProjectClick(projectId: Long) {
        if (projectId == 0L) return
        lifecycleScope.launch {
            val proj = withContext(Dispatchers.IO) { db.getProject(projectId) }
            if (proj?.isLocal == false) {
                val intent = Intent(applicationContext, EditProjectActivity::class.java).apply {
                    putExtra(EditProjectActivity.PARAM_PROJECT_ID, projectId)
                }
                editProjectLauncher.launch(intent)
            } else {
                showToast(this@BillsListViewActivity, getString(R.string.edit_project_local_impossible))
            }
        }
    }

    private fun onRemoveProjectClick(projectId: Long) {
        if (projectId == 0L) return
        lifecycleScope.launch {
            val proj = withContext(Dispatchers.IO) { db.getProject(projectId) } ?: return@launch
            
            viewModel.showDialog(
                title = getString(R.string.confirm_remove_project_dialog_title),
                message = if (!proj.isLocal) getString(R.string.confirm_remove_project_dialog_message) else null,
                positiveText = getString(R.string.simple_yes),
                onConfirm = {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            db.deleteProject(projectId)
                            val dbProjects = db.projects
                            if (dbProjects.isNotEmpty()) setSelectedProject(dbProjects[0].id) else setSelectedProject(0)
                        }
                        setupDrawerProjects()
                        refreshLists()
                        synchronize()
                        val projectNameString = proj.name.ifEmpty { proj.remoteId }
                        showToast(this@BillsListViewActivity, getString(R.string.remove_project_confirmation, projectNameString))
                    }
                },
                negativeText = getString(R.string.simple_no)
            )
        }
    }

    fun onManageMembersClick(projectId: Long) {
        if (projectId == 0L) return
        lifecycleScope.launch {
            val project = withContext(Dispatchers.IO) { db.getProject(projectId) } ?: return@launch
            if (project.myAccessLevel != DBProject.ACCESS_LEVEL_UNKNOWN && project.myAccessLevel < DBProject.ACCESS_LEVEL_MAINTAINER) {
                showToast(this@BillsListViewActivity, getString(R.string.insufficient_access_level))
                return@launch
            }

            viewModel.showMemberManagementDialogByProjectId = projectId
        }
    }

    fun onManageCurrenciesClick(projectId: Long) {
        lifecycleScope.launch {
            val proj = withContext(Dispatchers.IO) { db.getProject(projectId) }
            if (proj != null && proj.type == ProjectType.COSPEND) {
                startActivity(Intent(applicationContext, ManageCurrenciesActivity::class.java).apply {
                    putExtra(ManageCurrenciesActivity.EXTRA_PROJECT_ID, projectId)
                })
            } else showToast(this@BillsListViewActivity, getString(R.string.currency_management_unavailable))
        }
    }

    fun onProjectStatisticsClick(projectId: Long) {
        viewModel.showStatisticsDialogByProjectId = projectId
    }

    fun onSettleProjectClick(projectId: Long) {
        viewModel.showSettlementDialogByProjectId = projectId
    }

    fun onShareProjectClick(projectId: Long) {
        lifecycleScope.launch {
            val proj = withContext(Dispatchers.IO) { db.getProject(projectId) }
            if (projectId != 0L && proj?.isShareable() == true) viewModel.showShareDialogByProjectId = projectId
            else showToast(this@BillsListViewActivity, getString(R.string.share_impossible), Toast.LENGTH_LONG)
        }
    }

    fun onExportProjectClick(projectId: Long) {
        if (projectId == 0L) return
        lifecycleScope.launch {
            contentToExport = withContext(Dispatchers.IO) { ExportUtil.createExportContent(db, projectId) }
            val fileName = withContext(Dispatchers.IO) { ExportUtil.createExportFileName(db, projectId) }
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            saveFileLauncher.launch(intent)
        }
    }

    private fun saveToFileUri(content: String, fileUri: Uri) {
        try {
            contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                outputStream.writer().use { it.write(content) }
            }
            showToast(this,getString(R.string.file_saved_success, fileUri.lastPathSegment?.replace(Environment.getExternalStorageDirectory().toString(), "")))
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
            showToast(this,e.toString())
        }
    }

    private fun addProject() {
        lifecycleScope.launch {
            var defaultNcUrl = if (CowspentServerSyncHelper.isNextcloudAccountConfigured(this@BillsListViewActivity)) {
                CowspentServerSyncHelper.getNextcloudAccountServerUrl(this@BillsListViewActivity)
            } else "https://mynextcloud.org"

            val dbProjects = withContext(Dispatchers.IO) { db.projects }
            for (project in dbProjects) {
                val url = project.serverUrl
                if (!url.isNullOrEmpty() && url.contains("/index.php/apps/cospend")) {
                    defaultNcUrl = url.replace("/index.php/apps/cospend", "")
                    break
                }
            }
            val intent = Intent(applicationContext, NewProjectActivity::class.java).apply {
                putExtra(NewProjectActivity.PARAM_DEFAULT_NC_URL, defaultNcUrl)
            }
            addProjectLauncher.launch(intent)
        }
    }

    private fun setSelectedProject(projectId: Long) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        preferences.edit { putLong("selected_project", projectId) }

        lifecycleScope.launch {
            val (proj, members, memberBalances) = withContext(Dispatchers.IO) {
                var proj = db.getProject(projectId)
                if (proj == null) {
                    val dbProjects = db.projects
                    if (dbProjects.isNotEmpty()) {
                        proj = dbProjects[0]
                        preferences.edit { putLong("selected_project", proj.id) }
                    } else {
                        return@withContext Triple(null, emptyList(), emptyMap<Long, Double>())
                    }
                }

                val members = db.getMembersOfProject(proj.id, null)
                val bills = db.getBillsOfProject(proj.id)
                val balances = HashMap<Long, Double>()
                SupportUtil.getStats(
                    members, bills,
                    mutableMapOf(), balances, mutableMapOf(), mutableMapOf(),
                    -1000, -1000, null, null
                )
                Triple(proj, members, balances)
            }

            if (proj == null) {
                viewModel.selectedProjectId = 0L
                return@launch
            }

            if (viewModel.selectedProjectId != proj.id) {
                viewModel.selectedMemberId = null
            }
            viewModel.selectedProjectId = proj.id
            viewModel.members = members
            viewModel.memberBalances = memberBalances

            updateLastSyncText(proj)
        }
    }

    private fun updateLastSyncText(proj: DBProject?) {
        if (proj == null || proj.isLocal) {
            viewModel.lastSyncText = ""
        } else {
            val lastSyncTimestamp = proj.lastSyncedTimestamp ?: 0
            val cal = Calendar.getInstance().apply { timeInMillis = lastSyncTimestamp * 1000 }
            val text = getString(R.string.drawer_last_sync_text, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            viewModel.lastSyncText = text
        }
    }

    fun refreshLists() = refreshLists(false)

    override fun refreshLists(scrollToTop: Boolean) {
        val selectedProjectId = PreferenceManager.getDefaultSharedPreferences(applicationContext).getLong("selected_project", 0)
        
        lifecycleScope.launch {
            val (projId, projName) = withContext(Dispatchers.IO) {
                if (selectedProjectId != 0L) {
                    db.getProject(selectedProjectId)?.let {
                        it.id to (if (it.name == "null" || it.name.isEmpty()) it.remoteId else it.name)
                    } ?: (0L to "")
                } else {
                    0L to ""
                }
            }
            
            val title = if (selectedProjectId != 0L) projName else getString(R.string.app_name)
            
            setSelectedProject(selectedProjectId)
            viewModel.title = title
            val query = viewModel.searchQuery.ifEmpty { null }

            val (ljItems, memberCount) = withContext(Dispatchers.IO) {
                val db = CowspentSQLiteOpenHelper.getInstance(applicationContext)
                val billList: List<DBBill> = if (projId != 0L) {
                    db.searchBills(query, projId)
                } else {
                    ArrayList()
                }

                val bills = billList.filter {
                    val mid = viewModel.selectedMemberId
                    mid == null || mid == it.payerId || it.billOwersIds.contains(mid)
                }

                viewModel.hasUnlabeledBills = bills.any { it.categoryRemoteId == 0 && it.state != DBBill.STATE_DELETED }

                val projectMembers = db.getMembersOfProject(projId, null)
                val memberMap = projectMembers.associateBy { it.id }

                val projectPaymentModes = db.getPaymentModes(projId).associateBy { it.remoteId }
                val projectCategories = db.getCategories(projId).associateBy { it.remoteId }

                BillFormatter.formatBills(
                    bills,
                    memberMap,
                    projectCategories,
                    projectPaymentModes
                )

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                val itemList = BillsListUtils.groupAndSectionBills(
                    bills,
                    memberMap,
                    sdf,
                    applicationContext
                )

                itemList to projectMembers.size
            }

            viewModel.showNoProjects = false
            viewModel.showNoMembers = false
            viewModel.showNoBills = false
            
            when {
                memberCount == 0 -> {
                    viewModel.showNoMembers = true
                }
                ljItems.isEmpty() -> {
                    viewModel.showNoBills = true
                    viewModel.bills = emptyList()
                }
                else -> {
                    viewModel.bills = ljItems
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            viewModel.searchQuery = intent.getStringExtra(SearchManager.QUERY) ?: ""
        }
        super.onNewIntent(intent)
    }

    private fun duplicateBill(billId: Long) {
        val selectedProjectId = PreferenceManager.getDefaultSharedPreferences(applicationContext).getLong("selected_project", 0)
        if (selectedProjectId != 0L) {
            lifecycleScope.launch {
                val members = withContext(Dispatchers.IO) { db.getActivatedMembersOfProject(selectedProjectId) }
                if (members.isEmpty()) {
                    showToast(this@BillsListViewActivity, getString(R.string.add_bill_impossible_no_member))
                } else {
                    val projType = withContext(Dispatchers.IO) { db.getProject(selectedProjectId)?.type?.id }
                    val intent = Intent(applicationContext, EditBillActivity::class.java).apply {
                        putExtra(EditBillActivity.PARAM_PROJECT_ID, selectedProjectId)
                        putExtra(EditBillActivity.PARAM_PROJECT_TYPE, projType)
                        putExtra(EditBillActivity.PARAM_BILL_ID_TO_DUPLICATE, billId)
                    }
                    createBillLauncher.launch(intent)
                }
            }
        }
    }

    private fun showDialog(msg: String, title: String, icon: ImageVector) {
        viewModel.showDialog(
            title = title,
            message = msg,
            positiveText = getString(android.R.string.ok),
            icon = icon
        )
    }

    private fun updateUsernameInDrawer() {
        if (!CowspentServerSyncHelper.isNextcloudAccountConfigured(this)) {
            val text = getString(R.string.drawer_no_account)
            viewModel.accountName = text
            updateAvatarInDrawer(false)
        } else {
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)
            val (user, server) = if (preferences.getBoolean(AccountActivity.SETTINGS_USE_SSO, false)) {
                try {
                    val ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(this)
                    ssoAccount.userId to ssoAccount.url.replace(Regex("/+$"), "").replace(Regex("^https?://"), "")
                } catch (_: Exception) { "error" to "error" }
            } else {
                preferences.getString(AccountActivity.SETTINGS_USERNAME, "") to preferences.getString(
                    AccountActivity.SETTINGS_URL, "")?.replace(Regex("/+$"), "")?.replace(Regex("^https?://"), "")
            }
            val text = "$user@$server"
            viewModel.accountName = text
            updateAvatarInDrawer(true)
        }
    }

    private fun updateAvatarInDrawer(isConfigured: Boolean) {
        if (isConfigured) {
            val avatarB64 = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.pref_key_avatar), "")
            if (!avatarB64.isNullOrEmpty()) {
                try {
                    val bytes = Base64.decode(avatarB64, Base64.DEFAULT)
                    viewModel.userAvatar = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    viewModel.userAvatar = null
                }
            } else {
                viewModel.userAvatar = null
            }
        } else {
            viewModel.userAvatar = null
        }
    }

    private fun synchronize(manual: Boolean = false) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val offlineMode = preferences.getBoolean(getString(R.string.pref_key_offline_mode), false)
        if (offlineMode && !manual) {
            return
        }

        if (db.cowspentServerSyncHelper.isSyncPossible) {
            viewModel.isRefreshing = true
            val selectedProjectId = PreferenceManager.getDefaultSharedPreferences(applicationContext).getLong("selected_project", 0)
            if (selectedProjectId != 0L) {
                lifecycleScope.launch {
                    val proj = withContext(Dispatchers.IO) { db.getProject(selectedProjectId) }
                    if (proj != null && !proj.isLocal) {
                        db.cowspentServerSyncHelper.addCallbackPull(syncCallBack)
                        db.cowspentServerSyncHelper.scheduleSync(false, selectedProjectId)
                    } else viewModel.isRefreshing = false
                }
            } else viewModel.isRefreshing = false
            if (CowspentServerSyncHelper.isNextcloudAccountConfigured(applicationContext)) {
                db.cowspentServerSyncHelper.runAccountProjectsSync()
            }
        }
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(MainConstants.BROADCAST_PROJECT_SYNC_FAILED)
            addAction(MainConstants.BROADCAST_PROJECT_SYNCED)
            addAction(MainConstants.BROADCAST_SYNC_PROJECT)
            addAction(MainConstants.BROADCAST_NETWORK_AVAILABLE)
            addAction(MainConstants.BROADCAST_NETWORK_UNAVAILABLE)
            addAction(MainConstants.BROADCAST_AVATAR_UPDATED)
            addAction(MainConstants.BROADCAST_SSO_TOKEN_MISMATCH)
            addAction(MainConstants.BROADCAST_ACCOUNT_PROJECTS_SYNC_FAILED)
            addAction(MainConstants.BROADCAST_ACCOUNT_PROJECTS_SYNCED)
        }
        ContextCompat.registerReceiver(this, mBroadcastReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == null) return
            when (intent.action) {
                MainConstants.BROADCAST_PROJECT_SYNC_FAILED -> {
                    val errorMessage = intent.getStringExtra(MainConstants.BROADCAST_ERROR_MESSAGE)
                    val projectId = intent.getLongExtra(MainConstants.BROADCAST_PROJECT_ID, 0)
                    if (projectId != 0L) {
                        lifecycleScope.launch {
                            val project = withContext(Dispatchers.IO) { db.getProject(projectId) } ?: return@launch
                            viewModel.showDialog(
                                title = getString(R.string.sync_error_dialog_title),
                                message = getString(R.string.sync_error_dialog_full_content, project.name, errorMessage),
                                positiveText = getString(R.string.simple_close),
                                icon = Icons.Default.Sync
                            )
                        }
                    }
                }
                MainConstants.BROADCAST_PROJECT_SYNCED -> {
                    setupDrawerProjects()
                    refreshLists()
                }
                MainConstants.BROADCAST_SYNC_PROJECT -> {
                    synchronize()
                }
                MainConstants.BROADCAST_NETWORK_AVAILABLE -> {
                }
                MainConstants.BROADCAST_NETWORK_UNAVAILABLE -> {
                }
                MainConstants.BROADCAST_SSO_TOKEN_MISMATCH -> {
                    viewModel.showDialog(
                        title = getString(R.string.sync_error_dialog_title),
                        message = getString(R.string.error_token_mismatch),
                        positiveText = getString(R.string.simple_close),
                        icon = Icons.Default.Sync
                    )
                }
                MainConstants.BROADCAST_AVATAR_UPDATED -> {
                    val memberId = intent.getLongExtra(MainConstants.BROADCAST_AVATAR_UPDATED_MEMBER, 0)
                    if (memberId == 0L) updateAvatarInDrawer(true) else refreshLists()
                }
                MainConstants.BROADCAST_ACCOUNT_PROJECTS_SYNCED -> {
                    setupDrawerProjects()
                    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    lifecycleScope.launch {
                        val dbProjects = withContext(Dispatchers.IO) { db.projects }
                        if (prefs.getLong("selected_project", 0) == 0L && dbProjects.isNotEmpty()) {
                            setSelectedProject(dbProjects[0].id)
                            refreshLists()
                            if (db.cowspentServerSyncHelper.isSyncPossible) {
                                db.cowspentServerSyncHelper.addCallbackPull(syncCallBack)
                                synchronize()
                            }
                        }
                    }
                }
            }
        }
    }
}
