package studio.vela.art.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import studio.vela.art.ui.theme.VelaTheme

/** Bento-style surface card: the primary content container of the gallery/settings surfaces. */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    padding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val shape = RoundedCornerShape(dimens.cornerLarge)
    val base = Modifier
        .clip(shape)
        .background(if (highlight) colors.surface2 else colors.surface)
        .border(
            BorderStroke(1.dp, if (highlight) colors.accent.copy(alpha = 0.35f) else colors.border),
            shape,
        )
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(
        modifier = modifier
            .then(clickable)
            .padding(padding ?: PaddingValues(dimens.cardPadding)),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = VelaTheme.colors.textPrimary,
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun LabelText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = VelaTheme.colors.textSecondary,
    )
}

/**
 * Rounded icon button with an explicit selected state. Selection is conveyed by fill *and*
 * border, not colour alone.
 */
@Composable
fun VelaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    accentSelected: Boolean = true,
    containerColor: Color? = null,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surface.copy(alpha = 0.4f)
            selected && accentSelected -> colors.accent
            selected -> colors.surface3
            else -> containerColor ?: colors.surface2
        },
        label = "iconButtonBackground",
    )
    val tint = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.4f)
        selected && accentSelected -> colors.onAccent
        selected -> colors.textPrimary
        else -> colors.textPrimary
    }
    val shape = RoundedCornerShape(dimens.cornerMedium)
    Box(
        modifier = modifier
            .size(dimens.touchTarget)
            .clip(shape)
            .background(bg)
            .then(
                if (selected) Modifier.border(1.dp, colors.accent.copy(alpha = 0.6f), shape) else Modifier,
            )
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(dimens.iconSize),
        )
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val shape = RoundedCornerShape(dimens.cornerSmall + 4.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accentMuted else colors.surface2)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) leading()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Labelled slider used for every numeric brush/layer parameter. */
@Composable
fun VelaSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    valueText: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = VelaTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = valueText ?: formatValue(value),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surface3,
                disabledThumbColor = colors.textSecondary,
            ),
        )
    }
}

private fun formatValue(value: Float): String = when {
    value == value.toInt().toFloat() -> value.toInt().toString()
    else -> String.format(java.util.Locale.US, "%.2f", value)
}

@Composable
fun VelaSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VelaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surface3,
            ),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = VelaTheme.colors
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            fontWeight = FontWeight.Normal,
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun ColorSwatch(color: Color, size: androidx.compose.ui.unit.Dp = 28.dp, selected: Boolean = false) {
    val colors = VelaTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(
                2.dp,
                if (selected) colors.accent else colors.border,
                RoundedCornerShape(8.dp),
            ),
    )
}

@Composable
fun VerticalDivider(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 24.dp) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(height)
            .background(VelaTheme.colors.border),
    )
}
