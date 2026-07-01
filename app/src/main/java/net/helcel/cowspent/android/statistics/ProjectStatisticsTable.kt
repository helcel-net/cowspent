package net.helcel.cowspent.android.statistics

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.*
import net.helcel.cowspent.model.*
import net.helcel.cowspent.util.CategoryUtils
import net.helcel.cowspent.util.SupportUtil
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.round

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProjectStatisticsTable(
    proj: DBProject,
    allMembers: List<DBMember>,
    allBills: List<DBBill>,
    customCategories: List<DBCategory>,
    customPaymentModes: List<DBPaymentMode>,
    onShareReady: (String) -> Unit
) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) }
    val dateFormat = remember { android.text.format.DateFormat.getDateFormat(context) }

    var categoryId by remember { mutableIntStateOf(-1000) }
    var paymentModeId by remember { mutableIntStateOf(-1000) }
    var dateMin by remember { mutableStateOf<String?>(null) }
    var dateMax by remember { mutableStateOf<String?>(null) }

    val categoryAll = stringResource(R.string.category_all)
    val categoryNone = stringResource(R.string.category_none)
    val categoryReimbursement = stringResource(R.string.category_reimbursement)
    val categoryAllExceptReimbursement = stringResource(R.string.category_all_except_reimbursement)

    val paymentModeAll = stringResource(R.string.payment_mode_all)
    val paymentModeNone = stringResource(R.string.payment_mode_none)

    val shareStatsHeader = stringResource(R.string.msg_stats_header)
    val shareStatsIntro = stringResource(R.string.msg_stats_intro, proj.name.ifEmpty { proj.remoteId })

    val categories = remember(proj.id, customCategories, categoryAll, categoryNone, categoryReimbursement, categoryAllExceptReimbursement) {
        val list = mutableListOf<Triple<Int, String, String>>()
        list.add(Triple(-1000, "📋", categoryAll))
        list.add(Triple(-100, "🧾", categoryAllExceptReimbursement))
        list.add(Triple(0, "❌", categoryNone))

        val catsToUse = if (proj.type == ProjectType.LOCAL) {
            CategoryUtils.getDefaultCategories(context, proj.id)
        } else {
            customCategories.ifEmpty {
                CategoryUtils.getDefaultCategories(context, proj.id)
            }
        }

        catsToUse.forEach {
            list.add(Triple(it.remoteId.toInt(), it.icon, it.name ?: ""))
        }
        list.distinctBy { it.first }
    }

    val paymentModes = remember(proj.id, customPaymentModes, paymentModeAll, paymentModeNone) {
        val list = mutableListOf<Triple<Int, String, String>>()
        list.add(Triple(-1000, "💳", paymentModeAll))
        list.add(Triple(0, "❌", paymentModeNone))

        val pmsToUse = if (proj.type == ProjectType.LOCAL) {
            CategoryUtils.getDefaultPaymentModes(context, proj.id)
        } else {
            customPaymentModes.ifEmpty {
                CategoryUtils.getDefaultPaymentModes(context, proj.id)
            }
        }

        pmsToUse.forEach {
            list.add(Triple(it.remoteId.toInt(), it.icon, it.name ?: ""))
        }
        list.distinctBy { it.first }
    }

    val stats = remember(allMembers, allBills, categoryId, paymentModeId, dateMin, dateMax, shareStatsHeader, shareStatsIntro) {
        val membersNbBills = mutableMapOf<Long, Int>()
        val membersBalance = HashMap<Long, Double>()
        val membersPaid = HashMap<Long, Double>()
        val membersSpent = HashMap<Long, Double>()

        SupportUtil.getStats(
            allMembers, allBills,
            membersNbBills, membersBalance, membersPaid, membersSpent,
            categoryId, paymentModeId, dateMin, dateMax
        )

        var statsText = shareStatsIntro + "\n\n"
        statsText += shareStatsHeader + "\n"

        var totalPaid = 0.0
        val memberStats = allMembers.map { m ->
            val mPaid = membersPaid[m.id] ?: 0.0
            totalPaid += mPaid
            val mSpent = membersSpent[m.id] ?: 0.0
            val mBalance = membersBalance[m.id] ?: 0.0

            val rpaid = round(mPaid * 100.0) / 100.0
            val rspent = round(mSpent * 100.0) / 100.0
            val rbalance = round(abs(mBalance) * 100.0) / 100.0
            val sign = if (mBalance > 0.01) "+" else if (mBalance < -0.01) "-" else ""

            statsText += "\n${m.name} ("
            statsText += (if (rpaid == 0.0) "--" else SupportUtil.normalNumberFormat.format(rpaid)) + " | "
            statsText += (if (rspent == 0.0) "--" else SupportUtil.normalNumberFormat.format(rspent)) + " | "
            statsText += "$sign${SupportUtil.normalNumberFormat.format(rbalance)})"

            MemberStat(m.name, mPaid, mSpent, mBalance)
        }

        StatsResult(memberStats, totalPaid, statsText)
    }

    val dateMinLong = remember(dateMin) {
        dateMin?.let { try { sdf.parse(it)?.time } catch (_: Exception) { null } }
    }
    val dateMaxLong = remember(dateMax) {
        dateMax?.let { try { sdf.parse(it)?.time } catch (_: Exception) { null } }
    }

    LaunchedEffect(stats.statsText) {
        onShareReady(stats.statsText)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            elevation = 2.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                var categoryExpanded by remember { mutableStateOf(value = false) }
                val selectedCategory = categories.find { it.first == categoryId }

                EditableExposedDropdownMenu(
                    value = selectedCategory?.third ?: "",
                    placeholder = stringResource(R.string.label_category),
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    onDismissRequest = { categoryExpanded = false },
                    leadingIcon = {
                        Box(modifier = Modifier) {
                            if (selectedCategory != null) {
                                Text(text = selectedCategory.second, fontSize = 20.sp)
                            } else {
                                Icon(Icons.Default.Category, contentDescription = null)
                            }
                        }
                    },
                    content = {
                        categories.forEach { category ->
                            DropdownMenuItem(onClick = {
                                categoryId = category.first
                                categoryExpanded = false
                            }) {
                                Text(text = category.second, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(category.third)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                var pmExpanded by remember { mutableStateOf(false) }
                val selectedPm = paymentModes.find { it.first == paymentModeId }

                EditableExposedDropdownMenu(
                    value = selectedPm?.third ?: "",
                    placeholder = stringResource(R.string.label_mode),
                    expanded = pmExpanded,
                    onExpandedChange = { pmExpanded = it },
                    onDismissRequest = { pmExpanded = false },
                    leadingIcon = {
                        Box(modifier = Modifier) {
                            if (selectedPm != null) {
                                Text(text = selectedPm.second, fontSize = 20.sp)
                            } else {
                                Icon(Icons.Default.Payment, contentDescription = null)
                            }
                        }
                    },
                    content = {
                        paymentModes.forEach { pm ->
                            DropdownMenuItem(onClick = {
                                paymentModeId = pm.first
                                pmExpanded = false
                            }) {
                                Text(text = pm.second, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(pm.third)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    ClickableOutlinedTextField(
                        value = dateMin?.let { sdf.parse(it)?.let { d -> dateFormat.format(d) } } ?: "",
                        onClick = {
                            showDatePicker(context, dateMin, sdf, maxDate = dateMaxLong) { dateMin = it }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.stats_date_min)) },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                        trailingIcon = if (dateMin != null) {
                            {
                                IconButton(onClick = { dateMin = null }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red.copy(alpha = 0.6f))
                                }
                            }
                        } else null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ClickableOutlinedTextField(
                        value = dateMax?.let { sdf.parse(it)?.let { d -> dateFormat.format(d) } } ?: "",
                        onClick = {
                            showDatePicker(context, dateMax, sdf, minDate = dateMinLong) { dateMax = it }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.stats_date_max)) },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                        trailingIcon = if (dateMax != null) {
                            {
                                IconButton(onClick = { dateMax = null }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red.copy(alpha = 0.6f))
                                }
                            }
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.stats_who).uppercase(), modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, style = MaterialTheme.typography.overline)
                Text(stringResource(R.string.stats_paid).uppercase(), modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colors.onSurface, style = MaterialTheme.typography.overline)
                Text(stringResource(R.string.stats_spent).uppercase(), modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colors.onSurface, style = MaterialTheme.typography.overline)
                Text(stringResource(R.string.stats_balance).uppercase(), modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colors.onSurface, style = MaterialTheme.typography.overline)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(stats.memberStats) { m ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, modifier = Modifier.weight(2f), color = MaterialTheme.colors.onSurface, fontWeight = FontWeight.Medium)

                    Text(
                        if (m.paid == 0.0) "--" else SupportUtil.normalNumberFormat.format(m.paid),
                        modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colors.onSurface
                    )

                    Text(
                        if (m.spent == 0.0) "--" else SupportUtil.normalNumberFormat.format(m.spent),
                        modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colors.onSurface
                    )

                    val balanceColor = if (m.balance > 0.01) colorResource(R.color.green) else if (m.balance < -0.01) colorResource(R.color.red) else MaterialTheme.colors.onSurface
                    val sign = if (m.balance > 0.01) "+" else if (m.balance < -0.01) "-" else ""
                    Text(
                        "$sign${SupportUtil.normalNumberFormat.format(abs(m.balance))}",
                        modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.End,
                        color = balanceColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Divider(thickness = 0.5.dp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.05f))
            }
        }


        Card(
            elevation = 4.dp,
            shape = MaterialTheme.shapes.medium,
            backgroundColor = MaterialTheme.colors.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.padding(16.dp, 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.total, SupportUtil.normalNumberFormat.format(stats.totalPaid)),
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onPrimary
                )
            }
        }
    }
}

