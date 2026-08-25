package dev.clonner

import android.app.Application
import dev.clonner.data.AppRepository
import dev.clonner.data.BlocklistRepository
import dev.clonner.data.CloneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the app's singletons. Small enough that a DI framework would be overhead. */
class ClonnerApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val apps: AppRepository by lazy { AppRepository(this) }
    val clones: CloneRepository by lazy { CloneRepository(this) }
    val blocklists: BlocklistRepository by lazy { BlocklistRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Load whatever is already cached so the shield can filter from the first query,
        // without waiting on the network.
        scope.launch { runCatching { blocklists.reload() } }
    }
}
