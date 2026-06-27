package net.helcel.cowspent.android.statistics

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch
import net.helcel.cowspent.R
import net.helcel.cowspent.android.helper.EditableExposedDropdownMenu
import net.helcel.cowspent.android.helper.MemberAvatar
import net.helcel.cowspent.android.helper.formatShortValue
import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBCategory
import net.helcel.cowspent.model.DBMember
import kotlin.math.abs
import kotlin.math.roundToInt

private object SankeyDimens {
    val NodeHeight = 76.dp
    val TotalNodeHeight = 42.dp
    val NormalGap = 8.dp
    val FocusGap = 16.dp
    val ActiveMinWidth = 160.dp
}

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
        allBills.filter { it.state != DBBill.STATE_DELETED && it.categoryRemoteId != DBBill.CATEGORY_REIMBURSEMENT }
    }

    val membersMap = remember(allMembers) { allMembers.associateBy { it.id } }
    val categoriesMap = remember(customCategories) { customCategories.associateBy { it.remoteId.toInt() } }

    if (activeBills.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data to display", style = MaterialTheme.typography.h6)
        }
        return
    }

    val spendings = remember(activeBills, selectedMemberId, membersMap) {
        val spentMap = mutableMapOf<Long, Double>().apply { membersMap.keys.forEach { put(it, 0.0) } }
        val catMap = mutableMapOf<Int, Double>()
        
        activeBills.forEach { bill ->
            val totalWeight = bill.billOwers.sumOf { membersMap[it.memberId]?.weight ?: 1.0 }
            if (totalWeight > 0) {
                if (selectedMemberId == -1L) {
                    catMap[bill.categoryRemoteId] = (catMap[bill.categoryRemoteId] ?: 0.0) + bill.amount
                    bill.billOwers.forEach { bo ->
                        val weight = membersMap[bo.memberId]?.weight ?: 1.0
                        spentMap[bo.memberId] = (spentMap[bo.memberId] ?: 0.0) + (bill.amount / totalWeight) * weight
                    }
                } else {
                    bill.billOwers.find { it.memberId == selectedMemberId }?.let { bo ->
                        val weight = membersMap[bo.memberId]?.weight ?: 1.0
                        catMap[bill.categoryRemoteId] = (catMap[bill.categoryRemoteId] ?: 0.0) + (bill.amount / totalWeight) * weight
                        spentMap[selectedMemberId] = (spentMap[selectedMemberId] ?: 0.0) + (bill.amount / totalWeight) * weight
                    }
                }
            }
        }
        spentMap.toList().filter { it.second > 0 }.sortedByDescending { it.second } to catMap.toList().sortedByDescending { it.second }
    }

    val displayMemberSpendings = spendings.first
    val displayCategorySpendings = spendings.second
    val totalAmount = remember(displayMemberSpendings) { displayMemberSpendings.sumOf { it.second } }
    var isSpecialMode by remember { mutableStateOf(false) }

    LaunchedEffect(displayMemberSpendings, displayCategorySpendings, totalAmount, projectName) {
        val statsText = StringBuilder().apply {
            append("// ").append(shareStatsIntro.replace("\n", "\n// ")).append("\n\n")
            val middleNode = if (selectedMemberId == -1L) "Total" else "Spent"
            displayMemberSpendings.forEach { (id, amount) -> append("${membersMap[id]?.name ?: "???"} [${formatShortValue(amount)}] $middleNode\n") }
            append("\n")
            displayCategorySpendings.forEach { (id, amount) -> append("$middleNode [${formatShortValue(amount)}] ${categoriesMap[id]?.name ?: "Other"}\n") }
            append("\n")
            displayMemberSpendings.forEach { (id, _) ->
                membersMap[id]?.let { append(":${it.name} ${String.format("#%02x%02x%02x", it.r ?: 128, it.g ?: 128, it.b ?: 128)}\n") }
            }
            displayCategorySpendings.forEach { (id, _) -> categoriesMap[id]?.let { append(":${it.name ?: "Other"} ${it.color}\n") } }
        }
        onShareReady(statsText.toString())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(16.dp)) {
            val selectedMember = membersMap[selectedMemberId]
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditableExposedDropdownMenu(
                    value = selectedMember?.name ?: "All Members",
                    placeholder = "Filter by member",
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.weight(1f).padding(bottom = 16.dp),
                    leadingIcon = {
                        Box(modifier = Modifier.padding(start = 12.dp)) {
                            if (selectedMember != null) {
                                MemberAvatar(member = selectedMember, size = 24.dp)
                            } else Icon(Icons.Default.Group, contentDescription = null)
                        }
                    },
                    content = {
                        DropdownMenuItem(onClick = { selectedMemberId = -1L; expanded = false }) {
                            Icon(Icons.Default.Group, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("All Members")
                        }
                        allMembers.forEach { member ->
                            DropdownMenuItem(onClick = { selectedMemberId = member.id; expanded = false }) {
                                MemberAvatar(member = member, size = 24.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(member.name)
                            }
                        }
                    }
                )
                IconButton(onClick = { isSpecialMode = !isSpecialMode }, modifier = Modifier.padding(bottom = 16.dp)) {
                    Icon(Icons.Default.ViewColumn, contentDescription = "Focus Mode", tint = if (isSpecialMode) MaterialTheme.colors.primary else LocalContentColor.current)
                }
            }
        }

        if (totalAmount <= 0) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No spending data for selected filter", style = MaterialTheme.typography.body1)
            }
        } else {
            SankeyContent(isSpecialMode, selectedMemberId, totalAmount, displayMemberSpendings, displayCategorySpendings, membersMap, categoriesMap)
        }
        Spacer(Modifier.fillMaxWidth().height(32.dp))
    }
}

