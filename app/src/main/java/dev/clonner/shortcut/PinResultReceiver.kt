package dev.clonner.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.clonner.ClonnerApp
import kotlinx.coroutines.launch

/** Fires once the launcher confirms the user accepted a pin request. */
class PinResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PINNED) return
        val cloneId = intent.getStringExtra(EXTRA_CLONE_ID) ?: return

        val app = context.applicationContext as? ClonnerApp ?: return
        val pending = goAsync()
        app.scope.launch {
            try {
                app.clones.markPinned(cloneId, true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PINNED = "dev.clonner.SHORTCUT_PINNED"
        const val EXTRA_CLONE_ID = "dev.clonner.extra.CLONE_ID"
    }
}
