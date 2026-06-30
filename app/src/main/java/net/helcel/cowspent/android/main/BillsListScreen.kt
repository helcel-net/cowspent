package net.helcel.cowspent.android.main

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import net.helcel.cowspent.R
import net.helcel.cowspent.android.drawer.Drawer
import net.helcel.cowspent.android.helper.StatefulAlertDialog
import net.helcel.cowspent.android.project.ProjectOptionsDialogContent
import net.helcel.cowspent.android.project.ProjectShareDialogContent
import net.helcel.cowspent.android.project.member.MemberAddDialogContent
import net.helcel.cowspent.android.project.member.MemberEditDialogContent
import net.helcel.cowspent.android.project.member.MemberManagementDialogContent
import net.helcel.cowspent.android.project.settle.ProjectSettlementDialogContent
import net.helcel.cowspent.android.statistics.ProjectStatisticsActivity
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.ProjectType
import net.helcel.cowspent.model.SectionItem
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.IRefreshBillsListCallback

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BillsListScreen(
    viewModel: BillsListViewModel,
    db: CowspentSQLiteOpenHelper,
    refreshCallback: IRefreshBillsListCallback,
    onAddBillClick: () -> Unit,
    onBillClick: (DBBill) -> Unit,
    onProjectClick: (Long) -> Unit,
    onProjectOptionsClick: (Long) -> Unit,
    onProjectAction: (Long, Int) -> Unit,
    onAccountSwitcherClick: () -> Unit,
    onAddProjectClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onLabelBillsClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(viewModel.searchQuery) {
        refreshCallback.refreshLists(false)
    }

    val pullRefreshState = rememberPullRefreshState(viewModel.isRefreshing, onRefresh)
    val context = LocalContext.current
    val memberAlreadyExistsError = stringResource(R.string.member_already_exists)
    val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val showArchived = sharedPreferences.getBoolean(stringResource(R.string.pref_key_show_archived), false)

    StatefulAlertDialog(
        state = viewModel.dialogState,
        onDismissRequest = { viewModel.dismissDialog() }
    )

    val projectOptionsProjectId = viewModel.showProjectOptionsDialogByProjectId
    if (projectOptionsProjectId != null) {
        val proj = remember(projectOptionsProjectId, viewModel.projects) {
            viewModel.projects.find { it.id == projectOptionsProjectId }
        }
        Dialog(
            onDismissRequest = { viewModel.showProjectOptionsDialogByProjectId = null },
        ) {
            ProjectOptionsDialogContent(
                onEditProject = {
                    onProjectAction(projectOptionsProjectId, 0)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onRemoveProject = {
                    onProjectAction(projectOptionsProjectId, 1)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onManageMembers = {
                    onProjectAction(projectOptionsProjectId, 2)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onManageCurrencies = {
                    onProjectAction(projectOptionsProjectId, 3)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onManageLabels = {
                    onProjectAction(projectOptionsProjectId, 8)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onStatistics = {
                    onProjectAction(projectOptionsProjectId, 4)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onSettle = {
                    onProjectAction(projectOptionsProjectId, 5)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onShareProject = {
                    onProjectAction(projectOptionsProjectId, 6)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onExportProject = {
                    onProjectAction(projectOptionsProjectId, 7)
                    viewModel.showProjectOptionsDialogByProjectId = null
                },
                onDismiss = { viewModel.showProjectOptionsDialogByProjectId = null },
                isArchived = proj?.isArchived == true,
                projectType = proj?.type ?: ProjectType.LOCAL,
                accessLevel = proj?.myAccessLevel ?: DBProject.ACCESS_LEVEL_ADMIN,
                isShareable = proj?.isShareable() ?: true
            )
        }
    }

    val settlementProjectId = viewModel.showSettlementDialogByProjectId
    if (settlementProjectId != null) {
        val proj = remember(settlementProjectId, viewModel.projects) {
            viewModel.projects.find { it.id == settlementProjectId }
        }
        if (proj != null) {
            Dialog(
                onDismissRequest = { viewModel.showSettlementDialogByProjectId = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ProjectSettlementDialogContent(
                    proj = proj,
                    db = db,
                    onSettleBills = { transactions ->
                        BillsListUtils.createBillsFromTransactions(
                            db,
                            settlementProjectId,
                            transactions,
                            refreshCallback,
                            context
                        )
                        viewModel.showSettlementDialogByProjectId = null
                    },
                    onShare = { transactions, memberIdToName ->
                        BillsListUtils.shareSettlement(context, proj, transactions, memberIdToName)
                    },
                    onDismiss = { viewModel.showSettlementDialogByProjectId = null }
                )
            }
        }
    }

    val statisticsProjectId = viewModel.showStatisticsDialogByProjectId
    if (statisticsProjectId != null) {
        LaunchedEffect(statisticsProjectId) {
            context.startActivity(
                ProjectStatisticsActivity.createIntent(
                    context,
                    statisticsProjectId
                )
            )
            viewModel.showStatisticsDialogByProjectId = null
        }
    }

    val manageMembersProjectId = viewModel.showMemberManagementDialogByProjectId
    if (manageMembersProjectId != null) {
        val members = viewModel.members // Use members from ViewModel as they are already loaded
        Dialog(
            onDismissRequest = { viewModel.showMemberManagementDialogByProjectId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            MemberManagementDialogContent(
                members = members,
                onAddMember = {
                    viewModel.showAddMemberDialogByProjectId = manageMembersProjectId
                    viewModel.showMemberManagementDialogByProjectId = null
                },
                onEditMember = { member ->
                    viewModel.showEditMemberDialogByProjectId = member.id
                    viewModel.showMemberManagementDialogByProjectId = null
                },
                onDismiss = { viewModel.showMemberManagementDialogByProjectId = null }
            )
        }
    }

    val addMemberProjectId = viewModel.showAddMemberDialogByProjectId
    if (addMemberProjectId != null) {
        Dialog(
            onDismissRequest = { viewModel.showAddMemberDialogByProjectId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            MemberAddDialogContent(
                onAdd = { memberName ->
                    val memberNames =
                        db.getMembersOfProject(addMemberProjectId, null).map { it.name }
                    if (memberNames.contains(memberName)) {
                        Toast.makeText(
                            context,
                            memberAlreadyExistsError,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val color = net.helcel.cowspent.android.helper.TextDrawable.getColorFromName(memberName)
                        db.addMemberAndSync(
                            DBMember(
                                0,
                                0,
                                addMemberProjectId,
                                memberName,
                                true,
                                1.0,
                                DBBill.STATE_ADDED,
                                android.graphics.Color.red(color),
                                android.graphics.Color.green(color),
                                android.graphics.Color.blue(color),
                                null,
                                null
                            )
                        )
                        refreshCallback.refreshLists(false)
                        viewModel.showAddMemberDialogByProjectId = null
                        viewModel.showMemberManagementDialogByProjectId = addMemberProjectId
                    }
                },
                onDismiss = { 
                    viewModel.showAddMemberDialogByProjectId = null
                    viewModel.showMemberManagementDialogByProjectId = addMemberProjectId
                }
            )
        }
    }

    val editMemberId = viewModel.showEditMemberDialogByProjectId
    if (editMemberId != null) {
        val memberToEdit = remember(editMemberId, viewModel.members) {
            viewModel.members.find { it.id == editMemberId }
        }
        if (memberToEdit != null) {
            Dialog(
                onDismissRequest = { viewModel.showEditMemberDialogByProjectId = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                MemberEditDialogContent(
                    member = memberToEdit,
                    onSave = { name, weight, isActivated, r, g, b ->
                        db.updateMemberAndSync(
                            memberToEdit,
                            name,
                            weight,
                            isActivated,
                            r,
                            g,
                            b,
                            "",
                            ""
                        )
                        refreshCallback.refreshLists(false)
                        viewModel.showEditMemberDialogByProjectId = null
                        viewModel.showMemberManagementDialogByProjectId = memberToEdit.projectId
                    },
                    onDelete = {
                        db.deleteMember(editMemberId)
                        refreshCallback.refreshLists(false)
                        viewModel.showEditMemberDialogByProjectId = null
                        viewModel.showMemberManagementDialogByProjectId = memberToEdit.projectId
                    },
                    onDismiss = { 
                        viewModel.showEditMemberDialogByProjectId = null
                        viewModel.showMemberManagementDialogByProjectId = memberToEdit.projectId
                    }
                )
            }
        }
    }

    val shareProjectId = viewModel.showShareDialogByProjectId
    if (shareProjectId != null) {
        val proj = remember(shareProjectId, viewModel.projects) {
            viewModel.projects.find { it.id == shareProjectId }
        }
        if (proj != null) {
            val shareIntentTitle = stringResource(R.string.share_share_intent_title, proj.name)
            val shareChooserTitle = stringResource(R.string.share_share_chooser_title, proj.name)
            Dialog(
                onDismissRequest = { viewModel.showShareDialogByProjectId = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ProjectShareDialogContent(
                    proj = proj,
                    onShare = { shareUrl ->
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareIntentTitle)
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        val chooserIntent = Intent.createChooser(shareIntent, shareChooserTitle)
                        context.startActivity(chooserIntent)
                    },
                    onDismiss = { viewModel.showShareDialogByProjectId = null }
                )
            }
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        drawerShape = RectangleShape,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        TextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.searchQuery = it },
                            placeholder = { Text(stringResource(R.string.action_search), color = MaterialTheme.colors.onPrimary.copy(alpha = 0.7f)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = Color.Transparent,
                                cursorColor = MaterialTheme.colors.onPrimary,
                                textColor = MaterialTheme.colors.onPrimary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    isSearchExpanded = false
                                    viewModel.searchQuery = ""
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colors.onPrimary)
                                }
                            }
                        )
                    } else {
                        Column {
                            if (viewModel.title.isNotEmpty()) Text(viewModel.title)
                            else Text(stringResource(R.string.app_name))
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchExpanded) {
                        IconButton(onClick = {
                            isSearchExpanded = false
                            viewModel.searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = {
                            scope.launch { scaffoldState.drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        if (viewModel.hasUnlabeledBills) {
                            IconButton(onClick = onLabelBillsClick) {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = stringResource(R.string.action_label_bills)
                                )
                            }
                        }
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                elevation = 4.dp
            )
        },
        floatingActionButton = {
            val selectedProject = viewModel.projects.find { it.id == viewModel.selectedProjectId }
            if (selectedProject != null && !selectedProject.isArchived) {
                FloatingActionButton(onClick = onAddBillClick) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_create_bill))
                }
            }
        },
        drawerContent = {
            val selectedProject = viewModel.projects.find { it.id == viewModel.selectedProjectId }
            Drawer(
                projects = viewModel.projects,
                members = viewModel.members,
                memberBalances = viewModel.memberBalances,
                selectedProjectId = viewModel.selectedProjectId,
                selectedMemberId = viewModel.selectedMemberId,
                lastSyncText = viewModel.lastSyncText,
                mainCurrency = selectedProject?.currencyName,
                showArchived = showArchived,
                onProjectClick = {
                    viewModel.selectedMemberId = null
                    onProjectClick(it)
                    scope.launch { scaffoldState.drawerState.close() }
                },
                onProjectOptionsClick = {
                    onProjectOptionsClick(it)
                },
                onMemberClick = { memberId ->
                    viewModel.selectedMemberId = memberId
                    refreshCallback.refreshLists(false)
                    scope.launch { scaffoldState.drawerState.close() }
                },
                onAddProjectClick = {
                    onAddProjectClick()
                    scope.launch { scaffoldState.drawerState.close() }
                },
                onAppSettingsClick = {
                    onAppSettingsClick()
                    scope.launch { scaffoldState.drawerState.close() }
                }
            )
        }
    ) { padding ->
        // Pull-to-refresh is not officially in Material 2 basic components, 
        // but we can use SwipeRefresh from accompanist or implement it manually.
        // For simplicity and following common practices, I'll assume standard swipe refresh logic.
        // Actually, there is androidx.compose.material.pullrefresh.pullRefresh in later Material 2.
        
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .pullRefresh(pullRefreshState)) {
            when {
                viewModel.showNoProjects -> EmptyProjectsState(onAccountSwitcherClick, onAddProjectClick)
                viewModel.showNoMembers -> EmptyMembersState()
                viewModel.showNoBills -> EmptyBillsState()
                viewModel.bills.isEmpty() -> EmptyState()
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(viewModel.bills) { item ->
                            when (item) {
                                is DBBill -> {
                                    val payer = viewModel.members.find { it.id == item.payerId }
                                    BillItemRow(item, payer, onClick = { onBillClick(item) })
                                }
                                is SectionItem -> SectionHeader(item.title)
                            }
                        }
                    }
                }
            }
            PullRefreshIndicator(
                refreshing = viewModel.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}



@Preview(showBackground = true)
@Composable
fun BillItemRowPreview() {
    MaterialTheme {
        BillItemRow(
            bill = DBBill(0, 0, 0, 1, 15.0, System.currentTimeMillis() / 1000, "Dinner", 0, "n", null, 0, "", 0).apply {
                formattedWhat = "Dinner"
                formattedSubtitle = "Alice \u2192 Group"
            },
            payer = DBMember(1, 0, 0, "Alice", true, 1.0, 0, null, null, null, null, null),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SectionHeaderPreview() {
    MaterialTheme {
        SectionHeader(title = "October 2023")
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun BillsListScreenPreview() {
    MaterialTheme {
        BillsListScreen(
            viewModel = BillsListViewModel().apply {
                title = "Demo Project"
                members = listOf(
                    DBMember(1, 0, 0, "Alice", true, 1.0, 0, null, null, null, null, null)
                )
                bills = listOf(
                    SectionItem("Today"),
                    DBBill(0, 0, 0, 1, 10.0, System.currentTimeMillis() / 1000, "Lunch", 0, "n", null, 0, "", 0).apply {
                        formattedWhat = "Lunch"
                        formattedSubtitle = "Alice \u2192 Group"
                    }
                )
            },
            db = CowspentSQLiteOpenHelper.getInstance(LocalContext.current),
            refreshCallback = object : IRefreshBillsListCallback {
                override fun refreshLists(scrollToTop: Boolean) {}
            },
            onAddBillClick = {},
            onBillClick = {},
            onProjectClick = {},
            onProjectOptionsClick = {},
            onProjectAction = { _, _ -> },
            onAccountSwitcherClick = {},
            onAddProjectClick = {},
            onAppSettingsClick = {},
            onLabelBillsClick = {},
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyProjectsStatePreview() {
    MaterialTheme {
        EmptyProjectsState(onConfigureNextcloud = {}, onAddManually = {})
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyMembersStatePreview() {
    MaterialTheme {
        EmptyMembersState()
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyBillsStatePreview() {
    MaterialTheme {
        EmptyBillsState()
    }
}