private fun showDatePicker(
    context: Context,
    currentDate: String?,
    sdf: SimpleDateFormat,
    minDate: Long? = null,
    maxDate: Long? = null,
    onDateSelected: (String) -> Unit
) {
    val calendar = Calendar.getInstance()
    currentDate?.let {
        try { sdf.parse(it)?.let { date -> calendar.time = date } } catch (_: Exception) {}
    }
    val dialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            onDateSelected(sdf.format(cal.time))
        },
        calendar[Calendar.YEAR],
        calendar[Calendar.MONTH],
        calendar[Calendar.DAY_OF_MONTH]
    )
    minDate?.let { dialog.datePicker.minDate = it }
    maxDate?.let { dialog.datePicker.maxDate = it }
    dialog.show()
}

data class MemberStat(val name: String, val paid: Double, val spent: Double, val balance: Double)
data class StatsResult(val memberStats: List<MemberStat>, val totalPaid: Double, val statsText: String)


@Preview(showBackground = true)
@Composable
fun ProjectStatisticsTablePreview() {
    MaterialTheme {
        ProjectStatisticsTable(
            proj = StatisticsMockData.project,
            allMembers = StatisticsMockData.members,
            allBills = StatisticsMockData.bills,
            customCategories = emptyList(),
            customPaymentModes = emptyList(),
            onShareReady = {}
        )    }
}
