package dev.clonner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.clonner.data.model.BlocklistSource
import dev.clonner.vpn.BlocklistEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Context.shieldStore by preferencesDataStore(name = "shield")

/**
 * Owns the domain blocklists: which sources are enabled, their cached copies on disk,
 * and the user's own allow/deny overrides.
 */
class BlocklistRepository(private val context: Context) {

    private val sourcesKey = stringPreferencesKey("sources")
    private val allowKey = stringPreferencesKey("user_allow")
    private val denyKey = stringPreferencesKey("user_deny")
    private val enabledKey = booleanPreferencesKey("shield_enabled")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val cacheDir: File get() = File(context.filesDir, "blocklists").apply { mkdirs() }

    val sources: Flow<List<BlocklistSource>> = context.shieldStore.data.map { prefs ->
        prefs[sourcesKey]
            ?.let { runCatching { json.decodeFromString<List<BlocklistSource>>(it) }.getOrNull() }
            ?: DEFAULT_SOURCES
    }

    val userAllowed: Flow<Set<String>> =
        context.shieldStore.data.map { it[allowKey].toDomainSet() }

    val userDenied: Flow<Set<String>> =
        context.shieldStore.data.map { it[denyKey].toDomainSet() }

    val shieldEnabled: Flow<Boolean> =
        context.shieldStore.data.map { it[enabledKey] ?: false }

    suspend fun setShieldEnabled(enabled: Boolean) {
        context.shieldStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        val updated = sources.first().map { if (it.id == id) it.copy(enabled = enabled) else it }
        writeSources(updated)
    }

    suspend fun addUserRule(domain: String, block: Boolean) {
        val clean = domain.trim().lowercase().removePrefix("www.")
        if (clean.isEmpty()) return
        context.shieldStore.edit { prefs ->
            val key = if (block) denyKey else allowKey
            val other = if (block) allowKey else denyKey
            prefs[key] = (prefs[key].toDomainSet() + clean).joinToString(",")
            prefs[other] = (prefs[other].toDomainSet() - clean).joinToString(",")
        }
    }

    suspend fun removeUserRule(domain: String) {
        context.shieldStore.edit { prefs ->
            prefs[denyKey] = (prefs[denyKey].toDomainSet() - domain).joinToString(",")
            prefs[allowKey] = (prefs[allowKey].toDomainSet() - domain).joinToString(",")
        }
    }

    /**
     * Downloads every enabled source that has no fresh cache, then rebuilds the in-memory
     * matcher. Returns the total number of rules now loaded.
     */
    suspend fun refresh(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val current = sources.first()
        val updated = current.map { source ->
            if (!source.enabled) return@map source
            val file = cacheFile(source)
            val stale = force || !file.exists() ||
                System.currentTimeMillis() - source.lastUpdated > MAX_CACHE_AGE_MS
            if (!stale) return@map source

            val count = runCatching { download(source.url, file) }.getOrNull()
            if (count == null) source
            else source.copy(ruleCount = count, lastUpdated = System.currentTimeMillis())
        }
        writeSources(updated)
        reload(updated)
    }

    /** Rebuilds [BlocklistEngine] from whatever is already cached on disk. */
    suspend fun reload(known: List<BlocklistSource>? = null): Int = withContext(Dispatchers.IO) {
        val list = known ?: sources.first()
        val domains = HashSet<String>(1 shl 16)

        // The bundled seed list keeps the shield useful before the first download.
        runCatching {
            context.assets.open(SEED_ASSET).bufferedReader().use { parseInto(it, domains) }
        }

        list.filter { it.enabled }.forEach { source ->
            val file = cacheFile(source)
            if (file.exists()) {
                runCatching { file.bufferedReader().use { parseInto(it, domains) } }
            }
        }

        BlocklistEngine.replaceRules(
            blocked = domains,
            allowOverrides = userAllowed.first(),
            denyOverrides = userDenied.first(),
        )
        domains.size
    }

    private fun download(url: String, into: File): Int {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Clonner/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val tmp = File(into.parentFile, into.name + ".tmp")
            var lines = 0
            connection.inputStream.bufferedReader().use { reader ->
                tmp.bufferedWriter().use { writer ->
                    reader.lineSequence().forEach { line ->
                        val domain = HostsParser.parse(line) ?: return@forEach
                        writer.append(domain).append('\n')
                        lines++
                    }
                }
            }
            if (!tmp.renameTo(into)) {
                tmp.copyTo(into, overwrite = true)
                tmp.delete()
            }
            return lines
        } finally {
            connection.disconnect()
        }
    }

    private fun parseInto(reader: BufferedReader, into: MutableSet<String>) {
        reader.lineSequence().forEach { line ->
            HostsParser.parse(line)?.let(into::add)
        }
    }

    private fun cacheFile(source: BlocklistSource) = File(cacheDir, "${source.id}.txt")

    private suspend fun writeSources(list: List<BlocklistSource>) {
        context.shieldStore.edit { it[sourcesKey] = json.encodeToString(SOURCE_LIST, list) }
    }

    private fun String?.toDomainSet(): Set<String> =
        this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    companion object {
        private const val SEED_ASSET = "seed_blocklist.txt"
        private val MAX_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private val SOURCE_LIST = ListSerializer(BlocklistSource.serializer())

        val DEFAULT_SOURCES = listOf(
            BlocklistSource(
                id = "stevenblack",
                title = "StevenBlack — ads + trackers",
                url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            ),
            BlocklistSource(
                id = "adaway",
                title = "AdAway — mobile ad servers",
                url = "https://adaway.org/hosts.txt",
            ),
            BlocklistSource(
                id = "easyprivacy",
                title = "Peter Lowe — ad + tracking servers",
                url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
                enabled = false,
            ),
        )
    }
}
