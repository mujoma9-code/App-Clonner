package dev.clonner

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.clonner.data.model.Clone
import dev.clonner.ui.ClonnerViewModel
import dev.clonner.ui.screens.AppPickerScreen
import dev.clonner.ui.screens.CloneDetailScreen
import dev.clonner.ui.screens.ClonesScreen
import dev.clonner.ui.screens.SettingsScreen
import dev.clonner.ui.screens.ShieldScreen
import dev.clonner.ui.theme.ClonnerTheme
import dev.clonner.vpn.ClonnerVpnService
import dev.clonner.vpn.ShieldState

class MainActivity : ComponentActivity() {

    /** Android shows its own system dialog for VPN consent; this carries the result back. */
    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            ClonnerVpnService.start(this)
        } else {
            pendingShieldRequest?.invoke(false)
        }
        pendingShieldRequest = null
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The shield still runs without it; only the ongoing notice is affected. */ }

    private var pendingShieldRequest: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClonnerTheme {
                ClonnerRoot(
                    onRequestShield = ::requestShield,
                    onStopShield = { ClonnerVpnService.stop(this) },
                    onLaunchApp = ::launchPackage,
                )
            }
        }
        askForNotificationPermission()
    }

    /** Asks for VPN consent if it has not been granted yet, then starts the service. */
    private fun requestShield(onDenied: (Boolean) -> Unit) {
        val intent = VpnService.prepare(this)
        if (intent == null) {
            ClonnerVpnService.start(this)
        } else {
            pendingShieldRequest = onDenied
            vpnConsent.launch(intent)
        }
    }

    private fun launchPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { startActivity(intent) }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class Tab(val label: String) { Clones("Clones"), Shield("Shield"), Settings("Settings") }

private sealed interface Route {
    data object Tabs : Route
    data object Picker : Route
    data class Detail(val cloneId: String) : Route
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClonnerRoot(
    onRequestShield: ((Boolean) -> Unit) -> Unit,
    onStopShield: () -> Unit,
    onLaunchApp: (String) -> Unit,
) {
    val viewModel: ClonnerViewModel = viewModel(factory = ClonnerViewModel.Factory)

    val clones by viewModel.clones.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val loadingApps by viewModel.loadingApps.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val shieldState by viewModel.shieldState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val recentBlocks by viewModel.recentBlocks.collectAsStateWithLifecycle()
    val userRules by viewModel.userRules.collectAsStateWithLifecycle()
    val updating by viewModel.updatingLists.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(Tab.Clones) }
    var route by remember { mutableStateOf<Route>(Route.Tabs) }
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val openClone: (Clone) -> Unit = { clone ->
        viewModel.recordLaunch(clone)
        onLaunchApp(clone.packageName)
        if (clone.blockAds && shieldState != ShieldState.Running) {
            onRequestShield { }
        }
    }

    when (val current = route) {
        is Route.Picker -> {
            LaunchedEffect(Unit) { viewModel.loadInstalledApps() }
            AppPickerScreen(
                apps = installedApps,
                repository = viewModel.appRepository,
                loading = loadingApps,
                onPick = { app ->
                    viewModel.createClone(app) { clone -> route = Route.Detail(clone.id) }
                },
                onBack = { route = Route.Tabs },
            )
        }

        is Route.Detail -> {
            val clone = clones.firstOrNull { it.id == current.cloneId }
            if (clone == null) {
                LaunchedEffect(current.cloneId) { route = Route.Tabs }
            } else {
                CloneDetailScreen(
                    clone = clone,
                    apps = viewModel.appRepository,
                    onBack = { route = Route.Tabs },
                    onRename = { viewModel.rename(clone, it) },
                    onBadge = { viewModel.setBadge(clone, it) },
                    onBlockAds = { viewModel.setBlockAds(clone, it) },
                    onPin = { viewModel.pinToHome(clone) },
                    onLaunch = { openClone(clone) },
                    onDelete = { route = Route.Tabs; viewModel.delete(clone) },
                )
            }
        }

        Route.Tabs -> Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (tab) {
                                Tab.Clones -> "Clonner"
                                Tab.Shield -> "Ad Shield"
                                Tab.Settings -> "Settings"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = {
                                Icon(
                                    imageVector = when (entry) {
                                        Tab.Clones -> Icons.Rounded.ContentCopy
                                        Tab.Shield -> Icons.Rounded.Shield
                                        Tab.Settings -> Icons.Rounded.Settings
                                    },
                                    contentDescription = entry.label,
                                )
                            },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbars) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween()) togetherWith fadeOut(tween()) },
                label = "tab",
                modifier = Modifier.fillMaxSize(),
            ) { active ->
                Box(Modifier.fillMaxSize()) {
                    when (active) {
                        Tab.Clones -> ClonesScreen(
                            clones = clones,
                            apps = viewModel.appRepository,
                            contentPadding = padding,
                            onOpenClone = { route = Route.Detail(it.id) },
                            onLaunchClone = openClone,
                            onNewClone = { route = Route.Picker },
                        )

                        Tab.Shield -> ShieldScreen(
                            state = shieldState,
                            stats = stats,
                            sources = sources,
                            recentBlocks = recentBlocks,
                            userDenied = userRules.denied,
                            updating = updating,
                            contentPadding = padding,
                            onToggleShield = { wanted ->
                                viewModel.setShieldPreference(wanted)
                                if (wanted) onRequestShield { } else onStopShield()
                            },
                            onToggleSource = viewModel::toggleSource,
                            onUpdateLists = viewModel::updateBlocklists,
                            onBlockDomain = { viewModel.addRule(it, block = true) },
                            onRemoveRule = viewModel::removeRule,
                            onResetCounters = viewModel::resetCounters,
                        )

                        Tab.Settings -> SettingsScreen(
                            ruleCount = stats.ruleCount,
                            cloneCount = clones.size,
                            contentPadding = padding,
                            onAddRule = { viewModel.addRule(it, block = true) },
                        )
                    }
                }
            }
        }
    }
}

private fun tween() = androidx.compose.animation.core.tween<Float>(220)
