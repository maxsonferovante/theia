package app.theia.android.workbench

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.theia.android.R
import app.theia.android.catalog.CatalogArtworkCard
import app.theia.domain.catalog.CatalogPresentationPort
import app.theia.domain.model.CameraBody
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStock
import app.theia.domain.model.Lens
import app.theia.domain.port.EquipmentCatalogPort

private val PickerNavy = Color(0xFF182344)
private val PickerViolet = Color(0xFF2D2D57)
private val PickerGold = Color(0xFFEAAE35)
private val PickerIvory = Color(0xFFE7D7B7)
private val PickerStone = Color(0xFFB6A58C)

private enum class RecipePart { BODY, LENS, FILM }

private data class PickerItem(
    val id: String,
    val name: String,
    val description: String,
    val artworkKey: String,
    val visualDescription: String,
    val collection: String? = null,
)

/** Compact in-camera recipe editor. Selections stay local until the user applies them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePickerSheet(
    catalog: EquipmentCatalogPort,
    selectedRecipe: CaptureRecipe,
    onDismiss: () -> Unit,
    onDraftChange: (CaptureRecipe) -> Unit,
    onApply: (CaptureRecipe) -> Unit,
) {
    val context = LocalContext.current
    val presentation = catalog as? CatalogPresentationPort
    val bodies = remember(catalog) { catalog.cameraBodies().map { it.toPickerItem(context, presentation) } }
    val lenses = remember(catalog) { catalog.lenses().map { it.toPickerItem(context, presentation) } }
    val films = remember(catalog) { catalog.filmStocks().map { it.toPickerItem(context, presentation) } }
    var part by remember { mutableStateOf(RecipePart.BODY) }
    var bodyIndex by remember(selectedRecipe, bodies) { mutableIntStateOf(bodies.indexOfFirst { it.id == selectedRecipe.bodyId.value }.coerceAtLeast(0)) }
    var lensIndex by remember(selectedRecipe, lenses) { mutableIntStateOf(lenses.indexOfFirst { it.id == selectedRecipe.lensId.value }.coerceAtLeast(0)) }
    var filmIndex by remember(selectedRecipe, films) { mutableIntStateOf(films.indexOfFirst { it.id == selectedRecipe.filmStockId.value }.coerceAtLeast(0)) }
    val items = when (part) {
        RecipePart.BODY -> bodies
        RecipePart.LENS -> lenses
        RecipePart.FILM -> films
    }
    val selectedIndex = when (part) {
        RecipePart.BODY -> bodyIndex
        RecipePart.LENS -> lensIndex
        RecipePart.FILM -> filmIndex
    }
    val selectedItem = items.getOrNull(selectedIndex)
    val body = bodies.getOrNull(bodyIndex)
    val lens = lenses.getOrNull(lensIndex)
    val film = films.getOrNull(filmIndex)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PickerNavy,
        contentColor = PickerIvory,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.recipe_sheet_title), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.recipe_sheet_description), color = PickerStone, fontSize = 14.sp)

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipePart.entries.forEach { option ->
                    FilterChip(
                        selected = part == option,
                        onClick = { part = option },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PickerGold,
                            selectedLabelColor = PickerNavy,
                            containerColor = PickerViolet,
                            labelColor = PickerIvory,
                        ),
                        label = { Text(stringResource(option.labelResource())) },
                    )
                }
            }

            if (selectedItem != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CatalogArtworkCard(
                        artworkKey = selectedItem.artworkKey,
                        visualDescription = selectedItem.visualDescription,
                        modifier = Modifier.width(116.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        Text(selectedItem.name, color = PickerIvory, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        selectedItem.collection?.let { Text(it, color = PickerGold, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                        Text(selectedItem.description, color = PickerStone, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.workbench_item_counter, selectedIndex + 1, items.size), color = PickerStone, fontSize = 12.sp)
                    }
                }
            } else {
                Text(stringResource(R.string.workbench_empty_catalog), color = PickerStone)
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Card(
                        modifier = Modifier.width(168.dp).heightIn(min = 76.dp).clickable {
                            when (part) {
                                RecipePart.BODY -> bodyIndex = index
                                RecipePart.LENS -> lensIndex = index
                                RecipePart.FILM -> filmIndex = index
                            }
                            val draftBody = if (part == RecipePart.BODY) bodies.getOrNull(index) else body
                            val draftLens = if (part == RecipePart.LENS) lenses.getOrNull(index) else lens
                            val draftFilm = if (part == RecipePart.FILM) films.getOrNull(index) else film
                            if (draftBody != null && draftLens != null && draftFilm != null) {
                                onDraftChange(CaptureRecipe(
                                    bodyId = catalog.cameraBodies()[bodies.indexOf(draftBody)].id,
                                    lensId = catalog.lenses()[lenses.indexOf(draftLens)].id,
                                    filmStockId = catalog.filmStocks()[films.indexOf(draftFilm)].id,
                                ))
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected) PickerGold else PickerViolet),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                item.name,
                                color = if (selected) PickerNavy else PickerIvory,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.description,
                                color = if (selected) PickerNavy else PickerStone,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (body != null && lens != null && film != null) {
                Text(
                    stringResource(R.string.recipe_sheet_summary, body.name, lens.name, film.name),
                    color = PickerStone,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = {
                        onApply(CaptureRecipe(
                            bodyId = catalog.cameraBodies()[bodyIndex].id,
                            lensId = catalog.lenses()[lensIndex].id,
                            filmStockId = catalog.filmStocks()[filmIndex].id,
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = bodyIndex in bodies.indices && lensIndex in lenses.indices && filmIndex in films.indices,
                    colors = ButtonDefaults.buttonColors(containerColor = PickerGold, contentColor = PickerNavy),
                ) { Text(stringResource(R.string.recipe_sheet_apply)) }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = PickerIvory),
            ) {
                Text(stringResource(R.string.recipe_sheet_cancel))
            }
        }
    }
}

@Composable
private fun RecipePart.labelResource(): Int = when (this) {
    RecipePart.BODY -> R.string.workbench_body
    RecipePart.LENS -> R.string.workbench_lens
    RecipePart.FILM -> R.string.workbench_film
}

private fun CameraBody.toPickerItem(context: Context, presentation: CatalogPresentationPort?) = PickerItem(
    id = id.value,
    name = context.resolveCatalogKey(nameKey),
    description = context.resolveCatalogKey(descriptionKey),
    artworkKey = presentation?.artwork(id)?.artworkKey ?: "artwork.body.${id.value}",
    visualDescription = presentation?.artwork(id)?.visualCharacteristicsKey?.let(context::resolveCatalogKey) ?: context.resolveCatalogKey(nameKey),
)

private fun Lens.toPickerItem(context: Context, presentation: CatalogPresentationPort?) = PickerItem(
    id = id.value,
    name = context.resolveCatalogKey(nameKey),
    description = context.resolveCatalogKey(descriptionKey),
    artworkKey = presentation?.artwork(id)?.artworkKey ?: "artwork.lens.${id.value}",
    visualDescription = presentation?.artwork(id)?.visualCharacteristicsKey?.let(context::resolveCatalogKey) ?: context.resolveCatalogKey(nameKey),
)

private fun FilmStock.toPickerItem(context: Context, presentation: CatalogPresentationPort?) = PickerItem(
    id = id.value,
    name = context.resolveCatalogKey(nameKey),
    description = context.resolveCatalogKey(descriptionKey),
    artworkKey = presentation?.artwork(id)?.artworkKey ?: "artwork.film.${id.value}",
    visualDescription = presentation?.artwork(id)?.visualCharacteristicsKey?.let(context::resolveCatalogKey) ?: context.resolveCatalogKey(nameKey),
    collection = presentation?.collection(collectionKey)?.nameKey?.let(context::resolveCatalogKey),
)

private fun Context.resolveCatalogKey(key: String): String {
    val resourceName = key.replace('.', '_').replace('-', '_')
    val resourceId = resources.getIdentifier(resourceName, "string", packageName)
    return if (resourceId == 0) key.replace('_', ' ').replace('-', ' ') else getString(resourceId)
}
