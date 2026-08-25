package dev.clonner.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clonner.data.AppRepository
import dev.clonner.data.model.InstalledApp
import dev.clonner.ui.components.AppIcon
import dev.clonner.ui.theme.Amber
import dev.clonner.ui.theme.Lime
import dev.clonner.work.WorkProfileManager

/**
 * The real-cloning tab.
 *
 * Unlike the shortcut-based clones on the Clones tab, an app installed here is a genuinely
 * separate instance with its own storage and its own login, because it runs as a different
 * Android user inside a managed profile.
 */
@Composable
fun WorkProfileScreen(
    state: WorkProfileManager.State,
    clonedApps: List<InstalledApp>,
    candidates: List<InstalledApp>,
    apps: AppRepository,
    busy: Boolean,
    contentPadding: PaddingValues,
    onCreateProfile: () -> Unit,
    onOpenWorkClonner: () -> Unit,
    onCloneApp: (InstalledApp) -> Unit,
    onLaunchCloned: (InstalledApp) -> Unit,
    onRemoveCloned: (InstalledApp) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(state, busy, onCreateProfile, onOpenWorkClonner) }

        when (state) {
            is WorkProfileManager.State.Ready -> {
                if (clonedApps.isEmpty()) {
                    item {
                        InfoCard(
                            "Nothing cloned yet",
                            "Open Clonner inside the work profile to pick apps. They install as " +
                                "separate copies with their own logins.",
                        )
                    }
                } else {
                    item { SectionHeader("Real clones", "${clonedApps.size}") }
                    items(clonedApps, key = { it.packageName }) { app ->
                        ClonedRow(
                            app = app,
                            apps = apps,
                            actionLabel = "Open",
                            onAction = { onLaunchCloned(app) },
                        )
                    }
                }
            }

            WorkProfileManager.State.InsideWorkProfile -> {
                if (clonedApps.isNotEmpty()) {
                    item { SectionHeader("Cloned here", "${clonedApps.size}") }
                    items(clonedApps, key = { "installed-" + it.packageName }) { app ->
                        ClonedRow(
                            app = app,
                            apps = apps,
                            actionLabel = "Remove",
                            destructive = true,
                            onAction = { onRemoveCloned(app) },
                        )
                    }
                }

                item { SectionHeader("Add a clone", "${candidates.size}") }
                items(candidates, key = { "candidate-" + it.packageName }) { app ->
                    ClonedRow(
                        app = app,
                        apps = apps,
                        actionLabel = "Clone",
                        onAction = { onCloneApp(app) },
                    )
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun StatusCard(
    state: WorkProfileManager.State,
    busy: Boolean,
    onCreateProfile: () -> Unit,
    onOpenWorkClonner: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is WorkProfileManager.State.Ready,
                WorkProfileManager.State.InsideWorkProfile -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (state) {
                        is WorkProfileManager.State.Ready,
                        WorkProfileManager.State.InsideWorkProfile -> Icons.Rounded.CheckCircle
                        WorkProfileManager.State.Unsupported,
                        WorkProfileManager.State.ForeignProfile -> Icons.Rounded.Warning
                        else -> Icons.Rounded.WorkOutline
                    },
                    contentDescription = null,
                    tint = when (state) {
                        is WorkProfileManager.State.Ready,
                        WorkProfileManager.State.InsideWorkProfile -> Lime
                        WorkProfileManager.State.Unsupported,
                        WorkProfileManager.State.ForeignProfile -> Amber
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = when (state) {
                        WorkProfileManager.State.Unsupported -> "Not supported here"
                        WorkProfileManager.State.NotSetUp -> "Real cloning is off"
                        is WorkProfileManager.State.Ready -> "Real cloning is on"
                        WorkProfileManager.State.InsideWorkProfile -> "You're in the work profile"
                        WorkProfileManager.State.ForeignProfile -> "Work profile already in use"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = when (state) {
                    WorkProfileManager.State.Unsupported ->
                        "This device does not support managed profiles, so separate app instances " +
                            "are not possible. The Clones tab still works for shortcuts and shielding."
                    WorkProfileManager.State.NotSetUp ->
                        "A work profile gives you genuinely separate copies of your apps — their own " +
                            "storage, their own logins. Android runs the setup itself and asks you to " +
                            "confirm. Nothing is wiped, and your personal apps are untouched."
                    is WorkProfileManager.State.Ready ->
                        "Your work profile is ready. Cloning happens inside it, so open the work " +
                            "profile's copy of Clonner to add or remove clones."
                    WorkProfileManager.State.InsideWorkProfile ->
                        "This is the work-profile copy of Clonner. Apps you clone here install as " +
                            "separate instances and appear in your launcher with a briefcase badge."
                    WorkProfileManager.State.ForeignProfile ->
                        "This device already has a work profile that Clonner does not own — usually " +
                            "one set up by an employer or another app. Android allows only one, so " +
                            "Clonner cannot install into it."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state) {
                WorkProfileManager.State.NotSetUp -> {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onCreateProfile,
                        enabled = !busy,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("Set up real cloning")
                    }
                }

                is WorkProfileManager.State.Ready -> {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onOpenWorkClonner,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Clonner in the work profile")
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClonedRow(
    app: InstalledApp,
    apps: AppRepository,
    actionLabel: String,
    destructive: Boolean = false,
    onAction: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon = apps.iconFor(app.packageName), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
