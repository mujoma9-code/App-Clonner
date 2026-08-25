package dev.clonner.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.clonner.data.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/** Reads the device's installed-app list and renders launcher icons for Compose. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    private val iconCache: MutableMap<String, ImageBitmap> =
        Collections.synchronizedMap(object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > 192
        })

    /** Every app with a launcher entry, minus Clonner itself, sorted by name. */
    suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        resolved.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    versionName = runCatching { pm.getPackageInfo(info.packageName, 0).versionName }
                        .getOrNull(),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun isInstalled(packageName: String): Boolean =
        runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess

    fun labelFor(packageName: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)

    fun launchIntentFor(packageName: String): Intent? =
        pm.getLaunchIntentForPackage(packageName)

    /** Launcher icon as an [ImageBitmap], memoised. Returns null if the app is gone. */
    fun iconFor(packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        val drawable = runCatching { pm.getApplicationIcon(packageName) }.getOrNull() ?: return null
        val bitmap = drawable.toBitmap(ICON_PX).asImageBitmap()
        iconCache[packageName] = bitmap
        return bitmap
    }

    /** Raw bitmap at shortcut resolution, used when building a pinned icon. */
    fun iconBitmapFor(packageName: String, sizePx: Int = SHORTCUT_PX): Bitmap? =
        runCatching { pm.getApplicationIcon(packageName) }.getOrNull()?.toBitmap(sizePx)

    private companion object {
        const val ICON_PX = 144
        const val SHORTCUT_PX = 192
    }
}

/** Rasterises any [Drawable] — including adaptive icons — to a square bitmap. */
internal fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null && this !is AdaptiveIconDrawable) {
        if (bitmap.width == sizePx && bitmap.height == sizePx) return bitmap
        return Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
    }
    val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return out
}
