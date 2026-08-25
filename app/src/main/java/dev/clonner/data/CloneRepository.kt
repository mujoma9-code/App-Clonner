package dev.clonner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.clonner.data.model.Clone
import dev.clonner.data.model.CloneBadge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val CLONE_LIST = ListSerializer(Clone.serializer())

private val Context.cloneStore by preferencesDataStore(name = "clones")

/** Persists the user's clones as a JSON blob in DataStore. */
class CloneRepository(private val context: Context) {

    private val key = stringPreferencesKey("clone_list")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val clones: Flow<List<Clone>> = context.cloneStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString<List<Clone>>(raw) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    suspend fun create(packageName: String, label: String, existingCount: Int): Clone {
        val siblings = current().count { it.packageName == packageName }
        val clone = Clone(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            displayName = if (siblings == 0) label else "$label ${siblings + 1}",
            badge = CloneBadge.next(existingCount),
            blockAds = true,
            createdAt = System.currentTimeMillis(),
        )
        mutate { it + clone }
        return clone
    }

    suspend fun update(clone: Clone) = mutate { list ->
        list.map { if (it.id == clone.id) clone else it }
    }

    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    suspend fun recordLaunch(id: String) = mutate { list ->
        list.map { if (it.id == id) it.copy(launchCount = it.launchCount + 1) else it }
    }

    suspend fun markPinned(id: String, pinned: Boolean) = mutate { list ->
        list.map { if (it.id == id) it.copy(pinned = pinned) else it }
    }

    /** One-shot read, for callers outside a Flow collector (the shortcut trampoline). */
    suspend fun find(id: String): Clone? = current().firstOrNull { it.id == id }

    private suspend fun current(): List<Clone> {
        var result: List<Clone> = emptyList()
        context.cloneStore.edit { prefs ->
            result = prefs[key]?.let { raw ->
                runCatching { json.decodeFromString<List<Clone>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
        }
        return result
    }

    private suspend fun mutate(block: (List<Clone>) -> List<Clone>) {
        context.cloneStore.edit { prefs ->
            val existing = prefs[key]?.let { raw ->
                runCatching { json.decodeFromString<List<Clone>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
            prefs[key] = json.encodeToString(CLONE_LIST, block(existing))
        }
    }
}
