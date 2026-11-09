package app.theia.domain.catalog

import app.theia.domain.model.CameraBody
import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.FilmStock
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.Lens
import app.theia.domain.model.LensId
import app.theia.domain.model.StableIdentifier
import app.theia.domain.port.EquipmentCatalogPort

data class FilmCollection(
    val key: String,
    val nameKey: String,
    val descriptionKey: String,
    val artworkKey: String,
)

data class CatalogArtwork(
    val artworkKey: String,
    val visualCharacteristicsKey: String,
    val visualCharacteristics: String,
) {
    init {
        require(artworkKey.isNotBlank()) { "Artwork keys cannot be blank" }
        require(visualCharacteristicsKey.isNotBlank()) { "Visual characteristic keys cannot be blank" }
        require(visualCharacteristics.isNotBlank()) { "Visual characteristics cannot be blank" }
    }
}

data class CatalogEntry<T>(
    val item: T,
    val artwork: CatalogArtwork,
    val isUnlocked: Boolean = true,
)

/**
 * A complete starter catalog with stable IDs and presentation metadata.
 *
 * Artwork keys are stable asset identifiers. UI layers resolve them to the
 * platform's original vector or raster artwork without adding platform types
 * to the shared domain module.
 */
object BuiltInEquipmentCatalog : EquipmentCatalogPort, CatalogPresentationPort {
    val collections: List<FilmCollection> = listOf(
        FilmCollection("sunlit", "catalog.collection.sunlit.name", "catalog.collection.sunlit.description", "artwork.collection.sunlit"),
        FilmCollection("portrait", "catalog.collection.portrait.name", "catalog.collection.portrait.description", "artwork.collection.portrait"),
        FilmCollection("monochrome", "catalog.collection.monochrome.name", "catalog.collection.monochrome.description", "artwork.collection.monochrome"),
        FilmCollection("cinema", "catalog.collection.cinema.name", "catalog.collection.cinema.description", "artwork.collection.cinema"),
        FilmCollection("instant", "catalog.collection.instant.name", "catalog.collection.instant.description", "artwork.collection.instant"),
        FilmCollection("night", "catalog.collection.night.name", "catalog.collection.night.description", "artwork.collection.night"),
        FilmCollection("travel", "catalog.collection.travel.name", "catalog.collection.travel.description", "artwork.collection.travel"),
        FilmCollection("experimental", "catalog.collection.experimental.name", "catalog.collection.experimental.description", "artwork.collection.experimental"),
    )

    val cameraBodyEntries: List<CatalogEntry<CameraBody>> = listOf(
        body("helia", "Helia", "A sun-warmed rangefinder that lifts highlights and gives skin a soft golden edge.", "A brass-ringed square rangefinder with a small dawn disc on its prism."),
        body("celeste", "Celeste", "A balanced everyday body with clear color separation and open shadow detail.", "A blue enamel body framed by a fine ivory top plate and star-shaped finder."),
        body("nocturne", "Nocturne", "A low-light body that protects bright points and deepens blue shadows.", "A midnight-blue body with a crescent meter window and a single copper dial."),
        body("terra", "Terra", "A tactile travel body that brings earthy reds and muted greens forward.", "A terracotta leather body with a bronze shutter ring and engraved horizon line."),
        body("ivory", "Ivory", "A gentle portrait body that favors cream highlights and restrained contrast.", "An ivory porcelain body with an olive-green finder frame and gold nameplate."),
        body("orbit", "Orbit", "A precise studio body with crisp detail and a subtle cool color bias.", "A compact cosmic-violet body with concentric silver rings around the lens mount."),
    )

    val lensEntries: List<CatalogEntry<Lens>> = listOf(
        lens("aurora", "Aurora 35", "A luminous wide-normal lens with warm flare and softly rounded highlights.", "A short gold barrel with a sunburst aperture ring and amber front glass."),
        lens("portrait", "Portrait 58", "A portrait lens with gentle falloff, smooth backgrounds, and natural skin tones.", "A long ivory barrel with a narrow copper focus band and pale rose glass."),
        lens("macro", "Petal Macro", "A close-focus lens that resolves fine texture while keeping color transitions calm.", "A short olive barrel with a scalloped focus grip and mint-green front element."),
        lens("halo", "Halo 50", "A standard lens with a delicate glow around bright subjects and clean midtones.", "A bronze barrel with a luminous glass ring and engraved halo marks."),
        lens("wide", "Horizon 24", "An expansive lens that keeps corners legible and gives skies extra breathing room.", "A broad blue barrel with a curved horizon engraving and cobalt glass."),
        lens("tele", "Farpoint 85", "A compact telephoto lens that compresses scenes and separates distant details.", "A tall violet barrel with stepped rings and a small silver distance scale."),
        lens("soft-focus", "Cloud 70", "A diffusion lens with a gauzy highlight bloom and low-contrast transitions.", "A cream barrel with a translucent lilac filter ring and cloud etching."),
        lens("tilt", "Prism Tilt", "A selective-focus lens with a narrow plane of detail and playful edge softness.", "A faceted bronze barrel with a violet prism window and offset tilt markings."),
        lens("biscoito-32", "Biscoito 32", "A compact lens with a clear center, soft edges and a nostalgic vignette.", "A slim dark round lens with a cream ring and small violet glass center."),
    )

