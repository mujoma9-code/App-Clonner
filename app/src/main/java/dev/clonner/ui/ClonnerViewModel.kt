package dev.clonner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.clonner.ClonnerApp
import dev.clonner.data.model.BlocklistSource
import dev.clonner.data.model.Clone
import dev.clonner.data.model.CloneBadge
import dev.clonner.data.model.InstalledApp
import dev.clonner.shortcut.CloneShortcuts
import dev.clonner.vpn.BlocklistEngine
import dev.clonner.vpn.ClonnerVpnService
import dev.clonner.vpn.ShieldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClonnerViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ClonnerApp

    val clones: StateFlow<List<Clone>> = application.clones.clones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sources: StateFlow<List<BlocklistSource>> = application.blocklists.sources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userRules: StateFlow<UserRules> = combine(
        application.blocklists.userDenied,
        application.blocklists.userAllowed,
    ) { denied, allowed -> UserRules(denied, allowed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserRules())

    val shieldState: StateFlow<ShieldState> = ClonnerVpnService.state
    val stats = BlocklistEngine.stats
    val recentBlocks = BlocklistEngine.recentBlocks

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _loadingApps = MutableStateFlow(false)
    val loadingApps: StateFlow<Boolean> = _loadingApps.asStateFlow()

    private val _updatingLists = MutableStateFlow(false)
    val updatingLists: StateFlow<Boolean> = _updatingLists.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    val appRepository get() = application.apps

    fun loadInstalledApps() {
        if (_loadingApps.value) return
        viewModelScope.launch {
            _loadingApps.value = true
            _installedApps.value = runCatching { application.apps.loadLaunchableApps() }
                .getOrDefault(emptyList())
            _loadingApps.value = false
        }
    }

    fun createClone(app: InstalledApp, onCreated: (Clone) -> Unit) {
        viewModelScope.launch {
            val clone = application.clones.create(app.packageName, app.label, clones.value.size)
            onCreated(clone)
        }
    }

    fun rename(clone: Clone, name: String) = viewModelScope.launch {
        val trimmed = name.trim().ifEmpty { application.apps.labelFor(clone.packageName) }
        val updated = clone.copy(displayName = trimmed)
        application.clones.update(updated)
        CloneShortcuts.refresh(application, updated, application.apps)
    }

    fun setBadge(clone: Clone, badge: CloneBadge) = viewModelScope.launch {
        val updated = clone.copy(badge = badge)
        application.clones.update(updated)
        CloneShortcuts.refresh(application, updated, application.apps)
    }

    fun setBlockAds(clone: Clone, enabled: Boolean) = viewModelScope.launch {
        application.clones.update(clone.copy(blockAds = enabled))
    }

    fun delete(clone: Clone) = viewModelScope.launch {
        CloneShortcuts.removePinned(application, clone.id, "This clone was deleted in Clonner")
        application.clones.delete(clone.id)
        _messages.value = "${clone.displayName} deleted"
    }

    fun pinToHome(clone: Clone) {
        if (!CloneShortcuts.isPinningSupported(application)) {
            _messages.value = "This launcher does not support pinning shortcuts"
            return
        }
        val requested = CloneShortcuts.requestPin(application, clone, application.apps)
        _messages.value = if (requested) {
            "Confirm the shortcut in your launcher"
        } else {
            "Could not create a shortcut for ${clone.displayName}"
        }
    }

    fun recordLaunch(clone: Clone) = viewModelScope.launch {
        application.clones.recordLaunch(clone.id)
    }

    fun setShieldPreference(enabled: Boolean) = viewModelScope.launch {
        application.blocklists.setShieldEnabled(enabled)
    }

    fun toggleSource(source: BlocklistSource, enabled: Boolean) = viewModelScope.launch {
        application.blocklists.setSourceEnabled(source.id, enabled)
        application.blocklists.reload()
    }

    fun updateBlocklists() {
        if (_updatingLists.value) return
        viewModelScope.launch {
            _updatingLists.value = true
            val count = runCatching { application.blocklists.refresh(force = true) }.getOrNull()
            _updatingLists.value = false
            _messages.value = if (count != null) {
                "%,d rules loaded".format(count)
            } else {
                "Could not update the blocklists — check your connection"
            }
        }
    }

    fun addRule(domain: String, block: Boolean) = viewModelScope.launch {
        application.blocklists.addUserRule(domain, block)
        application.blocklists.reload()
        _messages.value = if (block) "Blocking $domain" else "Allowing $domain"
    }

    fun removeRule(domain: String) = viewModelScope.launch {
        application.blocklists.removeUserRule(domain)
        application.blocklists.reload()
    }

    fun resetCounters() = BlocklistEngine.resetCounters()

    fun consumeMessage() { _messages.value = null }

    data class UserRules(
        val denied: Set<String> = emptySet(),
        val allowed: Set<String> = emptySet(),
    )

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras,
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return ClonnerViewModel(app) as T
            }
        }
    }
}
