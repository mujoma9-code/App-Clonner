package dev.clonner.shortcut

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import dev.clonner.ClonnerApp
import dev.clonner.vpn.ClonnerVpnService
import dev.clonner.vpn.ShieldState
import kotlinx.coroutines.launch

/**
 * Invisible trampoline behind every pinned clone icon.
 *
 * It brings Ad Shield up first when the clone asks for it, then hands off to the real
 * installed app. Nothing is drawn — the activity finishes as soon as the target starts.
 */
class CloneLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val cloneId = intent?.getStringExtra(EXTRA_CLONE_ID)
        if (cloneId == null) {
            finishQuietly()
            return
        }
        resolveAndLaunch(cloneId)
    }

    private fun resolveAndLaunch(cloneId: String) {
        val app = application as ClonnerApp

        app.scope.launch {
            val clone = app.clones.find(cloneId)
            if (clone == null) {
                toast("That clone no longer exists")
                finishQuietly()
                return@launch
            }

            val launch = app.apps.launchIntentFor(clone.packageName)
            if (launch == null) {
                toast("${clone.displayName} is not installed any more")
                CloneShortcuts.removePinned(
                    this@CloneLaunchActivity,
                    cloneId,
                    "The app this clone points to was uninstalled",
                )
                finishQuietly()
                return@launch
            }

            if (clone.blockAds) ensureShieldRunning()

            app.clones.recordLaunch(cloneId)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            runCatching { startActivity(launch) }
                .onFailure { toast("Could not open ${clone.displayName}") }
            finishQuietly()
        }
    }

    /**
     * Starts the shield if it is off and already has consent. If consent was never granted
     * the clone still launches — the prompt would interrupt the tap, so it is left for the
     * Shield screen inside Clonner.
     */
    private fun ensureShieldRunning() {
        if (ClonnerVpnService.state.value == ShieldState.Running) return
        if (VpnService.prepare(this) != null) return
        runCatching { ClonnerVpnService.start(this) }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun finishQuietly() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_CLONE_ID = "dev.clonner.extra.CLONE_ID"
    }
}
