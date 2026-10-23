package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun accentBrush(): Brush = Brush.horizontalGradient(listOf(Mono.Black, Mono.Black))

/** Readable icon/text colour on top of [bg]. */
private fun onColor(bg: Color) = if (bg.luminance() > 0.45f) Color(0xFF141416) else Color.White

/** Primary action: solid pill (black in light mode, peach in dark mode). Only one per card. */
@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val c = LocalVoce.current
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .neonGlow(shape, c.action, if (c.dark && enabled) 0.12f else 0f, 10.dp)
            .clip(shape)
            .background(c.action)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.onAction, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.onAction, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary action: quiet pill with a thin edge. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val c = LocalVoce.current
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(c.tile)
            .border(1.dp, c.tileBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round icon button. Normal = solid action colour; active = card-coloured with an edge. */
@Composable
fun IconOrb(icon: ImageVector, active: Boolean = false, size: Int = 44, onClick: (() -> Unit)? = null) {
    val c = LocalVoce.current
    val bg = if (active) c.tile else if (c.dark) c.tile else c.action
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (c.dark || active) c.tileBorder else Color.Transparent, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (c.dark || active) c.ink else c.onAction, modifier = Modifier.size((size * 0.46f).dp))
    }
}

@Composable
fun TopBar(title: String, subtitle: String? = null, leading: @Composable () -> Unit = {}, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

@Composable
fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val c = LocalVoce.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) c.onAction else c.ink,
        maxLines = 1,
        modifier = Modifier
            .alpha(if (enabled || selected) 1f else 0.5f)
            .clip(shape)
            .background(if (selected) c.action else c.tile)
            .border(1.dp, if (selected) c.action else c.tileBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Small orange label with a glowing dot, like "STEP 1" above a card title. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    val c = LocalVoce.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).neonGlow(CircleShape, c.accent, 0.25f, 6.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = c.accent, maxLines = 1)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** Tappable header that folds a card's body away; the arrow turns to show the state. */
@Composable
fun CollapseHeader(title: String, subtitle: String?, expanded: Boolean, eyebrow: String? = null, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onToggle).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Eyebrow(eyebrow)
                Spacer(Modifier.height(6.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Mono.Tile).border(1.dp, Mono.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = if (expanded) "تصغير" else "تكبير", tint = Ink, modifier = Modifier.size(22.dp).rotate(turn))
        }
    }
}

@Composable
fun GlowBar(fraction: Float, modifier: Modifier = Modifier) {
    val c = LocalVoce.current
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(c.tileBorder)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp)
                .neonGlow(RoundedCornerShape(50), c.accent, if (c.dark) 0.2f else 0f, 6.dp)
                .clip(RoundedCornerShape(50)).background(c.accent),
        )
    }
}

/** Icon in a solid circle. Orange = working, grey = waiting, red = error, action colour = the rest. */
@Composable
fun GlowIcon(icon: ImageVector, color: Color, size: Int = 54, pulse: Boolean = false) {
    val scale = if (pulse) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.94f, targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulseValue",
        ).value
    } else {
        1f
    }
    val dark = LocalVoce.current.dark
    Box(
        modifier = Modifier.size(size.dp).scale(scale)
            .neonGlow(CircleShape, color, if (dark) 0.18f else 0f, 10.dp)
            .clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = onColor(color), modifier = Modifier.size((size * 0.48f).dp))
    }
}

/** Dashboard tile: icon circle, label, and a big number. */
@Composable
fun StatTile(icon: ImageVector, label: String, value: String, detail: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .glass(RoundedCornerShape(28.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlowIcon(icon, Mono.Black, size = 40)
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink, maxLines = 1)
    }
}

/** Round floating button that jumps to the end of a long page (or back to the top). */
@Composable
fun JumpButton(down: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalVoce.current
    val turn by animateFloatAsState(if (down) 0f else 180f, label = "jump")
    Box(
        modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape, ambientColor = c.shadow, spotColor = c.shadow)
            .neonGlow(CircleShape, c.accent, if (c.dark) 0.15f else 0f, 8.dp)
            .clip(CircleShape)
            .background(if (c.dark) c.cardTop else c.action)
            .border(1.dp, if (c.dark) c.accent.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowDown, contentDescription = if (down) "لآخر الصفحة" else "لأول الصفحة",
            tint = if (c.dark) c.accent else c.onAction, modifier = Modifier.size(28.dp).rotate(turn),
        )
    }
}

data class NavItem(val icon: ImageVector, val label: String, val selected: Boolean, val onClick: () -> Unit)

/** Floating pill at the bottom; the selected tab is a filled pill with its label. */
@Composable
fun BottomBar(items: List<NavItem>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val c = LocalVoce.current
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.bar)
            .border(1.dp, if (c.dark) c.border else Color.Transparent, shape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val bg by animateColorAsState(if (item.selected) c.barSelected else Color.Transparent, label = "navBg")
            Row(
                Modifier.clip(shape).background(bg).clickable(onClick = item.onClick).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = item.label, tint = if (item.selected) c.onBarSelected else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                if (item.selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.onBarSelected, maxLines = 1)
                }
            }
        }
    }
}
