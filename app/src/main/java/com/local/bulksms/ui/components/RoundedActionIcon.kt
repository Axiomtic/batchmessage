package com.local.bulksms.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class RoundedActionKind { ADD, REMOVE }

@Composable
fun RoundedActionIcon(
    kind: RoundedActionKind,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val foreground = when (kind) {
        RoundedActionKind.ADD -> MaterialTheme.colorScheme.primary
        RoundedActionKind.REMOVE -> MaterialTheme.colorScheme.error
    }
    val pressedBackground = foreground.copy(alpha = 0.18f)
    val background by animateColorAsState(
        targetValue = if (pressed && enabled) pressedBackground else Color.Transparent,
        animationSpec = if (pressed) snap() else tween(850),
        label = "rounded-action-background",
    )
    val description = if (kind == RoundedActionKind.ADD) "添加模板" else "删除模板"

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) { drawCircle(background) }
        Canvas(Modifier.size(14.dp)) {
            drawRoundedGlyph(kind, foreground.copy(alpha = if (enabled) 1f else 0.35f))
        }
    }
}

/**
 * A borderless round action button carrying an icon, sharing the look of the
 * template +/- buttons ([RoundedActionIcon]) so the export/share actions in the
 * data table and the history detail match them.
 */
@Composable
fun RoundedIconAction(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedBackground = tint.copy(alpha = 0.18f)
    val background by animateColorAsState(
        targetValue = if (pressed && enabled) pressedBackground else Color.Transparent,
        animationSpec = if (pressed) snap() else tween(850),
        label = "rounded-icon-action-background",
    )
    val description = contentDescription

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics { this.contentDescription = description }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) { drawCircle(background) }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun DrawScope.drawRoundedGlyph(kind: RoundedActionKind, color: Color) {
    val stroke = 3.dp.toPx()
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
        end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    if (kind == RoundedActionKind.ADD) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width / 2, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width / 2, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
