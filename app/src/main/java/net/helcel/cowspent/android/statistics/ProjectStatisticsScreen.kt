package net.helcel.cowspent.android.statistics

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.helcel.cowspent.R
import net.helcel.cowspent.model.*
import net.helcel.cowspent.persistence.CowspentSQLiteOpenHelper
import net.helcel.cowspent.util.CategoryUtils

@Composable
fun ProjectStatisticsScreen(
    proj: DBProject,
    db: CowspentSQLiteOpenHelper,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var selectedTab by rememberSaveable {
        mutableIntStateOf(prefs.getInt("last_statistics_tab", 0))
    }
    var currentShareText by remember { mutableStateOf("") }
    val tabs = listOf(
        stringResource(R.string.statistic_title),
        "Trend",
        "Sankey"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistic_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (currentShareText.isNotEmpty()) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, currentShareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                elevation = 0.dp
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            prefs.edit { putInt("last_statistics_tab", index) }
                        },
                        text = { Text(title) }
                    )
                }
            }
            
            val statsData by produceState<StatisticsData?>(null, proj.id) {
                value = withContext(Dispatchers.IO) {
                    val members = db.getMembersOfProject(proj.id, null)
                    val bills = db.getBillsOfProject(proj.id)
                    val categories = db.getCategories(proj.id)
                    val paymentModes = db.getPaymentModes(proj.id)
                    StatisticsData(members, bills, categories, paymentModes)
                }
            }

            if (statsData != null) {
                val data = statsData!!
                val defaultCategories = remember(proj.id) { CategoryUtils.getDefaultCategories(context, proj.id) }
                val categories = remember(proj.type, data.categories, defaultCategories) {
                    val hardcoded = if (proj.type == ProjectType.LOCAL) {
                        defaultCategories
                    } else {
                        listOfNotNull(defaultCategories.find { it.remoteId.toInt() == DBBill.CATEGORY_REIMBURSEMENT })
                    }
                    (data.categories + hardcoded).distinctBy { it.remoteId }
                }
                val categoryNoneLabel = stringResource(R.string.category_none)
                val sankeyCategories = remember(proj.id, data.categories, defaultCategories, categoryNoneLabel) {
                    val noneCategory = DBCategory(0, 0, proj.id, categoryNoneLabel, "❌", "#9E9E9E")
                    (data.categories + defaultCategories + noneCategory).distinctBy { it.remoteId }
                }

                when (selectedTab) {
                    0 -> {
                        ProjectStatisticsTable(
                            proj = proj,
                            allMembers = data.members,
                            allBills = data.bills,
                            customCategories = categories,
                            customPaymentModes = data.paymentModes,
                            onShareReady = { currentShareText = it }
                        )
                    }
                    1 -> {
                        ProjectSpendingGraph(
                            projectName = proj.name.ifEmpty { proj.remoteId },
                            allMembers = data.members,
                            allBills = data.bills,
                            onShareReady = { currentShareText = it }
                        )
                    }
                    2 -> {
                        ProjectSankeyDiagram(
                            projectName = proj.name.ifEmpty { proj.remoteId },
                            allMembers = data.members,
                            allBills = data.bills,
                            customCategories = sankeyCategories,
                            onShareReady = { currentShareText = it }
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

data class StatisticsData(
    val members: List<DBMember>,
    val bills: List<DBBill>,
    val categories: List<DBCategory>,
    val paymentModes: List<DBPaymentMode>
)
