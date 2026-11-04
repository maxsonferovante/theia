package app.theia.android.roll

import android.content.Context
import app.theia.domain.model.FilmRollId
import app.theia.domain.model.FilmRollRecord
import app.theia.domain.port.FilmRollRepositoryPort
import app.theia.domain.serialization.VersionedJsonCodec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** SharedPreferences-backed roll records. Writes are synchronous commits so a completed exposure survives process death. */
class AndroidFilmRollRepository(context: Context) : FilmRollRepositoryPort {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val codec = VersionedJsonCodec()
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun find(id: FilmRollId): FilmRollRecord? = synchronized(storageLock) {
        preferences.getString(recordKey(id), null)?.let { encoded ->
            runCatching { codec.decode<FilmRollRecord>(encoded) }.getOrElse { throw IllegalStateException("Could not decode film roll ${id.value}", it) }
        }
    }

    override suspend fun insert(record: FilmRollRecord) = synchronized(storageLock) {
        check(!preferences.contains(recordKey(record.roll.id))) { "Film roll already exists" }
        val ids = rollIds().toMutableSet().apply { add(record.roll.id.value) }
        check(preferences.edit()
            .putString(recordKey(record.roll.id), codec.encode(record))
            .putString(KEY_ROLL_IDS, ids.joinToString(separator = "\n"))
            .commit()) { "Could not persist film roll" }
    }

    override suspend fun save(record: FilmRollRecord) = synchronized(storageLock) {
        check(preferences.contains(recordKey(record.roll.id))) { "Film roll does not exist" }
        check(preferences.edit().putString(recordKey(record.roll.id), codec.encode(record)).commit()) {
            "Could not update film roll"
        }
    }

    override suspend fun <T> withRollLock(id: FilmRollId, operation: suspend () -> T): T {
        val mutex = locks.getOrPut(id.value) { Mutex() }
        return mutex.withLock { operation() }
    }

    suspend fun all(): List<FilmRollRecord> = synchronized(storageLock) {
        rollIds().mapNotNull { id ->
            preferences.getString("$RECORD_PREFIX$id", null)?.let { encoded ->
                runCatching { codec.decode<FilmRollRecord>(encoded) }
                    .getOrElse { throw IllegalStateException("Could not decode film roll $id", it) }
            }
        }.sortedByDescending { it.roll.createdAtEpochMillis }
    }

    private fun rollIds(): Set<String> = preferences.getString(KEY_ROLL_IDS, null)
        ?.lineSequence()?.filter(String::isNotBlank)?.toSet().orEmpty()

    private fun recordKey(id: FilmRollId) = "$RECORD_PREFIX${id.value}"

    private companion object {
        const val PREFERENCES_NAME = "theia_film_rolls"
        const val KEY_ROLL_IDS = "roll_ids"
        const val RECORD_PREFIX = "roll:"
        val storageLock = Any()
    }
}
