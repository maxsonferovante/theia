package app.theia.domain.catalog

import app.theia.domain.model.CaptureRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuiltInEquipmentCatalogTest {
    @Test
    fun providesCompleteUnlockedCatalogAcrossEightFilmCollections() {
        val catalog = BuiltInEquipmentCatalog

        assertEquals(6, catalog.cameraBodyEntries.size)
        assertEquals(8, catalog.lensEntries.size)
        assertTrue(catalog.filmEntries.size >= 30)
        assertEquals(8, catalog.collections.size)
        assertEquals(catalog.collections.map { it.key }.toSet(), catalog.filmEntries.map { it.item.collectionKey }.toSet())
        assertTrue(catalog.collections.all { collection -> catalog.filmEntries.count { it.item.collectionKey == collection.key } == 4 })
        assertTrue(catalog.cameraBodyEntries.all { it.isUnlocked })
        assertTrue(catalog.lensEntries.all { it.isUnlocked })
        assertTrue(catalog.filmEntries.all { it.isUnlocked })
    }

    @Test
    fun usesUniqueStableIdsLocalizationKeysAndArtworkKeys() {
        val catalog = BuiltInEquipmentCatalog

        assertEquals(catalog.cameraBodyEntries.size, catalog.cameraBodyEntries.map { it.item.id }.toSet().size)
        assertEquals(catalog.lensEntries.size, catalog.lensEntries.map { it.item.id }.toSet().size)
        assertEquals(catalog.filmEntries.size, catalog.filmEntries.map { it.item.id }.toSet().size)

        val allEntries = catalog.cameraBodyEntries + catalog.lensEntries + catalog.filmEntries
        val artworkKeys = allEntries.map { it.artwork.artworkKey } + catalog.collections.map { it.artworkKey }
        assertEquals(artworkKeys.size, artworkKeys.toSet().size)
        assertTrue(allEntries.all { it.artwork.visualCharacteristics.isNotBlank() })
        assertTrue(allEntries.all { it.artwork.visualCharacteristicsKey.isNotBlank() })
        val characteristics = allEntries.map { it.artwork.visualCharacteristics }
        assertEquals(characteristics.size, characteristics.toSet().size)
        val localizationKeys = catalog.cameraBodyEntries.flatMap { listOf(it.item.nameKey, it.item.descriptionKey, it.artwork.visualCharacteristicsKey) } +
            catalog.lensEntries.flatMap { listOf(it.item.nameKey, it.item.descriptionKey, it.artwork.visualCharacteristicsKey) } +
            catalog.filmEntries.flatMap { listOf(it.item.nameKey, it.item.descriptionKey, it.artwork.visualCharacteristicsKey) }
        assertEquals(localizationKeys.size, localizationKeys.toSet().size)
    }

    @Test
    fun exposesValidRecipesAndStableLookupResults() {
        val catalog = BuiltInEquipmentCatalog
        val firstBody = catalog.cameraBodies().first()
        val firstLens = catalog.lenses().first()
        val firstFilm = catalog.filmStocks().first()

        val recipe = CaptureRecipe(
            bodyId = firstBody.id,
            lensId = firstLens.id,
            filmStockId = firstFilm.id,
        )

        assertEquals(recipe.id, CaptureRecipe(firstBody.id, firstLens.id, firstFilm.id).id)
        assertEquals(firstBody, catalog.cameraBody(firstBody.id))
        assertEquals(firstLens, catalog.lens(firstLens.id))
        assertEquals(firstFilm, catalog.filmStock(firstFilm.id))
        assertNotNull(catalog.cameraBodyEntry(firstBody.id))
        assertNotNull(catalog.lensEntry(firstLens.id))
        assertNotNull(catalog.filmEntry(firstFilm.id))
        assertNotNull(catalog.artwork(firstBody.id))
        assertNotNull(catalog.artwork(firstLens.id))
        assertNotNull(catalog.artwork(firstFilm.id))
        assertNotNull(catalog.collection(firstFilm.collectionKey))
    }

    @Test
    fun catalogContentIsDeterministicAcrossReads() {
        val catalog = BuiltInEquipmentCatalog

        assertEquals(catalog.cameraBodyEntries, catalog.cameraBodyEntries)
        assertEquals(catalog.lensEntries, catalog.lensEntries)
        assertEquals(catalog.filmEntries, catalog.filmEntries)
        assertEquals(catalog.collections, catalog.collections)
        assertEquals(catalog.filmEntries.map { it.item.id.value }, catalog.filmEntries.map { it.item.id.value })
    }
}
