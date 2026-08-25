package dev.clonner.data.model

import kotlinx.serialization.Serializable

/** An app that is actually installed on the device and can be launched. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
)

/**
 * A Clonner "clone".
 *
 * Clonner does not repackage or modify the target APK. A clone is a *launch profile*:
 * its own name, its own badge colour and its own shielding policy, reachable from its
 * own pinned home-screen icon. Launching it opens the real installed app, with Ad
 * Shield brought up first when [blockAds] is set.
 */
@Serializable
data class Clone(
    val id: String,
    val packageName: String,
    val displayName: String,
    val badge: CloneBadge = CloneBadge.VIOLET,
    val blockAds: Boolean = true,
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val launchCount: Int = 0,
)

/** Colour used to tint a clone's badge so two clones of one app stay tellable apart. */
@Serializable
enum class CloneBadge(val rgb: Int) {
    VIOLET(0xFF7C4DFF.toInt()),
    TEAL(0xFF12F0E1.toInt()),
    CORAL(0xFFFF6B6B.toInt()),
    AMBER(0xFFFFB020.toInt()),
    LIME(0xFF7BE04F.toInt()),
    PINK(0xFFFF5FA2.toInt()),
    SKY(0xFF35A7FF.toInt()),
    ;

    companion object {
        fun next(index: Int): CloneBadge = entries[index % entries.size]
    }
}

/** A remote or bundled hosts-format list of domains to block. */
@Serializable
data class BlocklistSource(
    val id: String,
    val title: String,
    val url: String,
    val enabled: Boolean = true,
    val ruleCount: Int = 0,
    val lastUpdated: Long = 0L,
)

/** One filtering decision, kept in a small in-memory ring buffer for the UI. */
data class BlockEvent(
    val domain: String,
    val blocked: Boolean,
    val at: Long,
)