private data class NodeLayout(val centerX: Float, val visualWidth: Float)

@SuppressLint("FrequentlyChangingValue")
@Composable
private fun SankeyContent(
    isSpecialMode: Boolean,
    selectedMemberId: Long,
    totalAmount: Double,
    displayMemberSpendings: List<Pair<Long, Double>>,
    displayCategorySpendings: List<Pair<Int, Double>>,
    membersMap: Map<Long, DBMember>,
    categoriesMap: Map<Int, DBCategory>
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    val topFocalIndex = remember { Animatable(0f) }
    val bottomFocalIndex = remember { Animatable(0f) }

    var diagramAreaCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var totalNodeCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(isSpecialMode) {
        if (!isSpecialMode) {
            topFocalIndex.snapTo(0f)
            bottomFocalIndex.snapTo(0f)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().onGloballyPositioned { diagramAreaCoordinates = it }) {
        val totalMaxWidthPx = with(density) { maxWidth.toPx() }
        val normalGapPx = with(density) { SankeyDimens.NormalGap.toPx() }
        val focusGapPx = with(density) { SankeyDimens.FocusGap.toPx() }
        val activeMinPx = with(density) { SankeyDimens.ActiveMinWidth.toPx() }

        val moneyScalePx = if (totalAmount > 0) (totalMaxWidthPx - (maxOf(displayMemberSpendings.size, displayCategorySpendings.size) - 1) * normalGapPx).coerceAtLeast(0f) / totalAmount else 0.0

        fun calculateRowLayout(spendings: List<Pair<*, Double>>, focalIndex: Float): List<NodeLayout> {
            val visualWidths = spendings.mapIndexed { i, (_, amount) ->
                val propWidthPx = (amount * moneyScalePx).toFloat()
                if (!isSpecialMode) propWidthPx
                else {
                    val focusAmount = (1f - abs(i - focalIndex)).coerceIn(0f, 1f)
                    maxOf(propWidthPx, activeMinPx * focusAmount)
                }
            }

            return if (!isSpecialMode) {
                val totalWidthPx = visualWidths.sum() + (spendings.size - 1).coerceAtLeast(0) * normalGapPx
                var currentX = (totalMaxWidthPx - totalWidthPx) / 2
                visualWidths.map { w ->
                    NodeLayout(currentX + w / 2, w).also { currentX += w + normalGapPx }
                }
            } else {
                val centers = mutableListOf<Float>()
                var currentX = 0f
                visualWidths.forEachIndexed { i, w ->
                    if (i > 0) currentX += visualWidths[i - 1] / 2 + focusGapPx + w / 2
                    else currentX = w / 2
                    centers.add(currentX)
                }
                
                val focalPointX = if (spendings.isEmpty()) 0f else {
                    val idx = focalIndex.coerceIn(0f, (spendings.size - 1).toFloat())
                    val low = idx.toInt()
                    val high = (low + 1).coerceAtMost(spendings.size - 1)
                    val fract = idx - low
                    centers[low] * (1 - fract) + centers[high] * fract
                }

                centers.mapIndexed { i, c ->
                    NodeLayout(totalMaxWidthPx / 2 + (c - focalPointX), visualWidths[i])
                }
            }
        }

        val topLayout = calculateRowLayout(displayMemberSpendings, topFocalIndex.value)
        val bottomLayout = calculateRowLayout(displayCategorySpendings, bottomFocalIndex.value)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val root = diagramAreaCoordinates ?: return@Canvas
            val totalRect = totalNodeCoordinates?.let {
                val pos = it.positionInRoot() - root.positionInRoot()
                Rect(pos.x, pos.y, pos.x + it.size.width, pos.y + it.size.height)
            } ?: return@Canvas
            val flowMoneyScalePx = totalRect.width / totalAmount
            val totalNodeColor = Color(0xFF333333)

            displayMemberSpendings.forEachIndexed { index, (_, amount) ->
                val flowWidth = (amount * flowMoneyScalePx).toFloat()
                val layout = topLayout[index]
                if (flowWidth > 0.5f) {
                    val color = membersMap[displayMemberSpendings[index].first]?.let { Color(it.r ?: 128, it.g ?: 128, it.b ?: 128) } ?: Color.Gray
                    drawSankeyFlow(layout.centerX - layout.visualWidth / 2, SankeyDimens.NodeHeight.toPx() / 2, layout.visualWidth, totalRect.left + displayMemberSpendings.take(index).sumOf { it.second * flowMoneyScalePx }.toFloat(), totalRect.top + totalRect.height / 2, flowWidth, color.copy(alpha = 0.5f), totalNodeColor.copy(alpha = 0.35f))
                }
            }
            displayCategorySpendings.forEachIndexed { index, (_, amount) ->
                val flowWidth = (amount * flowMoneyScalePx).toFloat()
                val layout = bottomLayout[index]
                if (flowWidth > 0.5f) {
                    val color = categoriesMap[displayCategorySpendings[index].first]?.color?.let { try { Color(it.toColorInt()) } catch (_: Exception) { Color(0xFF999999) } } ?: Color(0xFF999999)
                    drawSankeyFlow(totalRect.left + displayCategorySpendings.take(index).sumOf { it.second * flowMoneyScalePx }.toFloat(), totalRect.top + totalRect.height / 2, flowWidth, layout.centerX - layout.visualWidth / 2, size.height - SankeyDimens.NodeHeight.toPx() / 2, layout.visualWidth, totalNodeColor.copy(alpha = 0.35f), color.copy(alpha = 0.5f))
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // TOP Row
            Box(modifier = Modifier.fillMaxWidth().height(SankeyDimens.NodeHeight).then(if (isSpecialMode) Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        val currentIdx = topFocalIndex.value.roundToInt().coerceIn(displayMemberSpendings.indices)
                        val step = topLayout[currentIdx].visualWidth + focusGapPx
                        topFocalIndex.snapTo((topFocalIndex.value - delta / step).coerceIn(0f, (displayMemberSpendings.size - 1).toFloat()))
                    }
                },
                onDragStopped = {
                    topFocalIndex.animateTo(topFocalIndex.value.roundToInt().toFloat(), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
                }
            ) else Modifier)) {
                displayMemberSpendings.forEachIndexed { index, (id, amount) ->
                    val member = membersMap[id]
                    val layout = topLayout[index]
                    val wDp = with(density) { layout.visualWidth.toDp() }
                    val xDp = with(density) { (layout.centerX - layout.visualWidth / 2).toDp() }
                    
                    Box(modifier = Modifier.offset(x = xDp).width(wDp).fillMaxHeight()
                        .then(if (isSpecialMode) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { topFocalIndex.animateTo(index.toFloat(), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
                        } else Modifier), contentAlignment = Alignment.Center) {
                        Column(modifier = Modifier.fillMaxSize().background(color = member?.let { Color(it.r ?: 128, it.g ?: 128, it.b ?: 128) } ?: Color.Gray, shape = RoundedCornerShape(10.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(text = member?.name ?: "???", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.wrapContentWidth(unbounded = true))
                            if (wDp >= 40.dp) Text(text = formatShortValue(amount), fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(with(density) { (totalAmount * moneyScalePx).toFloat().toDp() }).height(SankeyDimens.TotalNodeHeight).background(Color(0xFF333333)).onGloballyPositioned { totalNodeCoordinates = it }, contentAlignment = Alignment.Center) {
                    Text(text = if (selectedMemberId == -1L) formatShortValue(totalAmount) else "SPENT: ${formatShortValue(totalAmount)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp), maxLines = 1)
                }
            }

            // BOTTOM Row
            Box(modifier = Modifier.fillMaxWidth().height(SankeyDimens.NodeHeight).then(if (isSpecialMode) Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        val currentIdx = bottomFocalIndex.value.roundToInt().coerceIn(displayCategorySpendings.indices)
                        val step = bottomLayout[currentIdx].visualWidth + focusGapPx
                        bottomFocalIndex.snapTo((bottomFocalIndex.value - delta / step).coerceIn(0f, (displayCategorySpendings.size - 1).toFloat()))
                    }
                },
                onDragStopped = {
                    bottomFocalIndex.animateTo(bottomFocalIndex.value.roundToInt().toFloat(), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
                }
            ) else Modifier)) {
                displayCategorySpendings.forEachIndexed { index, (id, amount) ->
                    val category = categoriesMap[id]
                    val layout = bottomLayout[index]
                    val wDp = with(density) { layout.visualWidth.toDp() }
                    val xDp = with(density) { (layout.centerX - layout.visualWidth / 2).toDp() }
                    
                    Box(modifier = Modifier.offset(x = xDp).width(wDp).fillMaxHeight()
                        .then(if (isSpecialMode) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { bottomFocalIndex.animateTo(index.toFloat(), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
                        } else Modifier), contentAlignment = Alignment.Center) {
                        Column(modifier = Modifier.fillMaxSize().background(color = category?.color?.let { try { Color(it.toColorInt()) } catch (_: Exception) { Color(0xFF999999) } } ?: Color(0xFF999999), shape = RoundedCornerShape(10.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(text = category?.icon ?: "❔", fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.wrapContentWidth(unbounded = true))
                            if (wDp >= 40.dp) Text(text = formatShortValue(amount), fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSankeyFlow(startX: Float, startY: Float, startWidth: Float, endX: Float, endY: Float, endWidth: Float, startColor: Color, endColor: Color) {
    val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(startX, startY + (endY - startY) * 0.5f, endX, endY - (endY - startY) * 0.5f, endX, endY)
        lineTo(endX + endWidth, endY)
        cubicTo(endX + endWidth, endY - (endY - startY) * 0.5f, startX + startWidth, startY + (endY - startY) * 0.5f, startX + startWidth, startY)
        close()
    }
    drawPath(path = path, brush = Brush.verticalGradient(colors = listOf(startColor, endColor), startY = startY, endY = endY))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ProjectSankeyDiagramPreview() = MaterialTheme { ProjectSankeyDiagram("Test Project", StatisticsMockData.members, StatisticsMockData.bills, emptyList()) {} }
