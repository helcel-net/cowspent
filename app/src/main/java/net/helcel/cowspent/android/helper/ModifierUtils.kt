package net.helcel.cowspent.android.helper

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color = Color.Gray,
    alpha: Float = 0.6f
): Modifier = drawWithContent {
    drawContent()
    val maxValue = state.maxValue.toFloat()
    if (maxValue > 0) {
        val viewPortHeight = size.height
        val totalContentHeight = maxValue + viewPortHeight
        val scrollBarHeight = (viewPortHeight * viewPortHeight / totalContentHeight).coerceAtLeast(24.dp.toPx())
        val scrollBarOffset = (state.value.toFloat() / maxValue) * (viewPortHeight - scrollBarHeight)

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollBarOffset),
            size = Size(width.toPx(), scrollBarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2),
            alpha = alpha
        )
    }
}

fun Modifier.lazyVerticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = Color.Gray,
    alpha: Float = 0.6f
): Modifier = drawWithContent {
    drawContent()
    val layoutInfo = state.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isNotEmpty()) {
        val totalItemsCount = layoutInfo.totalItemsCount
        val firstVisibleItem = visibleItemsInfo.first()

        val viewPortHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val avgItemHeight = visibleItemsInfo.map { it.size }.average().toFloat()
        val totalContentHeight = avgItemHeight * totalItemsCount

        if (totalContentHeight > viewPortHeight) {
            val scrollBarHeight = (viewPortHeight.toFloat() * viewPortHeight / totalContentHeight).coerceAtLeast(24.dp.toPx())
            val firstItemOffset = firstVisibleItem.offset
            val scrollOffset = firstVisibleItem.index * avgItemHeight - firstItemOffset
            val scrollBarOffset = (scrollOffset / (totalContentHeight - viewPortHeight)) * (viewPortHeight - scrollBarHeight)

            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollBarOffset),
                size = Size(width.toPx(), scrollBarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2),
                alpha = alpha
            )
        }
    }
}
