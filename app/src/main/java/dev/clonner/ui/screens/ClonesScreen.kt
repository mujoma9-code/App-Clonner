package dev.clonner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clonner.data.AppRepository
import dev.clonner.data.model.Clone
import dev.clonner.ui.components.AppIcon
import dev.clonner.ui.components.EmptyState

@Composable
fun ClonesScreen(
    clones: List<Clone>,
    apps: AppRepository,
    contentPadding: PaddingValues,
    onOpenClone: (Clone) -> Unit,
    onLaunchClone: (Clone) -> Unit,
    onNewClone: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (clones.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(38.dp),
                        )
                    },
                    title = "No clones yet",
                    body = "Make a clone of an installed app to give it its own name, its own " +
                        "colour and its own ad-blocking rules — then pin it straight to your home screen.",
                    action = {
                        ExtendedFloatingActionButton(
                            onClick = onNewClone,
                            icon = { Icon(Icons.Rounded.Add, null) },
                            text = { Text("Create your first clone") },
                        )
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(clones, key = { it.id }) { clone ->
                    CloneCard(
                        clone = clone,
                        apps = apps,
                        onOpen = { onOpenClone(clone) },
                        onLaunch = { onLaunchClone(clone) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = clones.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp,
                ),
        ) {
            ExtendedFloatingActionButton(
                onClick = onNewClone,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New clone") },
            )
        }
    }
}

@Composable
private fun CloneCard(
    clone: Clone,
    apps: AppRepository,
    onOpen: () -> Unit,
    onLaunch: () -> Unit,
) {
    val installed = apps.isInstalled(clone.packageName)
    val badge = Color(clone.badge.rgb)

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                icon = apps.iconFor(clone.packageName),
                size = 54.dp,
                badgeColor = badge,
            )
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = clone.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!installed) {
                        Text(
                            text = "App not installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        if (clone.blockAds) {
                            Icon(
                                Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = buildString {
                                append(if (clone.blockAds) "Shielded" else "Unshielded")
                                if (clone.pinned) append(" · Pinned")
                                if (clone.launchCount > 0) append(" · ${clone.launchCount} opens")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (installed) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = installed, onClick = onLaunch)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (installed) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
