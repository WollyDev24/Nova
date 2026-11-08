package com.livewall.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.livewall.app.ui.feedback
import com.livewall.app.ui.theme.fullRoundedShape
import com.livewall.app.ui.theme.springDefaultEffects
import com.livewall.app.ui.theme.springDefaultSpatial
import kotlin.math.abs
import kotlin.math.roundToInt

data class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Expressive floating pill navigation bar with horizontal drag gesture tracking,
 * spring-based sliding indicator, and haptic feedback.
 */
@Composable
fun FloatingNavigationBar(
    destinations: List<NavDestination>,
    currentTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentTabState by rememberUpdatedState(currentTab)
    val onSelectState by rememberUpdatedState(onSelect)

    var itemRects by remember { mutableStateOf<Map<Int, IntRect>>(emptyMap()) }
    var dragX by remember { mutableStateOf<Float?>(null) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var lastDragHapticIndex by remember { mutableStateOf<Int?>(null) }

    fun nearestIndex(x: Float): Int? =
        itemRects.minByOrNull { (_, r) -> abs((r.left + r.width / 2f) - x) }?.key

    // When actively dragging, follow dragIndex; otherwise always reliably follow currentTab
    val highlightIndex = if (dragX != null) (dragIndex ?: currentTab) else currentTab

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .shadow(10.dp, fullRoundedShape()),
            shape = fullRoundedShape(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                val currentRect = itemRects[highlightIndex]
                val minLeftPx = itemRects.values.minOfOrNull { it.left }?.toFloat() ?: 0f
                val maxRightPx = itemRects.values.maxOfOrNull { it.right }?.toFloat() ?: 0f
                val pillSpring: SpringSpec<Float> = springDefaultSpatial()

                val pillWidthPx by animateFloatAsState(
                    targetValue = (currentRect?.width ?: 0).toFloat(),
                    animationSpec = pillSpring,
                    label = "pill_width"
                )
                val pillHeightPx by animateFloatAsState(
                    targetValue = (currentRect?.height ?: 0).toFloat(),
                    animationSpec = pillSpring,
                    label = "pill_height"
                )
                val pillLeftPx by animateFloatAsState(
                    targetValue = dragX?.let {
                        (it - pillWidthPx / 2f).coerceIn(minLeftPx, (maxRightPx - pillWidthPx).coerceAtLeast(minLeftPx))
                    } ?: (currentRect?.left ?: 0).toFloat(),
                    animationSpec = pillSpring,
                    label = "pill_left"
                )
                val pillTopPx by animateFloatAsState(
                    targetValue = (currentRect?.top ?: 0).toFloat(),
                    animationSpec = pillSpring,
                    label = "pill_top"
                )

                // Sliding Active Indicator Pill
                Box(
                    modifier = Modifier
                        .offset { IntOffset(pillLeftPx.roundToInt(), pillTopPx.roundToInt()) }
                        .size(with(density) { pillWidthPx.toDp() }, with(density) { pillHeightPx.toDp() })
                        .clip(fullRoundedShape())
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )

                // Navigation Items Row with Drag Detection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    dragX = offset.x
                                    val index = nearestIndex(offset.x)
                                    dragIndex = index
                                    lastDragHapticIndex = index
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    dragX = change.position.x
                                    val index = nearestIndex(change.position.x)
                                    if (index != null && index != lastDragHapticIndex) {
                                        lastDragHapticIndex = index
                                        haptics.feedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    dragIndex = index
                                },
                                onDragEnd = {
                                    val finalX = dragX
                                    val finalIndex = finalX?.let { nearestIndex(it) } ?: currentTabState
                                    if (finalIndex != currentTabState) {
                                        haptics.feedback(HapticFeedbackType.LongPress)
                                        onSelectState(finalIndex)
                                    }
                                    dragX = null
                                    dragIndex = null
                                    lastDragHapticIndex = null
                                },
                                onDragCancel = {
                                    dragX = null
                                    dragIndex = null
                                    lastDragHapticIndex = null
                                }
                            )
                        }
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val selected = highlightIndex == index
                        val iconTint by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = springDefaultEffects(),
                            label = "floating_nav_icon_tint"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(fullRoundedShape())
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    dragX = null
                                    dragIndex = null
                                    lastDragHapticIndex = null
                                    haptics.feedback(HapticFeedbackType.LongPress)
                                    onSelectState(index)
                                }
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInParent()
                                    val rect = IntRect(
                                        bounds.left.toInt(),
                                        bounds.top.toInt(),
                                        bounds.right.toInt(),
                                        bounds.bottom.toInt()
                                    )
                                    if (itemRects[index] != rect) {
                                        itemRects = itemRects + (index to rect)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