    val filmEntries: List<CatalogEntry<FilmStock>> = listOf(
        film("sunlit", "apricot-noon", "Apricot Noon", "Warm amber highlights, clear skies, and gentle grain for bright open shade.", "A pale apricot frame with a radiating noon sun and fine cream grain."),
        film("sunlit", "wheatfield", "Wheatfield", "Honeyed yellows and quiet olive shadows for late-summer landscapes.", "A wheat-gold field cut by thin olive rows beneath a small white sun."),
        film("sunlit", "glasshouse", "Glasshouse", "Clean greens and pearly highlights with a cool reflection through the mids.", "A mint greenhouse arch reflecting a gold beam across translucent panes."),
        film("sunlit", "citrus-paper", "Citrus Paper", "Bright citrus color, soft contrast, and a lightly faded paper finish.", "Layered lemon and tangerine circles over a warm ivory paper texture."),

        film("portrait", "porcelain-hour", "Porcelain Hour", "Creamy skin tones, pale rose shadows, and smooth highlight rolloff.", "A porcelain oval portrait silhouette surrounded by a rose-gold vignette."),
        film("portrait", "rose-window", "Rose Window", "Soft magenta mids and luminous reds with a calm, flattering contrast.", "A stained-glass rose lattice with pink panes and a narrow gold border."),
        film("portrait", "velvet-light", "Velvet Light", "Deep wine shadows and satin skin highlights for intimate portraits.", "A burgundy velvet curtain opening onto a warm ivory highlight."),
        film("portrait", "pearl-garden", "Pearl Garden", "Pastel greens and delicate blush tones with a pearlescent glow.", "Pearl-like droplets over sage leaves with a faint peach halo."),

        film("monochrome", "silvergrain", "Silvergrain", "Neutral silver monochrome with visible fine grain and crisp midtone detail.", "Silver halide crystals arranged in a diagonal metallic grain field."),
        film("monochrome", "ash-archive", "Ash Archive", "Soft charcoal blacks and warm paper whites for a quiet archival print.", "A charcoal frame with an ivory contact-sheet edge and dust flecks."),
        film("monochrome", "blue-hour-negative", "Blue Hour Negative", "Cool graphite shadows and bright whites with a restrained blue cast.", "A graphite moon over a blue-black gradient with narrow white scratches."),
        film("monochrome", "tungsten-noir", "Tungsten Noir", "Hard noir contrast, dense blacks, and clean highlights for night scenes.", "A sharp beam of white light cut through a black and bronze geometry."),

        film("cinema", "copper-gate", "Copper Gate", "Copper highlights, teal shadows, and a wide cinematic tonal range.", "A copper film gate with a teal beam crossing its horizontal sprockets."),
        film("cinema", "faded-reel", "Faded Reel", "Faded reds and lifted blacks with the gentle drift of an old projection.", "A worn coral frame with a rotating reel shadow and ivory dust marks."),
        film("cinema", "neon-frame", "Neon Frame", "Vivid neon accents against deep navy with restrained highlight bloom.", "A violet neon rectangle with one coral edge against a midnight field."),
        film("cinema", "last-screening", "Last Screening", "Muted cinema color, deep burgundy shadows, and a soft amber spotlight.", "An empty theater aisle ending at a small amber screen."),

        film("instant", "pocket-sun", "Pocket Sun", "Cream borders, sunny reds, and a quick-print softness with cheerful grain.", "A miniature instant-film card with a yellow sun and hand-drawn edge."),
        film("instant", "sugar-flash", "Sugar Flash", "Direct-flash sparkle, pastel shadows, and a bright white print finish.", "A star-shaped flash over a lilac instant frame with rounded corners."),
        film("instant", "sunday-print", "Sunday Print", "Warm family-album color with lifted shadows and gentle paper fade.", "A tiny pressed flower and handwritten date on a cream print border."),
        film("instant", "blue-envelope", "Blue Envelope", "Cool cyan whites and rose shadows with the charm of a kept note.", "A folded sky-blue envelope holding a small blush instant photograph."),

        film("night", "starling", "Starling", "Blue-black skies, bright point lights, and softly glowing warm windows.", "Three gold window lights below a field of cobalt stars."),
        film("night", "cobalt-afterdark", "Cobalt Afterdark", "Cobalt shadows and restrained violet highlights for deep evening scenes.", "A cobalt skyline reflected in a violet crescent of water."),
        film("night", "sodium-glow", "Sodium Glow", "Amber streetlight tones with deep plum shadows and calm skin highlights.", "An amber streetlamp halo suspended over a plum-black avenue."),
        film("night", "moonlit-120", "Moonlit 120", "Soft moonlit blues and visible medium-format grain for still night portraits.", "A silver moon partially veiled by blue clouds above a grain-textured frame."),

        film("travel", "atlas-color", "Atlas Color", "Balanced travel color with crisp blue distance and warm architecture.", "A folded map crossed by a gold route line and a blue compass mark."),
        film("travel", "coastal-air", "Coastal Air", "Sea-glass blues and sun-bleached neutrals for coastlines and open skies.", "A pale turquoise tide line crossing a sand-colored frame."),
        film("travel", "old-station", "Old Station", "Faded station greens, rust reds, and softened contrast for journeys.", "A small vintage station clock above rust-red tracks and moss-green tile."),
        film("travel", "red-earth", "Red Earth", "Terracotta earth, dusty greens, and a dry, tactile print surface.", "A red mesa silhouette with olive scrub and engraved contour lines."),

        film("experimental", "prism-bleach", "Prism Bleach", "High-energy color separation, bright whites, and a washed chemical edge.", "Three overlapping magenta, cyan, and gold prisms with bleached paper gaps."),
        film("experimental", "violet-haze", "Violet Haze", "Lilac shadows and hazy amber highlights with a deliberately dreamy cast.", "A violet fog ring crossing a small amber horizon disc."),
        film("experimental", "solarized", "Solarized", "Inverted edge colors, luminous outlines, and unexpected tonal reversals.", "A gold contour drawing inverted across a midnight-violet plate."),
        film("experimental", "paper-moon", "Paper Moon", "Hand-tinted blues, cream highlights, and a cut-paper collage character.", "A layered paper moon with ivory cutouts and a cobalt handmade edge."),
    )

