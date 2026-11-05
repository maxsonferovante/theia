package app.theia.android.workbench

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.theia.android.R
import app.theia.android.catalog.CatalogArtworkCard
import app.theia.domain.catalog.CatalogPresentationPort
import app.theia.domain.model.CameraBody
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStock
import app.theia.domain.model.Lens
import app.theia.domain.port.EquipmentCatalogPort

private val SolarGold = Color(0xFFEAAE35)
private val DeepSky = Color(0xFF182344)
private val CosmicViolet = Color(0xFF2D2D57)
private val Ivory = Color(0xFFE7D7B7)
private val Stone = Color(0xFFB6A58C)

/**
 * Lets the user inspect one body, lens, and film stock, then returns one valid recipe.
 * Display values are resolved from Android string resources when a matching key exists.
 */
@Composable
fun WorkbenchScreen(
    catalog: EquipmentCatalogPort,
    selectedRecipe: CaptureRecipe?,
    onRecipeSelected: (CaptureRecipe) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bodies = remember(catalog) { catalog.cameraBodies() }
    val lenses = remember(catalog) { catalog.lenses() }
    val filmStocks = remember(catalog) { catalog.filmStocks() }

    var bodyIndex by remember(catalog) {
        mutableIntStateOf(selectedRecipe.indexOfBody(bodies))
    }
    var lensIndex by remember(catalog) {
        mutableIntStateOf(selectedRecipe.indexOfLens(lenses))
    }
    var filmIndex by remember(catalog) {
        mutableIntStateOf(selectedRecipe.indexOfFilm(filmStocks))
    }

    LaunchedEffect(selectedRecipe, bodies, lenses, filmStocks) {
        selectedRecipe?.let { recipe ->
            bodyIndex = bodies.indexOfFirst { it.id == recipe.bodyId }.takeIf { it >= 0 } ?: bodyIndex
            lensIndex = lenses.indexOfFirst { it.id == recipe.lensId }.takeIf { it >= 0 } ?: lensIndex
            filmIndex = filmStocks.indexOfFirst { it.id == recipe.filmStockId }.takeIf { it >= 0 } ?: filmIndex
        }
    }

    val body = bodies.itemAt(bodyIndex)
    val lens = lenses.itemAt(lensIndex)
    val film = filmStocks.itemAt(filmIndex)
    val context = LocalContext.current
    val presentation = catalog as? CatalogPresentationPort
    val bodyArtwork = body?.let { presentation?.artwork(it.id) }
    val lensArtwork = lens?.let { presentation?.artwork(it.id) }
    val filmArtwork = film?.let { presentation?.artwork(it.id) }

    MaterialTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = DeepSky,
            contentColor = Ivory,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.workbench_title),
                        color = Ivory,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (onBack != null) {
                        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.back_to_preview))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.workbench_instructions),
                    color = Stone,
                    fontSize = 15.sp,
                )

                EquipmentPicker(
                    title = stringResource(R.string.workbench_body),
                    itemName = body?.let { context.resolveCatalogKey(it.nameKey) },
                    description = body?.let { context.resolveCatalogKey(it.descriptionKey) },
                    artworkKey = bodyArtwork?.artworkKey,
                    visualDescription = bodyArtwork?.let { context.resolveCatalogKey(it.visualCharacteristicsKey) },
                    currentPosition = bodyIndex,
                    itemCount = bodies.size,
                    previousAction = { bodyIndex = bodies.previousIndex(bodyIndex) },
                    nextAction = { bodyIndex = bodies.nextIndex(bodyIndex) },
                )
                EquipmentPicker(
                    title = stringResource(R.string.workbench_lens),
                    itemName = lens?.let { context.resolveCatalogKey(it.nameKey) },
                    description = lens?.let { context.resolveCatalogKey(it.descriptionKey) },
                    artworkKey = lensArtwork?.artworkKey,
                    visualDescription = lensArtwork?.let { context.resolveCatalogKey(it.visualCharacteristicsKey) },
                    currentPosition = lensIndex,
                    itemCount = lenses.size,
                    previousAction = { lensIndex = lenses.previousIndex(lensIndex) },
                    nextAction = { lensIndex = lenses.nextIndex(lensIndex) },
                )
                EquipmentPicker(
                    title = stringResource(R.string.workbench_film),
                    itemName = film?.let { context.resolveCatalogKey(it.nameKey) },
                    description = film?.let { context.resolveCatalogKey(it.descriptionKey) },
                    artworkKey = filmArtwork?.artworkKey,
                    visualDescription = filmArtwork?.let { context.resolveCatalogKey(it.visualCharacteristicsKey) },
                    collection = film?.let {
                        presentation?.collection(it.collectionKey)?.let { collection ->
                            context.resolveCatalogKey(collection.nameKey)
                        } ?: context.resolveCatalogKey("catalog.collection.${it.collectionKey}.name")
                    },
                    currentPosition = filmIndex,
                    itemCount = filmStocks.size,
                    previousAction = { filmIndex = filmStocks.previousIndex(filmIndex) },
                    nextAction = { filmIndex = filmStocks.nextIndex(filmIndex) },
                )

                val recipe = if (body != null && lens != null && film != null) {
                    CaptureRecipe(
                        bodyId = body.id,
                        lensId = lens.id,
                        filmStockId = film.id,
                    )
                } else {
                    null
                }
                val selectedStateDescription = stringResource(
                    if (recipe != null && selectedRecipe == recipe) R.string.workbench_state_selected else R.string.workbench_state_not_selected,
                )

                Button(
                    onClick = { recipe?.let(onRecipeSelected) },
                    enabled = recipe != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = context.getString(R.string.workbench_use_selected_setup)
                            stateDescription = selectedStateDescription
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SolarGold,
                        contentColor = DeepSky,
                        disabledContainerColor = SolarGold.copy(alpha = 0.35f),
                        disabledContentColor = Ivory.copy(alpha = 0.65f),
                    ),
                ) {
                    Text(
                        text = stringResource(
                            if (selectedRecipe == recipe) R.string.workbench_selected_setup else R.string.workbench_use_setup,
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EquipmentPicker(
    title: String,
    itemName: String?,
    description: String?,
    artworkKey: String?,
    visualDescription: String?,
    currentPosition: Int,
    itemCount: Int,
    previousAction: () -> Unit,
    nextAction: () -> Unit,
    collection: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CosmicViolet, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = SolarGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.workbench_item_counter, if (itemCount == 0) 0 else currentPosition + 1, itemCount),
                color = Stone,
                fontSize = 12.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CatalogArtworkCard(
                artworkKey = artworkKey ?: "artwork.collection.empty",
                visualDescription = visualDescription ?: stringResource(R.string.workbench_no_item),
                modifier = Modifier.weight(0.9f),
            )
            PickerArrow(
                label = stringResource(R.string.workbench_previous_item, title),
                symbol = "‹",
                enabled = itemCount > 1,
                onClick = previousAction,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = itemName ?: stringResource(R.string.workbench_no_item),
                    color = Ivory,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (collection != null) {
                    Text(
                        text = stringResource(R.string.workbench_collection_label, collection),
                        color = SolarGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    text = description ?: stringResource(R.string.workbench_empty_catalog),
                    color = Stone,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            PickerArrow(
                label = stringResource(R.string.workbench_next_item, title),
                symbol = "›",
                enabled = itemCount > 1,
                onClick = nextAction,
            )
        }
    }
}

@Composable
private fun PickerArrow(
    label: String,
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label },
    ) {
        Text(
            text = symbol,
            color = if (enabled) Ivory else Stone.copy(alpha = 0.4f),
            fontSize = 32.sp,
            lineHeight = 36.sp,
        )
    }
}

private fun Context.resolveCatalogKey(key: String): String {
    val resourceName = key.replace('.', '_').replace('-', '_')
    val resourceId = resources.getIdentifier(resourceName, "string", packageName)
    return if (resourceId == 0) key.toDisplayText() else getString(resourceId)
}

private fun String.toDisplayText(): String =
    replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }

private fun CaptureRecipe?.indexOfBody(items: List<CameraBody>): Int =
    this?.let { recipe -> items.indexOfFirst { it.id == recipe.bodyId }.takeIf { it >= 0 } } ?: 0

private fun CaptureRecipe?.indexOfLens(items: List<Lens>): Int =
    this?.let { recipe -> items.indexOfFirst { it.id == recipe.lensId }.takeIf { it >= 0 } } ?: 0

private fun CaptureRecipe?.indexOfFilm(items: List<FilmStock>): Int =
    this?.let { recipe -> items.indexOfFirst { it.id == recipe.filmStockId }.takeIf { it >= 0 } } ?: 0

private fun <T> List<T>.itemAt(index: Int): T? = getOrNull(index.coerceAtLeast(0))

private fun <T> List<T>.previousIndex(index: Int): Int =
    if (isEmpty()) 0 else (index - 1 + size) % size

private fun <T> List<T>.nextIndex(index: Int): Int =
    if (isEmpty()) 0 else (index + 1) % size
