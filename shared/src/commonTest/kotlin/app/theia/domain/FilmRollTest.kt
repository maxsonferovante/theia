package app.theia.domain

import app.theia.domain.model.FilmRoll
import app.theia.domain.model.FilmRollCapacity
import app.theia.domain.model.FilmRollId
import app.theia.domain.model.PhotoAssetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilmRollTest {
    @Test
    fun recordsPhotosUntilItsSelectedCapacity() {
        val roll = FilmRoll(
            id = FilmRollId("roll-24"),
            capacity = FilmRollCapacity.EXPOSURES_24,
            createdAtEpochMillis = 100,
        )

        val filledRoll = (1..24).fold(roll) { current, pose ->
            current.recordPhoto(PhotoAssetId("photo-$pose"))
        }

        assertEquals(24, filledRoll.photoIds.size)
        assertFailsWith<IllegalArgumentException> {
            filledRoll.recordPhoto(PhotoAssetId("photo-25"))
        }
    }

    @Test
    fun doesNotAllowOnePhotoToConsumeTwoExposures() {
        val roll = FilmRoll(
            id = FilmRollId("roll-36"),
            capacity = FilmRollCapacity.EXPOSURES_36,
            createdAtEpochMillis = 100,
        ).recordPhoto(PhotoAssetId("photo-1"))

        assertFailsWith<IllegalArgumentException> {
            roll.recordPhoto(PhotoAssetId("photo-1"))
        }
    }
}
