package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun accentBrush(): Brush = Brush.horizontalGradient(listOf(Mono.Black, if (Mono.dark) Color(0xFFCFCFD4) else Color(0xFF2A2B2F)))

/** Primary action: black pill. Only one per card. */
@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(Mono.Black)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Mono.OnBlack, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Mono.OnBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary action: light grey pill. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(Mono.Tile)
            .border(1.dp, Mono.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round icon button: black circle with a white icon; active = white circle with a black icon. */
@Composable
fun IconOrb(icon: ImageVector, active: Boolean = false, size: Int = 44, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .then(
                if (active) {
                    Modifier.shadow(6.dp, CircleShape, ambientColor = Color(0x33101318), spotColor = Color(0x33101318))
                        .clip(CircleShape).background(Mono.Card)
                } else {
                    Modifier.clip(CircleShape).background(Mono.Black)
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Ink else Mono.OnBlack, modifier = Modifier.size((size * 0.46f).dp))
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
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) Mono.OnBlack else Ink,
        maxLines = 1,
        modifier = Modifier
            .alpha(if (enabled || selected) 1f else 0.5f)
            .clip(shape)
            .background(if (selected) Mono.Black else Mono.Tile)
            .border(1.dp, if (selected) Mono.Black else Mono.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
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

@Composable
fun GlowBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Mono.Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(Mono.Black))
    }
}

/** Icon in a solid circle, like the dashboard tiles. Grey = waiting, red = error, black = the rest. */
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
    Box(
        modifier = Modifier.size(size.dp).scale(scale).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (color == Mono.Black) Mono.OnBlack else Color.White, modifier = Modifier.size((size * 0.48f).dp))
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

data class NavItem(val icon: ImageVector, val label: String, val selected: Boolean, val onClick: () -> Unit)

/** Floating black pill at the bottom; the selected tab is a white circle with a black icon. */
@Composable
fun BottomBar(items: List<NavItem>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color(0x55101318), spotColor = Color(0x55101318))
            .clip(shape)
            .background(Mono.Black)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val bg by animateColorAsState(if (item.selected) Mono.OnBlack else Color.Transparent, label = "navBg")
            Row(
                Modifier.clip(shape).background(bg).clickable(onClick = item.onClick).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = item.label, tint = if (item.selected) Mono.Black else Mono.OnBlack.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                if (item.selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Mono.Black, maxLines = 1)
                }
            }
        }
    }
}

/**
 * A card that folds to a single line (icon, title, one-line summary). Tap the line to open it;
 * an open card shows a small "fold" handle under its content.
 */
@Composable
fun Foldable(
    open: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    title: String,
    summary: String,
    content: @Composable () -> Unit,
) {
    AnimatedContent(targetState = open, label = "fold") { isOpen ->
        if (isOpen) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                content()
                Row(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Mono.Tile)
                        .clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ExpandLess, "تصغير", tint = Ink.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlowIcon(icon, Mono.Black, size = 36)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                    Text(summary, style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ExpandMore, "فتح", tint = Ink.copy(alpha = 0.5f))
            }
        }
    }
}

/** Round button that scrolls a list to its end; shows only while there is more below. */
@Composable
fun JumpToEnd(list: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val visible by remember { derivedStateOf { list.canScrollForward } }
    AnimatedVisibility(visible, modifier, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        Box(
            Modifier.size(52.dp)
                .shadow(10.dp, CircleShape, ambientColor = Color(0x55101318), spotColor = Color(0x55101318))
                .clip(CircleShape)
                .background(Mono.Black)
                .clickable { scope.launch { list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.KeyboardDoubleArrowDown, "لآخر الصفحة", tint = Mono.OnBlack, modifier = Modifier.size(26.dp))
        }
    }
}
