package dev.clonner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clonner.data.model.BlockEvent
import dev.clonner.data.model.BlocklistSource
import dev.clonner.ui.components.BlockRateRing
import dev.clonner.ui.components.StatTile
import dev.clonner.ui.theme.Coral
import dev.clonner.ui.theme.Lime
import dev.clonner.vpn.ShieldState
import dev.clonner.vpn.ShieldStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShieldScreen(
    state: ShieldState,
    stats: ShieldStats,
    sources: List<BlocklistSource>,
    recentBlocks: List<BlockEvent>,
    userDenied: Set<String>,
    updating: Boolean,
    contentPadding: PaddingValues,
    onToggleShield: (Boolean) -> Unit,
    onToggleSource: (BlocklistSource, Boolean) -> Unit,
    onUpdateLists: () -> Unit,
    onBlockDomain: (String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onResetCounters: () -> Unit,
) {
    val running = state == ShieldState.Running

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ShieldHeroCard(running, stats, onToggleShield) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = formatCount(stats.blocked),
                    label = "Blocked",
                    tint = Coral,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = formatCount(stats.allowed),
                    label = "Allowed",
                    tint = Lime,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            ShieldCard(
                title = "Blocklists",
                trailing = {
                    if (updating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onUpdateLists) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Update blocklists",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            ) {
                Text(
                    text = "%,d rules loaded".format(stats.ruleCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                sources.forEachIndexed { index, source ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = source.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (source.ruleCount > 0) {
                                    "%,d rules".format(source.ruleCount)
                                } else {
                                    "Not downloaded yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = source.enabled,
                            onCheckedChange = { onToggleSource(source, it) },
                        )
                    }
                }
            }
        }

        if (userDenied.isNotEmpty()) {
            item {
                ShieldCard(title = "Your rules") {
                    userDenied.forEach { domain ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Block,
                                contentDescription = null,
                                tint = Coral,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = { onRemoveRule(domain) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove rule",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            ShieldCard(
                title = "Recently blocked",
                trailing = {
                    if (stats.total > 0) {
                        TextButton(onClick = onResetCounters) { Text("Reset") }
                    }
                },
            ) {
                if (recentBlocks.isEmpty()) {
                    Text(
                        text = if (running) {
                            "Nothing blocked yet. Open an app with ads and watch this fill up."
                        } else {
                            "Turn the shield on to start filtering."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(recentBlocks, key = { it.domain + it.at }) { event ->
            BlockedRow(event = event, onBlockDomain = onBlockDomain)
        }
    }
}

@Composable
private fun ShieldHeroCard(
    running: Boolean,
    stats: ShieldStats,
    onToggle: (Boolean) -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (running) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(400),
        label = "hero",
    )

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (running) "Protection on" else "Protection off",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (running) "Filtering DNS on this device" else "Ads are getting through",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = running, onCheckedChange = onToggle)
            }

            AnimatedVisibility(visible = stats.total > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(18.dp))
                    BlockRateRing(
                        rate = stats.blockRate,
                        active = running,
                        label = "${(stats.blockRate * 100).toInt()}%",
                        caption = "of lookups\nblocked",
                        modifier = Modifier.size(168.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(event: BlockEvent, onBlockDomain: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onBlockDomain(event.domain) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Coral),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = event.domain,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = TIME_FORMAT.format(Date(event.at)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShieldCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 10_000 -> "%.1fk".format(value / 1_000.0)
    else -> "%,d".format(value)
}
