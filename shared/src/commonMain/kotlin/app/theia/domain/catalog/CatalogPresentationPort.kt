package app.theia.domain.catalog

import app.theia.domain.model.StableIdentifier

/** Presentation metadata kept alongside shared catalog entities without platform UI types. */
interface CatalogPresentationPort {
    fun artwork(identifier: StableIdentifier): CatalogArtwork?

    fun collection(key: String): FilmCollection?
}
