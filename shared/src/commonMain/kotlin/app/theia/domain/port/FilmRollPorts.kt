package app.theia.domain.port

import app.theia.domain.model.FilmRollId
import app.theia.domain.model.FilmRollRecord
import app.theia.domain.model.GalleryLocation

/** Storage must serialize mutations for each roll ID; implementations may use a database transaction. */
interface FilmRollRepositoryPort {
    suspend fun find(id: FilmRollId): FilmRollRecord?

    suspend fun insert(record: FilmRollRecord)

    suspend fun save(record: FilmRollRecord)

    suspend fun <T> withRollLock(id: FilmRollId, operation: suspend () -> T): T
}

/** Reopens a gallery original so development also works after app restart. */
interface OriginalPhotoSourcePort {
    suspend fun loadOriginal(location: GalleryLocation): CapturedPhoto
}
