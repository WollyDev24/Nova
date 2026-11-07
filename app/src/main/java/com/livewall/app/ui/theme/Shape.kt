package com.livewall.app.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

const val FullCornerPercent: Int = 50

fun fullRoundedShape(): CornerBasedShape =
    RoundedCornerShape(
        topStart = CornerSize(FullCornerPercent),
        topEnd = CornerSize(FullCornerPercent),
        bottomStart = CornerSize(FullCornerPercent),
        bottomEnd = CornerSize(FullCornerPercent),
    )

val ShapeCard = RoundedCornerShape(24.dp)
val ShapeDialog = RoundedCornerShape(28.dp)
val ShapeBottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
val ShapePill = fullRoundedShape()
val ShapeChip = RoundedCornerShape(12.dp)
