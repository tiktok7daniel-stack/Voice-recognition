package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun accentBrush(): Brush {
    val primary = MaterialTheme.colorScheme.primary
    return Brush.horizontalGradient(listOf(primary, lerp(primary, Color.Black, 0.45f)))
}

/** Primary action. Only one per card. */
@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .neonGlow(shape, colors.primary, if (enabled) 0.25f else 0f, 10.dp)
            .clip(shape)
            .background(accentBrush())
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
    }
}

/** Secondary action: plain glass. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .glass(RoundedCornerShape(16.dp), alpha = 0.35f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon, contentDescription = null,
                tint = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.55f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable
fun IconOrb(icon: ImageVector, active: Boolean = false, size: Int = 42, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size.dp)
            .then(
                if (active) {
                    Modifier.neonGlow(CircleShape, colors.primary, 0.35f, 10.dp).clip(CircleShape).background(accentBrush())
                } else {
                    Modifier.glass(RoundedCornerShape((size / 3).dp), alpha = 0.45f)
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (active) Color.White else lerp(colors.primary, Color.White, 0.55f),
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
fun TopBar(title: String, subtitle: String? = null, leading: @Composable () -> Unit = {}, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), blurRadius = 22f),
                ),
                fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
        actions()
    }
}

@Composable
fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = Color.White.copy(alpha = if (selected) 1f else 0.75f),
        maxLines = 1,
        modifier = Modifier
            .alpha(if (enabled || selected) 1f else 0.5f)
            .then(
                if (selected) Modifier.neonGlow(shape, MaterialTheme.colorScheme.primary, 0.2f, 8.dp).clip(shape).background(accentBrush())
                else Modifier.glass(shape, alpha = 0.35f),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(4.dp).height(if (subtitle != null) 38.dp else 24.dp)
                .neonGlow(RoundedCornerShape(2.dp), colors.primary, 0.3f, 6.dp)
                .background(Brush.verticalGradient(listOf(colors.primary, colors.primary.copy(alpha = 0.2f))), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun GlowBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(6.dp).background(
                Brush.horizontalGradient(listOf(lerp(colors.primary, Color.Black, 0.3f), colors.primary, Color.White)),
                RoundedCornerShape(50),
            ),
        )
    }
}

/** Icon in a glowing circle. Color carries meaning: Mint ok, Amber attention, Ember working. */
@Composable
fun GlowIcon(icon: ImageVector, color: Color, size: Int = 54, pulse: Boolean = false) {
    val glow = if (pulse) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0.18f, targetValue = 0.42f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glowValue",
        )
    } else {
        null
    }
    Box(
        modifier = Modifier.size(size.dp)
            .neonGlow(CircleShape, color, { glow?.value ?: 0.22f }, 12.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.12f))))
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp))
    }
}
