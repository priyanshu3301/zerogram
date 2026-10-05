package com.zerogram.core.ui.scroll

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Premium interactive fast-scroller overlay:
 *  - Draggable thumb mapped to exact list scroll position.
 *  - Up/Down affordance arrows visible during drag.
 *  - Optional floating letter-bubble label during drag.
 *  - Auto-hides after [autoHideDelayMs] of scroll inactivity.
 *
 * Usage:
 *   Box(Modifier.fillMaxSize()) {
 *       val listState = rememberLazyListState()
 *       LazyColumn(state = listState) { ... }
 *       FastScroller(listState = listState)
 *   }
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = 4.dp,
    thumbColor: Color = Color(0xFF64B5F6),
    thumbActiveColor: Color = Color(0xFF1E88E5),
    trackColor: Color = Color(0x33FFFFFF),
    autoHideDelayMs: Long = 1500L,
    labelProvider: ((Int) -> String)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val layoutInfo = listState.layoutInfo

    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var showScroller by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            showScroller = true
        } else if (!isDragging) {
            delay(autoHideDelayMs)
            showScroller = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (showScroller || isDragging) 1f else 0f,
        animationSpec = tween(if (showScroller || isDragging) 150 else 600),
        label = "FastScrollerAlpha"
    )

    // thumb position [0..1]
    val thumbPositionFraction = remember(layoutInfo) {
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0 || layoutInfo.visibleItemsInfo.isEmpty()) return@remember 0f
        val firstVisible = layoutInfo.visibleItemsInfo.first()
        val avgItemSize = layoutInfo.visibleItemsInfo.map { it.size }.average().toFloat()
        val totalContentHeight = totalItems * avgItemSize
        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
        if (totalContentHeight <= viewportHeight) return@remember 0f
        val scrollOffset = firstVisible.index * avgItemSize - firstVisible.offset.toFloat()
        (scrollOffset / (totalContentHeight - viewportHeight)).coerceIn(0f, 1f)
    }

    // thumb height as fraction of viewport/total
    val thumbHeightFraction = remember(layoutInfo) {
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) return@remember 1f
        (layoutInfo.visibleItemsInfo.size.toFloat() / totalItems).coerceIn(0.05f, 1f)
    }

    if (alpha == 0f && !isDragging) return

    Box(modifier = modifier.fillMaxSize().alpha(alpha)) {
        // ── Touch target + track ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(thumbWidth + 12.dp)
                .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true; showScroller = true },
                        onDragEnd = {
                            isDragging = false
                            coroutineScope.launch { delay(autoHideDelayMs); showScroller = false }
                        },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { _, dragAmount ->
                            val totalItems = layoutInfo.totalItemsCount
                            if (trackHeightPx > 0 && totalItems > 0) {
                                val newFraction = (thumbPositionFraction + dragAmount / trackHeightPx)
                                val targetIndex = (newFraction * totalItems)
                                    .roundToInt().coerceIn(0, totalItems - 1)
                                coroutineScope.launch { listState.scrollToItem(targetIndex) }
                            }
                        }
                    )
                }
        ) {
            // Track bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(thumbWidth)
                    .clip(RoundedCornerShape(thumbWidth / 2))
                    .background(trackColor)
            )

            // Thumb pill
            val thumbTopFraction = thumbPositionFraction * (1f - thumbHeightFraction)
            val thumbTopDp = with(density) { (thumbTopFraction * trackHeightPx).toDp() }
            val thumbHeightDp = with(density) { (thumbHeightFraction * trackHeightPx).toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = thumbTopDp)
                    .width(if (isDragging) thumbWidth + 4.dp else thumbWidth)
                    .height(thumbHeightDp.coerceAtLeast(40.dp))
                    .clip(RoundedCornerShape(thumbWidth))
                    .background(if (isDragging) thumbActiveColor else thumbColor)
            )

            // Up/down affordance arrows (drag only)
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = (thumbTopDp - 20.dp).coerceAtLeast(0.dp))
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = thumbActiveColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = thumbTopDp + thumbHeightDp + 4.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = thumbActiveColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ── Floating label bubble ───────────────────────────────────────────
        if (isDragging && labelProvider != null && layoutInfo.totalItemsCount > 0) {
            val currentIndex = (thumbPositionFraction * layoutInfo.totalItemsCount)
                .roundToInt().coerceIn(0, layoutInfo.totalItemsCount - 1)
            val bubbleTopDp = with(density) { (thumbPositionFraction * trackHeightPx).toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(thumbWidth + 16.dp), y = (bubbleTopDp - 18.dp).coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(thumbActiveColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = labelProvider(currentIndex), color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
