package app.theia.domain.port

import app.theia.domain.model.CameraBody
import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.FilmStock
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.Lens
import app.theia.domain.model.LensId

interface EquipmentCatalogPort {
    fun cameraBodies(): List<CameraBody>

    fun cameraBody(id: CameraBodyId): CameraBody?

    fun lenses(): List<Lens>

    fun lens(id: LensId): Lens?

    fun filmStocks(): List<FilmStock>

    fun filmStock(id: FilmStockId): FilmStock?
}