    override fun cameraBodies(): List<CameraBody> = cameraBodyEntries.map { it.item }
    override fun cameraBody(id: CameraBodyId): CameraBody? = cameraBodyEntries.firstOrNull { it.item.id == id }?.item
    override fun lenses(): List<Lens> = lensEntries.map { it.item }
    override fun lens(id: LensId): Lens? = lensEntries.firstOrNull { it.item.id == id }?.item
    override fun filmStocks(): List<FilmStock> = filmEntries.map { it.item }
    override fun filmStock(id: FilmStockId): FilmStock? = filmEntries.firstOrNull { it.item.id == id }?.item

    fun cameraBodyEntry(id: CameraBodyId): CatalogEntry<CameraBody>? = cameraBodyEntries.firstOrNull { it.item.id == id }
    fun lensEntry(id: LensId): CatalogEntry<Lens>? = lensEntries.firstOrNull { it.item.id == id }
    fun filmEntry(id: FilmStockId): CatalogEntry<FilmStock>? = filmEntries.firstOrNull { it.item.id == id }

    override fun artwork(identifier: StableIdentifier): CatalogArtwork? =
        cameraBodyEntries.firstOrNull { it.item.id.value == identifier.value }?.artwork
            ?: lensEntries.firstOrNull { it.item.id.value == identifier.value }?.artwork
            ?: filmEntries.firstOrNull { it.item.id.value == identifier.value }?.artwork

    override fun collection(key: String): FilmCollection? = collections.firstOrNull { it.key == key }

    private fun body(slug: String, name: String, visual: String, illustrationDescription: String) =
        CatalogEntry(
            item = CameraBody(
                id = CameraBodyId("body-$slug"),
                nameKey = "catalog.body.$slug.name",
                descriptionKey = "catalog.body.$slug.description",
            ),
            artwork = CatalogArtwork(
                artworkKey = "artwork.body.$slug",
                visualCharacteristicsKey = "catalog.body.$slug.visual",
                visualCharacteristics = "$name: $visual $illustrationDescription",
            ),
        )

    private fun lens(slug: String, name: String, visual: String, illustrationDescription: String) =
        CatalogEntry(
            item = Lens(
                id = LensId("lens-$slug"),
                nameKey = "catalog.lens.$slug.name",
                descriptionKey = "catalog.lens.$slug.description",
            ),
            artwork = CatalogArtwork(
                artworkKey = "artwork.lens.$slug",
                visualCharacteristicsKey = "catalog.lens.$slug.visual",
                visualCharacteristics = "$name: $visual $illustrationDescription",
            ),
        )

    private fun film(collection: String, slug: String, name: String, visual: String, artwork: String) =
        CatalogEntry(
            item = FilmStock(
                id = FilmStockId("film-$slug"),
                nameKey = "catalog.film.$slug.name",
                descriptionKey = "catalog.film.$slug.description",
                collectionKey = collection,
            ),
            artwork = CatalogArtwork(
                artworkKey = "artwork.film.$slug",
                visualCharacteristicsKey = "catalog.film.$slug.visual",
                visualCharacteristics = "$name: $visual $artwork",
            ),
        )
}
