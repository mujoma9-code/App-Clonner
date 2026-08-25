package dev.clonner.work

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Makes Clonner eligible to own a managed profile.
 *
 * Android requires a [DeviceAdminReceiver] before it will hand an app profile ownership.
 * Clonner declares no invasive policies — the receiver exists so the system will let the
 * work profile be created and so provisioning has somewhere to report back to.
 */
class ClonnerDeviceAdminReceiver : DeviceAdminReceiver() {

    /**
     * Fires inside the newly created work profile once the system has finished setting it
     * up. Until [DevicePolicyManager.setProfileEnabled] is called the profile exists but
     * stays hidden, so this is the step that actually turns it on.
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = componentName(context)

        runCatching {
            dpm.setProfileName(admin, PROFILE_NAME)
            dpm.setProfileEnabled(admin)
        }.onFailure { Log.e(TAG, "Could not enable the work profile", it) }

        // Let the work-profile copy of Clonner be launched from the personal side.
        runCatching {
            dpm.addCrossProfileIntentFilter(
                admin,
                android.content.IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT,
            )
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Clonner is now a profile owner")
    }

    companion object {
        private const val TAG = "ClonnerAdmin"
        private const val PROFILE_NAME = "Clonner"

        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, ClonnerDeviceAdminReceiver::class.java)
    }
}
