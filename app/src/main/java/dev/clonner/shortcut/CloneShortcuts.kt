package dev.clonner.shortcut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.clonner.data.AppRepository
import dev.clonner.data.model.Clone

/** Builds and pins the home-screen icons that launch a clone. */
object CloneShortcuts {

    fun isPinningSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Asks the launcher to pin [clone]. The launcher shows its own confirmation dialog,
     * so this is a request, not a guarantee — [PinResultReceiver] records the outcome.
     */
    fun requestPin(context: Context, clone: Clone, apps: AppRepository): Boolean {
        if (!isPinningSupported(context)) return false

        val base = apps.iconBitmapFor(clone.packageName) ?: return false
        val icon = IconCompat.createWithAdaptiveBitmap(badge(base, clone.badge.rgb))

        val shortcut = ShortcutInfoCompat.Builder(context, clone.id)
            .setShortLabel(clone.displayName.take(24))
            .setLongLabel(clone.displayName.take(48))
            .setIcon(icon)
            .setIntent(launchIntent(context, clone.id))
            .build()

        val callback = PendingIntent.getBroadcast(
            context,
            clone.id.hashCode(),
            Intent(context, PinResultReceiver::class.java)
                .setAction(PinResultReceiver.ACTION_PINNED)
                .putExtra(PinResultReceiver.EXTRA_CLONE_ID, clone.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, callback.intentSender)
    }

    /** Keeps an already-pinned icon in step with a renamed or recoloured clone. */
    fun refresh(context: Context, clone: Clone, apps: AppRepository) {
        if (!clone.pinned) return
        val base = apps.iconBitmapFor(clone.packageName) ?: return
        val shortcut = ShortcutInfoCompat.Builder(context, clone.id)
            .setShortLabel(clone.displayName.take(24))
            .setLongLabel(clone.displayName.take(48))
            .setIcon(IconCompat.createWithAdaptiveBitmap(badge(base, clone.badge.rgb)))
            .setIntent(launchIntent(context, clone.id))
            .build()
        runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut)) }
    }

    fun removePinned(context: Context, cloneId: String, reason: String) {
        runCatching { ShortcutManagerCompat.disableShortcuts(context, listOf(cloneId), reason) }
    }

    private fun launchIntent(context: Context, cloneId: String) =
        Intent(context, CloneLaunchActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(CloneLaunchActivity.EXTRA_CLONE_ID, cloneId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /**
     * Draws the clone's colour as a corner dot on the app's icon so several clones of the
     * same app are distinguishable on the home screen.
     *
     * Adaptive icons get masked by the launcher, so the artwork is inset into the safe
     * 66% centre zone and the dot is placed just inside that circle.
     */
    private fun badge(source: Bitmap, color: Int): Bitmap {
        val size = maxOf(source.width, ICON_SIZE)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val inset = size * (1f - SAFE_ZONE) / 2f
        canvas.drawBitmap(
            source,
            null,
            RectF(inset, inset, size - inset, size - inset),
            paint,
        )

        val radius = size * 0.115f
        val centre = size / 2f
        val offset = (size * SAFE_ZONE / 2f) * 0.72f
        val cx = centre + offset
        val cy = centre + offset

        // Punch a transparent ring first so the dot reads against a busy icon.
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawCircle(cx, cy, radius * 1.28f, paint)
        paint.xfermode = null

        paint.color = color
        canvas.drawCircle(cx, cy, radius, paint)

        return out
    }

    private const val ICON_SIZE = 192
    private const val SAFE_ZONE = 0.66f
}
