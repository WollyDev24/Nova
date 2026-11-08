package com.livewall.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import com.livewall.app.ui.theme.springDefaultSpatial
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Eliminates the sluggish system EdgeEffect delay when reaching scroll boundaries,
 * replacing it with an instant, physics-based kinetic spring bounce.
 */
@Composable
fun Modifier.instantBounceOverscroll(
    resistance: Float = 0.35f,
    maxOverscrollPx: Float = 140f
): Modifier {
    val overscrollOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = overscrollOffset.value
                // If currently stretched and dragging in reverse direction, consume towards 0 first
                if ((current < 0f && available.y > 0f) || (current > 0f && available.y < 0f)) {
                    val consumed = if (abs(available.y) >= abs(current)) -current else available.y
                    scope.launch {
                        overscrollOffset.snapTo(current + consumed)
                    }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Instantly absorb leftover scroll with zero delay
                if (available.y != 0f) {
                    val target = (overscrollOffset.value + available.y * resistance)
                        .coerceIn(-maxOverscrollPx, maxOverscrollPx)
                    scope.launch {
                        overscrollOffset.snapTo(target)
                    }
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (overscrollOffset.value != 0f) {
                    overscrollOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = springDefaultSpatial()
                    )
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y != 0f) {
                    // Instantly bounce on fling hit with zero delay
                    val initialVelocity = (available.y * 0.12f).coerceIn(-1000f, 1000f)
                    overscrollOffset.animateTo(
                        targetValue = 0f,
                        initialVelocity = initialVelocity,
                        animationSpec = springDefaultSpatial()
                    )
                } else if (overscrollOffset.value != 0f) {
                    overscrollOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = springDefaultSpatial()
                    )
                }
                return available
            }
        }
    }

    return this
        .nestedScroll(nestedScrollConnection)
        .offset { IntOffset(0, overscrollOffset.value.roundToInt()) }
}
