package dev.clonner.work

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import dev.clonner.data.model.InstalledApp

/**
 * Real app cloning, using Android's managed-profile ("work profile") mechanism.
 *
 * This is the genuine article: an app installed into the work profile is a *separate
 * instance* with its own data directory and its own login, running as a different Android
 * user. It is the same mechanism Shelter and Island use, and the same one behind the
 * "Work" copies of apps on a corporate phone.
 *
 * The flow has two halves, because Clonner ends up installed in both profiles:
 *
 *  - **Personal side** — [state] reports what is set up, [provisioningIntent] starts the
 *    system flow that creates the profile, and [openWorkProfileClonner] hops to the other
 *    copy.
 *  - **Work side** — that copy is the profile owner, so it can call [cloneIntoProfile] to
 *    install an existing app into the profile, and [clonedApps] to list what is there.
 *
 * `installExistingPackage` is what keeps this tractable: the profile owner asks the system
 * to install a package that already exists elsewhere on the device, so Clonner never has
 * to read, copy or repackage an APK.
 */
class WorkProfileManager(private val context: Context) {

    private val dpm: DevicePolicyManager
        get() = context.getSystemService(DevicePolicyManager::class.java)

    private val launcherApps: LauncherApps
        get() = context.getSystemService(LauncherApps::class.java)

    private val userManager: UserManager
        get() = context.getSystemService(UserManager::class.java)

    /** What Clonner can do about work profiles on this device, right now. */
    sealed interface State {
        /** The device has no managed-user support at all (common on tablets//Go editions). */
        data object Unsupported : State

        /** Supported, but no profile exists yet. [provisioningIntent] is the next step. */
        data object NotSetUp : State

        /** A work profile exists and this is the personal-side copy of Clonner. */
        data class Ready(val profile: UserHandle) : State

        /** This copy of Clonner *is* the profile owner — cloning happens here. */
        data object InsideWorkProfile : State

        /**
         * A work profile exists but was not created by Clonner, so Clonner has no
         * ownership of it and cannot install into it.
         */
        data object ForeignProfile : State
    }

    fun state(): State {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return State.Unsupported
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
            return State.Unsupported
        }

        if (dpm.isProfileOwnerApp(context.packageName)) return State.InsideWorkProfile

        val profiles = runCatching { userManager.userProfiles }.getOrDefault(emptyList())
            .filter { it != Process.myUserHandle() }

        if (profiles.isEmpty()) return State.NotSetUp

        // Clonner is in both profiles only if it provisioned this one itself.
        val ours = profiles.firstOrNull { profile ->
            runCatching { launcherApps.getActivityList(context.packageName, profile).isNotEmpty() }
                .getOrDefault(false)
        }
        return ours?.let { State.Ready(it) } ?: State.ForeignProfile
    }

    /**
     * The system provisioning flow. Launch with `startActivityForResult`; Android shows its
     * own multi-screen consent UI and creates the profile only if the user agrees.
     */
    fun provisioningIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                ClonnerDeviceAdminReceiver.componentName(context),
            )
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, false)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT, false)
        }
    }

    /** True when the system will actually accept [provisioningIntent]. */
    fun canProvision(): Boolean {
        val intent = provisioningIntent() ?: return false
        return intent.resolveActivity(context.packageManager) != null
    }

    /**
     * Installs [packageName] into the work profile as a genuinely separate instance.
     *
     * Only meaningful from the work-profile copy of Clonner, which is the profile owner.
     * Returns false when this copy is not the owner or the system refused.
     */
    fun cloneIntoProfile(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (!dpm.isProfileOwnerApp(context.packageName)) return false

        val admin = ClonnerDeviceAdminReceiver.componentName(context)
        return runCatching {
            // Some apps are hidden rather than absent in a fresh profile; unhide first.
            runCatching { dpm.setApplicationHidden(admin, packageName, false) }
            dpm.installExistingPackage(admin, packageName)
        }.onFailure {
            Log.e(TAG, "Could not clone $packageName into the work profile", it)
        }.getOrDefault(false)
    }

    /** Removes a cloned instance from the work profile. The personal copy is untouched. */
    fun removeFromProfile(packageName: String): Boolean {
        if (!dpm.isProfileOwnerApp(context.packageName)) return false
        val admin = ClonnerDeviceAdminReceiver.componentName(context)
        return runCatching { dpm.setApplicationHidden(admin, packageName, true) }
            .getOrDefault(false)
    }

    /** Apps with a launcher entry inside the work profile — i.e. the working clones. */
    fun clonedApps(profile: UserHandle? = null): List<InstalledApp> {
        val target = profile ?: (state() as? State.Ready)?.profile ?: Process.myUserHandle()
        return runCatching {
            launcherApps.getActivityList(null, target)
                .distinctBy { it.applicationInfo.packageName }
                .filter { it.applicationInfo.packageName != context.packageName }
                .map { activity ->
                    InstalledApp(
                        packageName = activity.applicationInfo.packageName,
                        label = activity.label?.toString() ?: activity.applicationInfo.packageName,
                        versionName = null,
                        isSystem = false,
                    )
                }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** Launches a cloned app inside the work profile. */
    fun launchCloned(packageName: String, profile: UserHandle? = null): Boolean {
        val target = profile ?: (state() as? State.Ready)?.profile ?: return false
        return runCatching {
            val activity = launcherApps.getActivityList(packageName, target).firstOrNull()
                ?: return false
            launcherApps.startMainActivity(activity.componentName, target, null, null)
            true
        }.getOrDefault(false)
    }

    /**
     * Jumps to the work-profile copy of Clonner, which is where cloning is actually done.
     * Uses `CrossProfileApps`, which needs no permission when the app is in both profiles.
     */
    fun openWorkProfileClonner(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val profile = (state() as? State.Ready)?.profile ?: return false

        return runCatching {
            val crossProfile = context.getSystemService(android.content.pm.CrossProfileApps::class.java)
            val target = crossProfile.targetUserProfiles.firstOrNull { it == profile } ?: return false
            val main = context.packageManager
                .getLaunchIntentForPackage(context.packageName)?.component ?: return false
            crossProfile.startMainActivity(main, target)
            true
        }.onFailure { Log.w(TAG, "Could not open the work profile copy", it) }
            .getOrDefault(false)
    }

    /**
     * Tears the work profile down, removing every cloned app and its data. Only the
     * profile owner can do this, and it does not touch anything on the personal side.
     */
    fun removeWorkProfile(): Boolean {
        if (!dpm.isProfileOwnerApp(context.packageName)) return false
        val admin = ClonnerDeviceAdminReceiver.componentName(context)
        return runCatching {
            dpm.wipeData(0)
            true
        }.onFailure { Log.e(TAG, "Could not remove the work profile", it) }
            .getOrDefault(false)
    }

    private companion object {
        const val TAG = "WorkProfile"
    }
}
