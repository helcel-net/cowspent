package net.helcel.cowspent.android.statistics

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.formatShortValue
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBMember
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

enum class SpendingTimeView(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

@Composable
fun ProjectSpendingGraph(
    projectName: String,
    allMembers: List<DBMember>,
    allBills: List<DBBill>,
    onShareReady: (String) -> Unit
) {
    val shareStatsIntro = stringResource(R.string.msg_stats_intro, projectName)
    if (allBills.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data to display", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    var timeView by remember { mutableStateOf(SpendingTimeView.MONTHLY) }
    var showTotal by remember { mutableStateOf(false) }
    var showMovingAverage by remember { mutableStateOf(true) }

    val sortedBills = remember(allBills) { allBills.sortedBy { it.timestamp } }
    val projectMaxTimestamp = sortedBills.last().timestamp

    // Period generation logic
    val periods = remember(timeView, sortedBills.first().timestamp, projectMaxTimestamp) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = sortedBills.first().timestamp * 1000
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        when (timeView) {
            SpendingTimeView.WEEKLY -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            SpendingTimeView.MONTHLY -> cal.set(Calendar.DAY_OF_MONTH, 1)
            SpendingTimeView.YEARLY -> cal.set(Calendar.DAY_OF_YEAR, 1)
        }
        
        val list = mutableListOf<Long>()
        val endLimit = projectMaxTimestamp * 1000
        while (cal.timeInMillis <= endLimit) {
            list.add(cal.timeInMillis / 1000)
            when (timeView) {
                SpendingTimeView.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                SpendingTimeView.MONTHLY -> cal.add(Calendar.MONTH, 1)
                SpendingTimeView.YEARLY -> cal.add(Calendar.YEAR, 1)
            }
        }
        list
    }

    // Consolidated spending data calculation
    val chartData = remember(allMembers, sortedBills, periods, showTotal) {
        val memberSpending = mutableMapOf<Long, List<Double>>()
        allMembers.forEach { member ->
            val spending = mutableListOf<Double>()
            for (i in periods.indices) {
                val start = periods[i]
                val end = if (i + 1 < periods.size) periods[i + 1] else Long.MAX_VALUE
                val amount = sortedBills.asSequence()
                    .filter { it.timestamp in start..<end && it.payerId == member.id }
                    .sumOf { it.amount }
                spending.add(amount)
            }
            memberSpending[member.id] = spending
        }

        // EMA Trend Line calculation
        val alpha = 0.7 
        var lastEma = 0.0
        val trend = periods.indices.map { i ->
            val totalInPeriod = allMembers.sumOf { memberSpending[it.id]?.get(i) ?: 0.0 }
            val currentMetric = if (showTotal) totalInPeriod else totalInPeriod / allMembers.size.coerceAtLeast(1)
            lastEma = if (i == 0) currentMetric else alpha * currentMetric + (1 - alpha) * lastEma
            lastEma
        }

        val maxBar = if (showTotal) {
            periods.indices.maxOfOrNull { i ->
                allMembers.sumOf { memberSpending[it.id]?.get(i) ?: 0.0 }
            } ?: 1.0
        } else {
            periods.indices.maxOfOrNull { i ->
                allMembers.maxOfOrNull { memberSpending[it.id]?.get(i) ?: 0.0 } ?: 0.0
            } ?: 1.0
        }
        val maxVal = maxOf(maxBar, trend.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

        Triple(memberSpending, trend, maxVal)
    }

    val memberSpendingByPeriod = chartData.first
    val trendLine = chartData.second
    val maxSpendingInPeriod = chartData.third

    LaunchedEffect(memberSpendingByPeriod, periods, timeView, projectName) {
        val statsText = StringBuilder()
        statsText.append(shareStatsIntro).append("\n\n")
        statsText.append("Spending Trend (${timeView.label}):\n")
        periods.indices.forEach { i ->
            val timestamp = periods[i]
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(timestamp * 1000))
            val total = allMembers.sumOf { memberSpendingByPeriod[it.id]?.get(i) ?: 0.0 }
            if (total > 0) {
                statsText.append("- $dateStr: ${total.toInt()}\n")
            }
        }
        val grandTotal = allMembers.sumOf { m -> memberSpendingByPeriod[m.id]?.sum() ?: 0.0 }
        statsText.append("\nTotal: ${grandTotal.toInt()}")
        onShareReady(statsText.toString())
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Card(
            elevation = 4.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val totalAvailableWidth = constraints.maxWidth.toFloat()
                val totalHeight = constraints.maxHeight.toFloat()
                
                val yAxisWidth = 44.dp
                val xAxisHeight = 32.dp
                
                val density = LocalDensity.current
                val yAxisWidthPx = with(density) { yAxisWidth.toPx() }
                val xAxisHeightPx = with(density) { xAxisHeight.toPx() }
                
                val chartHeight = totalHeight - xAxisHeightPx

                // Y-Axis Labels
                Box(modifier = Modifier.width(yAxisWidth).height(with(density) { chartHeight.toDp() })) {
                    for (i in 0..5) {
                        val value = (i.toFloat() / 5) * maxSpendingInPeriod
                        val yOffset = chartHeight - (i.toFloat() / 5) * chartHeight
                        Text(
                            text = formatShortValue(value),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = with(density) { (yOffset - 6.sp.toPx()).toDp() })
                                .padding(end = 8.dp)
                        )
                    }
                }

                val scrollState = rememberScrollState()
                LaunchedEffect(scrollState.maxValue) {
                    if (scrollState.maxValue > 0) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                }
                val minPeriodWidth = when(timeView) {
                    SpendingTimeView.WEEKLY -> 48.dp
                    SpendingTimeView.MONTHLY -> 72.dp
                    SpendingTimeView.YEARLY -> 110.dp
                }
                val contentWidth = max(totalAvailableWidth - yAxisWidthPx, with(density) { periods.size * minPeriodWidth.toPx() })

                Box(
                    modifier = Modifier
                        .offset(x = yAxisWidth)
                        .width(with(density) { (totalAvailableWidth - yAxisWidthPx).toDp() })
                        .horizontalScroll(scrollState)
                ) {
                    Column(modifier = Modifier.width(with(density) { contentWidth.toDp() })) {
                        // Chart area
                        val primaryColor = MaterialTheme.colors.primary
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(density) { chartHeight.toDp() })
                        ) {
                            val gridColor = Color.Gray.copy(alpha = 0.15f)
                            for (i in 0..5) {
                                val y = size.height - (i.toFloat() / 5) * size.height
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            val fullWidthPerPeriod = size.width / periods.size
                            val groupWidth = fullWidthPerPeriod * 0.75f
                            val groupSpacing = fullWidthPerPeriod * 0.25f
                            
                            periods.indices.forEach { i ->
                                if (showTotal) {
                                    val totalAmount = allMembers.sumOf { memberSpendingByPeriod[it.id]?.get(i) ?: 0.0 }
                                    if (totalAmount > 0) {
                                        val barHeight = (totalAmount.toFloat() / maxSpendingInPeriod.toFloat()) * size.height
                                        val x = i * fullWidthPerPeriod + groupSpacing / 2
                                        drawRoundRect(
                                            color = primaryColor.copy(alpha = 0.8f),
                                            topLeft = Offset(x, size.height - barHeight),
                                            size = Size(groupWidth, barHeight),
                                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                        )
                                    }
                                } else {
                                    val activeMembers = allMembers.filter { (memberSpendingByPeriod[it.id]?.get(i) ?: 0.0) > 0 }
                                    if (activeMembers.isNotEmpty()) {
                                        val memberBarWidth = groupWidth / allMembers.size
                                        val groupStartX = i * fullWidthPerPeriod + groupSpacing / 2
                                        val totalActiveWidth = activeMembers.size * memberBarWidth
                                        val centeringOffset = (groupWidth - totalActiveWidth) / 2

                                        activeMembers.forEachIndexed { activeIndex, member ->
                                            val amount = memberSpendingByPeriod[member.id]!![i]
                                            val barHeight = (amount.toFloat() / maxSpendingInPeriod.toFloat()) * size.height
                                            val x = groupStartX + centeringOffset + activeIndex * memberBarWidth
                                            
                                            drawRoundRect(
                                                color = Color(android.graphics.Color.rgb(member.r ?: 0, member.g ?: 0, member.b ?: 0)),
                                                topLeft = Offset(x, size.height - barHeight),
                                                size = Size(memberBarWidth * 0.85f, barHeight),
                                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }

                            if (showMovingAverage && periods.isNotEmpty()) {
                                val linePath = Path()
                                trendLine.forEachIndexed { i, value ->
                                    val x = i * fullWidthPerPeriod + fullWidthPerPeriod / 2f
                                    val y = size.height - (value.toFloat() / maxSpendingInPeriod.toFloat()) * size.height
                                    
                                    if (i == 0) {
                                        linePath.moveTo(x, y)
                                    } else {
                                        val prevX = (i - 1) * fullWidthPerPeriod + fullWidthPerPeriod / 2f
                                        val prevY = size.height - (trendLine[i-1].toFloat() / maxSpendingInPeriod.toFloat()) * size.height
                                        linePath.cubicTo(
                                            prevX + (x - prevX) / 2f, prevY,
                                            prevX + (x - prevX) / 2f, y,
                                            x, y
                                        )
                                    }
                                }
                                drawPath(
                                    path = linePath,
                                    color = primaryColor,
                                    style = Stroke(
                                        width = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth().height(xAxisHeight)) {
                            val locale = LocalLocale.current.platformLocale
                            val labelIndices = when(timeView) {
                                SpendingTimeView.YEARLY -> periods.indices.toList()
                                SpendingTimeView.MONTHLY -> periods.indices.filter { it % 2 == 0 || it == periods.size - 1 }
                                SpendingTimeView.WEEKLY -> periods.indices.filter { it % 4 == 0 || it == periods.size - 1 }
                            }

                            labelIndices.forEach { index ->
                                val timestamp = periods[index]
                                val cal = Calendar.getInstance().apply { timeInMillis = timestamp * 1000 }
                                val dateStr = when (timeView) {
                                    SpendingTimeView.WEEKLY -> {
                                        val week = cal.get(Calendar.WEEK_OF_YEAR)
                                        val year = cal.get(Calendar.YEAR) % 100
                                        String.format(Locale.ROOT, "%02d/%02d", week, year)
                                    }
                                    SpendingTimeView.MONTHLY -> SimpleDateFormat("MM/yy", locale).format(Date(timestamp * 1000))
                                    SpendingTimeView.YEARLY -> "${cal.get(Calendar.YEAR)}"
                                }
                                val fullWidthPerPeriod = contentWidth / periods.size
                                val xPosition = index * fullWidthPerPeriod + (fullWidthPerPeriod / 2)
                                Text(
                                    text = dateStr,
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .offset(x = with(density) { (xPosition - 40.dp.toPx()).toDp() })
                                        .width(80.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.05f), MaterialTheme.shapes.medium)
                    .border(1.dp, Color.Black.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    SpendingTimeView.entries.forEach { view ->
                        val isSelected = timeView == view
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) MaterialTheme.colors.primary else Color.Transparent,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .clickable { timeView = view }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = view.label,
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colors.onPrimary else Color.Gray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showMovingAverage = !showMovingAverage }) {
                    Icon(
                        if (showMovingAverage) Icons.Default.Timeline else Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = "Trend",
                        tint = if (showMovingAverage) MaterialTheme.colors.primary else Color.Gray
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text("Total", style = MaterialTheme.typography.caption, color = Color.Gray)
                Switch(
                    checked = showTotal,
                    onCheckedChange = { showTotal = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (!showTotal) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp)
                    .verticalScroll(scrollState)
            ) {
                allMembers.chunked(3).forEach { rowMembers ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowMembers.forEach { member ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(bottom = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            Color(android.graphics.Color.rgb(member.r ?: 0, member.g ?: 0, member.b ?: 0)),
                                            shape = MaterialTheme.shapes.small
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(member.name, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        if (rowMembers.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun ProjectSpendingGraphPreview() {
    MaterialTheme {
        ProjectSpendingGraph(
            projectName = "Test Project",
            allMembers = StatisticsMockData.members,
            allBills = StatisticsMockData.bills,
            onShareReady = {}
        )
    }
}
