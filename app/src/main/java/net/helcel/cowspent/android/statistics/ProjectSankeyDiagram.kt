package net.helcel.cowspent.android.statistics

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.*
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBMember
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterialApi::class)
@SuppressLint("UseKtx")
@Composable
fun ProjectSankeyDiagram(
    projectName: String,
    allMembers: List<DBMember>,
    allBills: List<DBBill>,
    customCategories: List<DBCategory>,
    onShareReady: (String) -> Unit
) {
    val shareStatsIntro = stringResource(R.string.share_stats_intro, projectName)

    var selectedMemberId by remember { mutableLongStateOf(-1L) }
    var expanded by remember { mutableStateOf(false) }

    val activeBills = remember(allBills) {
        allBills.filter { 
            it.state != DBBill.STATE_DELETED && 
            it.categoryRemoteId != DBBill.CATEGORY_REIMBURSEMENT 
        }
    }

    val membersMap = remember(allMembers) { allMembers.associateBy { it.id } }
    val categoriesMap = remember(customCategories) { customCategories.associateBy { it.remoteId.toInt() } }

    if (activeBills.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data to display", style = MaterialTheme.typography.h6)
        }
        return
    }

    // Consolidated spending calculation
    val spendings = remember(activeBills, selectedMemberId, membersMap) {
        val spentMap = mutableMapOf<Long, Double>()
        val catMap = mutableMapOf<Int, Double>()
        membersMap.keys.forEach { spentMap[it] = 0.0 }
        
        activeBills.forEach { bill ->
            val totalWeight = bill.billOwers.sumOf { bo -> 
                membersMap[bo.memberId]?.weight ?: 1.0 
            }
            if (totalWeight > 0) {
                if (selectedMemberId == -1L) {
                    catMap[bill.categoryRemoteId] = (catMap[bill.categoryRemoteId] ?: 0.0) + bill.amount
                    bill.billOwers.forEach { bo ->
                        val weight = membersMap[bo.memberId]?.weight ?: 1.0
                        val share = (bill.amount / totalWeight) * weight
                        spentMap[bo.memberId] = (spentMap[bo.memberId] ?: 0.0) + share
                    }
                } else {
                    bill.billOwers.find { it.memberId == selectedMemberId }?.let { bo ->
                        val weight = membersMap[bo.memberId]?.weight ?: 1.0
                        val share = (bill.amount / totalWeight) * weight
                        catMap[bill.categoryRemoteId] = (catMap[bill.categoryRemoteId] ?: 0.0) + share
                        spentMap[selectedMemberId] = (spentMap[selectedMemberId] ?: 0.0) + share
                    }
                }
            }
        }
        
        val memberList = spentMap.toList().filter { it.second > 0 }.sortedByDescending { it.second }
        val categoryList = catMap.toList().sortedByDescending { it.second }
        memberList to categoryList
    }

    val displayMemberSpendings = spendings.first
    val displayCategorySpendings = spendings.second
    val totalAmount = remember(displayMemberSpendings) { displayMemberSpendings.sumOf { it.second } }

    LaunchedEffect(displayMemberSpendings, displayCategorySpendings, totalAmount, projectName) {
        val statsText = StringBuilder()
        statsText.append("// ").append(shareStatsIntro.replace("\n", "\n// ")).append("\n\n")

        val middleNode = if (selectedMemberId == -1L) "Total" else "Spent"

        displayMemberSpendings.forEach { (memberId, amount) ->
            val name = membersMap[memberId]?.name ?: "???"
            statsText.append("$name [${formatShortValue(amount)}] $middleNode\n")
        }

        statsText.append("\n")

        displayCategorySpendings.forEach { (catRemoteId, amount) ->
            val name = categoriesMap[catRemoteId]?.name ?: "Other"
            statsText.append("$middleNode [${formatShortValue(amount)}] $name\n")
        }

        statsText.append("\n")

        displayMemberSpendings.forEach { (memberId, _) ->
            val member = membersMap[memberId]
            if (member != null) {
                val hexColor = String.format("#%02x%02x%02x", member.r ?: 128, member.g ?: 128, member.b ?: 128)
                statsText.append(":${member.name} $hexColor\n")
            }
        }

        displayCategorySpendings.forEach { (catRemoteId, _) ->
            val category = categoriesMap[catRemoteId]
            if (category != null) {
                statsText.append(":${category.name ?: "Other"} ${category.color}\n")
            }
        }

        onShareReady(statsText.toString())
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val selectedMember = membersMap[selectedMemberId]
        EditableExposedDropdownMenu(
            value = selectedMember?.name ?: "All Members",
            placeholder = "Filter by member",
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            leadingIcon = {
                Box(modifier = Modifier.padding(start = 12.dp)) {
                    if (selectedMember != null) {
                        UserAvatar(
                            name = selectedMember.name,
                            r = selectedMember.r,
                            g = selectedMember.g,
                            b = selectedMember.b,
                            disabled = !selectedMember.isActivated,
                            size = 24.dp
                        )
                    } else {
                        Icon(Icons.Default.Group, contentDescription = null)
                    }
                }
            },
            content = {
                DropdownMenuItem(onClick = {
                    selectedMemberId = -1L
                    expanded = false
                }) {
                    Icon(Icons.Default.Group, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("All Members")
                }
                allMembers.forEach { member ->
                    DropdownMenuItem(onClick = {
                        selectedMemberId = member.id
                        expanded = false
                    }) {
                        UserAvatar(
                            name = member.name,
                            r = member.r,
                            g = member.g,
                            b = member.b,
                            disabled = !member.isActivated,
                            size = 24.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(member.name)
                    }
                }
            }
        )

        if (totalAmount <= 0) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No spending data for selected filter", style = MaterialTheme.typography.body1)
            }
        } else {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val nodeHeightMember = 76.dp
                val nodeHeightCategory = 76.dp
                val nodeHeightTotal = 42.dp
                val horizontalGap = 8.dp

                val memberCount = displayMemberSpendings.size
                val categoryCount = displayCategorySpendings.size

                val maxGaps = maxOf(memberCount - 1, categoryCount - 1, 0)
                val usableWidth = (maxWidth.value - horizontalGap.value * maxGaps).coerceAtLeast(0f)
                val moneyScale = if (totalAmount > 0) usableWidth / totalAmount.toFloat() else 0f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val nodeHeightMemberPx = nodeHeightMember.toPx()
                    val nodeHeightCategoryPx = nodeHeightCategory.toPx()
                    val nodeHeightTotalPx = nodeHeightTotal.toPx()
                    val gapPx = horizontalGap.toPx()

                    val totalWidthPx = size.width
                    val usableWidthPx = totalWidthPx - gapPx * maxGaps
                    val moneyScalePx = if (totalAmount > 0) usableWidthPx / totalAmount else 0.0

                    val totalNodeWidthPx = (totalAmount * moneyScalePx).toFloat()
                    val totalNodeXPx = (totalWidthPx - totalNodeWidthPx) / 2

                    val topY = 0f
                    val middleY = (size.height - nodeHeightTotalPx) / 2
                    val bottomY = size.height - nodeHeightCategoryPx

                    val totalNodeColor = Color(0xFF333333)

                    // Flows
                    var currentXTop = (totalWidthPx - (totalAmount * moneyScalePx + (memberCount - 1).coerceAtLeast(0) * gapPx).toFloat()) / 2
                    var currentXInTotalTop = totalNodeXPx
                    displayMemberSpendings.forEach { (memberId, amount) ->
                        val member = membersMap[memberId]
                        val boxWidth = (amount * moneyScalePx).toFloat()
                        val color = member?.let {
                            Color(it.r ?: 128, it.g ?: 128, it.b ?: 128)
                        } ?: Color.Gray

                        if (boxWidth > 0.5f) {
                            drawSankeyFlow(
                                startX = currentXTop,
                                startY = topY + nodeHeightMemberPx * 0.5f,
                                startWidth = boxWidth,
                                endX = currentXInTotalTop,
                                endY = middleY + nodeHeightTotalPx * 0.5f,
                                endWidth = boxWidth,
                                startColor = color.copy(alpha = 0.5f),
                                endColor = totalNodeColor.copy(alpha = 0.35f)
                            )
                        }
                        currentXTop += boxWidth + gapPx
                        currentXInTotalTop += boxWidth
                    }

                    var currentXBottom = (totalWidthPx - (totalAmount * moneyScalePx + (categoryCount - 1).coerceAtLeast(0) * gapPx).toFloat()) / 2
                    var currentXInTotalBottom = totalNodeXPx
                    displayCategorySpendings.forEach { (catRemoteId, amount) ->
                        val category = categoriesMap[catRemoteId]
                        val boxWidth = (amount * moneyScalePx).toFloat()
                        val color = category?.color?.let {
                            try { Color(it.toColorInt()) } catch (_: Exception) { Color(0xFF999999) }
                        } ?: Color(0xFF999999)

                        if (boxWidth > 0.5f) {
                            drawSankeyFlow(
                                startX = currentXInTotalBottom,
                                startY = middleY + nodeHeightTotalPx * 0.5f,
                                startWidth = boxWidth,
                                endX = currentXBottom,
                                endY = bottomY + nodeHeightCategoryPx * 0.5f,
                                endWidth = boxWidth,
                                startColor = totalNodeColor.copy(alpha = 0.35f),
                                endColor = color.copy(alpha = 0.5f)
                            )
                        }
                        currentXInTotalBottom += boxWidth
                        currentXBottom += boxWidth + gapPx
                    }

                    // Nodes
                    currentXTop = (totalWidthPx - (totalAmount * moneyScalePx + (memberCount - 1).coerceAtLeast(0) * gapPx).toFloat()) / 2
                    displayMemberSpendings.forEach { (memberId, amount) ->
                        val member = membersMap[memberId]
                        val width = (amount * moneyScalePx).toFloat()
                        val color = member?.let {
                            Color(it.r ?: 128, it.g ?: 128, it.b ?: 128)
                        } ?: Color.Gray

                        if (width > 0.5f) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(currentXTop, topY),
                                size = Size(width, nodeHeightMemberPx),
                                cornerRadius = CornerRadius(10.dp.toPx())
                            )
                        }
                        currentXTop += width + gapPx
                    }

                    drawRect(
                        color = totalNodeColor,
                        topLeft = Offset(totalNodeXPx, middleY),
                        size = Size(totalNodeWidthPx, nodeHeightTotalPx)
                    )

                    currentXBottom = (totalWidthPx - (totalAmount * moneyScalePx + (categoryCount - 1).coerceAtLeast(0) * gapPx).toFloat()) / 2
                    displayCategorySpendings.forEach { (catRemoteId, amount) ->
                        val category = categoriesMap[catRemoteId]
                        val width = (amount * moneyScalePx).toFloat()
                        val color = category?.color?.let {
                            try { Color(it.toColorInt()) } catch (_: Exception) { Color(0xFF999999) }
                        } ?: Color(0xFF999999)
                        
                        if (width > 0.5f) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(currentXBottom, bottomY),
                                size = Size(width, nodeHeightCategoryPx),
                                cornerRadius = CornerRadius(10.dp.toPx())
                            )
                        }
                        currentXBottom += width + gapPx
                    }
                }

                // Member labels
                var currentXTopLabel = (maxWidth.value - (totalAmount.toFloat() * moneyScale + (memberCount - 1).coerceAtLeast(0) * horizontalGap.value)) / 2
                displayMemberSpendings.forEach { (memberId, amount) ->
                    val member = membersMap[memberId]
                    val widthValue = amount.toFloat() * moneyScale
                    val width = widthValue.dp
                    if (widthValue > 12f) {
                        Box(
                            modifier = Modifier
                                .offset(x = currentXTopLabel.dp, y = 0.dp)
                                .size(width, nodeHeightMember),
                            contentAlignment = Alignment.Center
                        ) {
                            if (width >= 36.dp) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = member?.name ?: "???",
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatShortValue(amount),
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                    currentXTopLabel += widthValue + horizontalGap.value
                }

                // Total label
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(nodeHeightTotal),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedMemberId == -1L) formatShortValue(totalAmount) else "SPENT: ${formatShortValue(totalAmount)}",
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Category labels
                var currentXBottomLabel = (maxWidth.value - (totalAmount.toFloat() * moneyScale + (categoryCount - 1).coerceAtLeast(0) * horizontalGap.value)) / 2
                displayCategorySpendings.forEach { (catRemoteId, amount) ->
                    val category = categoriesMap[catRemoteId]
                    val widthValue = amount.toFloat() * moneyScale
                    val width = widthValue.dp
                    if (widthValue > 12f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = currentXBottomLabel.dp)
                                .size(width, nodeHeightCategory),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = category?.icon ?: "❔",
                                    fontSize = 20.sp
                                )
                                if (width >= 32.dp) {
                                    Text(
                                        text = formatShortValue(amount),
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                    currentXBottomLabel += widthValue + horizontalGap.value
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(32.dp))
    }
}

private fun DrawScope.drawSankeyFlow(
    startX: Float,
    startY: Float,
    startWidth: Float,
    endX: Float,
    endY: Float,
    endWidth: Float,
    startColor: Color,
    endColor: Color
) {
    val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(
            startX, startY + (endY - startY) * 0.5f,
            endX, endY - (endY - startY) * 0.5f,
            endX, endY
        )
        lineTo(endX + endWidth, endY)
        cubicTo(
            endX + endWidth, endY - (endY - startY) * 0.5f,
            startX + startWidth, startY + (endY - startY) * 0.5f,
            startX + startWidth, startY
        )
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(startColor, endColor),
            startY = startY,
            endY = endY
        )
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ProjectSankeyDiagramPreview() {
    MaterialTheme {
        ProjectSankeyDiagram(
            projectName = "Test Project",
            allMembers = StatisticsMockData.members,
            allBills = StatisticsMockData.bills,
            customCategories = emptyList(),
            onShareReady = {}
        )
    }
}
