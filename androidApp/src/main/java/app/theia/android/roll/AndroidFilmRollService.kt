package app.theia.android.roll

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.theia.android.album.AlbumMediaKind
import app.theia.android.album.AlbumMetadataRepository
import app.theia.android.album.AlbumPhoto
import app.theia.domain.catalog.BuiltInFilmVisualProfiles
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmRollCapacity
import app.theia.domain.model.FilmRollId
import app.theia.domain.model.FilmRollRecord
import app.theia.domain.model.GalleryLocation
import app.theia.domain.model.PhotoAsset
import app.theia.domain.model.PhotoAssetId
import app.theia.domain.port.CapturedPhoto
import app.theia.domain.port.DevelopedPhoto
import app.theia.domain.port.ImageProcessingPort
import app.theia.domain.port.MediaLibraryPort
import app.theia.domain.port.OriginalPhotoSourcePort
import app.theia.domain.usecase.CreateFilmRollUseCase
import app.theia.domain.usecase.DevelopFilmRollUseCase
import app.theia.domain.usecase.DevelopPhotoInRollUseCase
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sqrt
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resumable local roll lifecycle. Captures and all gallery bytes stay on this device. */
class AndroidFilmRollService(
    context: Context,
    private val album: AlbumMetadataRepository,
    private val rolls: AndroidFilmRollRepository = AndroidFilmRollRepository(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("theia_film_rolls", Context.MODE_PRIVATE)
    private val originals = ContentResolverOriginalPhotoSource(appContext)
    private val imageProcessor = ProfiledBitmapImageProcessor(appContext)
    private val mediaLibrary = MediaStorePhotoLibrary(appContext)

    suspend fun createRoll(
        recipe: CaptureRecipe,
        capacity: FilmRollCapacity,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): FilmRollRecord = withContext(Dispatchers.IO) {
        val id = FilmRollId("roll-${UUID.randomUUID()}")
        val created = CreateFilmRollUseCase(rolls).execute(id, recipe, capacity, createdAtEpochMillis)
        check(preferences.edit().putString(KEY_ACTIVE_ROLL, id.value).commit()) { "Could not activate film roll" }
        created
    }

    suspend fun allRolls(): List<FilmRollRecord> = withContext(Dispatchers.IO) { rolls.all() }

    suspend fun findRoll(id: FilmRollId): FilmRollRecord? = withContext(Dispatchers.IO) { rolls.find(id) }

    suspend fun activeRoll(): FilmRollRecord? = withContext(Dispatchers.IO) {
        val id = preferences.getString(KEY_ACTIVE_ROLL, null)?.let(::FilmRollId) ?: return@withContext null
        val record = rolls.find(id) ?: return@withContext null
        if (record.roll.developedAtEpochMillis != null || record.roll.photoIds.size >= record.roll.capacity.exposures) null else record
    }

    suspend fun setActiveRoll(id: FilmRollId) = withContext(Dispatchers.IO) {
        val record = rolls.find(id) ?: error("Film roll does not exist")
        require(record.roll.developedAtEpochMillis == null && record.roll.photoIds.size < record.roll.capacity.exposures) {
            "Only an open film roll with remaining exposures can be active"
        }
        check(preferences.edit().putString(KEY_ACTIVE_ROLL, id.value).commit()) { "Could not activate film roll" }
    }

    suspend fun remainingExposures(): Int? = activeRoll()?.let { it.roll.capacity.exposures - it.roll.photoIds.size }

    /** Adds the just-saved gallery original to the active roll. A null result means no roll is active. */
    suspend fun recordCapture(
        originalUri: Uri,
        recipe: CaptureRecipe,
        capturedAtEpochMillis: Long = System.currentTimeMillis(),
        developInstantly: Boolean = false,
    ): PhotoAsset? = withContext(Dispatchers.IO) {
        val activeId = preferences.getString(KEY_ACTIVE_ROLL, null)?.let(::FilmRollId) ?: return@withContext null
        val photoId = PhotoAssetId(originalUri.toString())
        val photo = rolls.withRollLock(activeId) {
            val record = rolls.find(activeId) ?: error("Active film roll does not exist")
            require(record.recipe == recipe) { "The active roll uses a fixed capture recipe" }
            val asset = PhotoAsset(
                id = photoId,
                recipe = record.recipe,
                rollId = record.roll.id,
                capturedAtEpochMillis = capturedAtEpochMillis,
                originalGalleryLocation = GalleryLocation(originalUri.toString()),
            )
            rolls.save(record.append(asset))
            asset
        }
        val albumPhoto = AlbumPhoto(
            id = photo.id.value,
            capturedAtEpochMillis = photo.capturedAtEpochMillis,
            originalUri = originalUri.toString(),
            cameraBodyId = recipe.bodyId.value,
            lensId = recipe.lensId.value,
            filmStockId = recipe.filmStockId.value,
            rollId = activeId.value,
            mediaKind = AlbumMediaKind.PHOTO,
        )
        val prior = album.all().firstOrNull { it.id == albumPhoto.id }
        album.upsert(prior?.copy(rollId = activeId.value) ?: albumPhoto)

        if (developInstantly) {
            val revealed = DevelopPhotoInRollUseCase(rolls, originals, imageProcessor, mediaLibrary)
                .execute(activeId, photo.id)
            syncDevelopedPhoto(revealed)
        }
        photo
    }

    /** A completed exposure is visible immediately; originals on an undeveloped roll stay hidden. */
    suspend fun visiblePhotoIds(): Set<String> = withContext(Dispatchers.IO) {
        rolls.all().flatMap { it.visiblePhotos() }.mapTo(mutableSetOf()) { it.id.value }
    }

    /** Full development is allowed only when the roll is full; early development is always explicit. */
    suspend fun developRoll(id: FilmRollId, allowEarly: Boolean): FilmRollRecord = withContext(Dispatchers.IO) {
        val result = DevelopFilmRollUseCase(rolls, originals, imageProcessor, mediaLibrary)
            .execute(id, System.currentTimeMillis(), allowEarly = allowEarly)
        result.photos.forEach(::syncDevelopedPhoto)
        if (preferences.getString(KEY_ACTIVE_ROLL, null) == id.value) {
            preferences.edit().remove(KEY_ACTIVE_ROLL).commit()
        }
        result
    }

    private fun syncDevelopedPhoto(photo: PhotoAsset) {
        val previous = album.all().firstOrNull { it.id == photo.id.value }
        val updated = (previous ?: AlbumPhoto(
            id = photo.id.value,
            capturedAtEpochMillis = photo.capturedAtEpochMillis,
            originalUri = photo.originalGalleryLocation.value,
            cameraBodyId = photo.recipe.bodyId.value,
            lensId = photo.recipe.lensId.value,
            filmStockId = photo.recipe.filmStockId.value,
            rollId = photo.rollId?.value,
        )).copy(developedUri = photo.developedGalleryLocation?.value)
        album.upsert(updated)
    }

    private companion object {
        const val KEY_ACTIVE_ROLL = "active_roll_id"
    }
}

private class ContentResolverOriginalPhotoSource(context: Context) : OriginalPhotoSourcePort {
    private val appContext = context.applicationContext

    override suspend fun loadOriginal(location: GalleryLocation): CapturedPhoto = withContext(Dispatchers.IO) {
        val uri = Uri.parse(location.value)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val decodedBounds = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
            bounds.outWidth > 0 && bounds.outHeight > 0
        } ?: false
        check(decodedBounds) { "Could not read original photo dimensions" }
        CapturedPhoto(
            handle = uri.toString(),
            capturedAtEpochMillis = System.currentTimeMillis(),
            widthPixels = bounds.outWidth.coerceAtLeast(1),
            heightPixels = bounds.outHeight.coerceAtLeast(1),
        )
    }
}

/** Deterministic CPU renderer for the resolved film/body/lens profile. */
private class ProfiledBitmapImageProcessor(context: Context) : ImageProcessingPort {
    private val appContext = context.applicationContext

    override suspend fun developPhoto(photo: CapturedPhoto, recipe: CaptureRecipe): DevelopedPhoto = withContext(Dispatchers.IO) {
        val visual = requireNotNull(BuiltInFilmVisualProfiles.resolve(recipe)) {
            "No built-in visual profile exists for this capture recipe"
        }
        val sourceUri = Uri.parse(photo.handle)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val decodedBounds = appContext.contentResolver.openInputStream(sourceUri)?.use {
            BitmapFactory.decodeStream(it, null, options)
            options.outWidth > 0 && options.outHeight > 0
        } ?: false
        check(decodedBounds) { "Could not read the original photo dimensions" }
        var sampleSize = 1
        while (maxOf(options.outWidth / sampleSize, options.outHeight / sampleSize) > MAX_DEVELOPED_EDGE) sampleSize *= 2
        val source = appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("Could not decode the original photo")

        val outputPixels = transform(source, visual)
        val developed = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        developed.setPixels(outputPixels, 0, source.width, 0, 0, source.width, source.height)
        if (source !== developed) source.recycle()

        val output = File.createTempFile("theia-developed-", ".jpg", appContext.cacheDir)
        output.outputStream().use { stream ->
            check(developed.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "Could not encode developed image" }
        }
        developed.recycle()
        val exif = ExifInterface(output.absolutePath)
        exif.setAttribute(
            ExifInterface.TAG_USER_COMMENT,
            JSONObject()
                .put("version", 1)
                .put("recipe", recipe.id.value)
                .put("filmProfile", visual.filmProfileId)
                .put("profileVersion", visual.filmProfileVersion)
                .put("filter_applied", true)
                .put("filter_applied_by", "theia")
                .put("filter_profile_id", visual.filmProfileId)
                .put("filter_profile_version", visual.filmProfileVersion)
                .toString(),
        )
        exif.saveAttributes()
        DevelopedPhoto(output.absolutePath)
    }

    private fun transform(source: Bitmap, visual: app.theia.domain.model.ResolvedVisualParameters): IntArray {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val output = IntArray(input.size)
        val exposureGain = 2.0.pow(visual.exposureBiasEv).toFloat()
        val seed = visual.filmProfileId.hashCode()
        val grainCell = 1 + (visual.grainScale * 12f).toInt()
        val radius = sqrt((width * width + height * height).toFloat()) / 2f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val original = input[index]
                var red = ((original shr 16) and 0xff).toFloat() * exposureGain
                var green = ((original shr 8) and 0xff).toFloat() * exposureGain
                var blue = (original and 0xff).toFloat() * exposureGain

                val luminanceBefore = (0.2126f * red + 0.7152f * green + 0.0722f * blue).coerceIn(0f, 255f)
                val shadowWeight = 1f - luminanceBefore / 255f
                val warmthShift = (visual.warmth * 24f).toFloat()
                val greenMagentaShift = (visual.greenMagenta * 22f).toFloat()
                red += warmthShift + greenMagentaShift * 0.45f
                green -= greenMagentaShift
                blue -= warmthShift - greenMagentaShift * 0.45f
                val shadowBoost = (visual.shadowLift * shadowWeight * 255f).toFloat()
                red += shadowBoost
                green += shadowBoost
                blue += shadowBoost

                val contrast = visual.contrast.toFloat()
                red = (red - 128f) * contrast + 128f
                green = (green - 128f) * contrast + 128f
                blue = (blue - 128f) * contrast + 128f
                val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue).coerceIn(0f, 255f)
                val saturation = visual.saturation.toFloat()
                red = luminance + (red - luminance) * saturation
                green = luminance + (green - luminance) * saturation
                blue = luminance + (blue - luminance) * saturation

                val normalizedHighlight = (luminance / 255f).coerceIn(0f, 1f)
                val highlightCompression = 1f - visual.highlightRollOff.toFloat() * 0.55f
                if (normalizedHighlight > 0.62f) {
                    val compressed = (0.62f + (normalizedHighlight - 0.62f) * highlightCompression) / normalizedHighlight
                    red *= compressed
                    green *= compressed
                    blue *= compressed
                }
                val highGlow = ((normalizedHighlight - 0.68f).coerceAtLeast(0f) / 0.32f)
                red += highGlow * (visual.halation * 34f + visual.bloom * 20f).toFloat()
                green += highGlow * (visual.halation * 15f + visual.bloom * 20f).toFloat()
                blue += highGlow * (visual.halation * 5f + visual.bloom * 20f).toFloat()

                val nx = (x - width / 2f) / radius
                val ny = (y - height / 2f) / radius
                val vignetteFactor = (1f - visual.vignette.toFloat() * (nx * nx + ny * ny)).coerceAtLeast(0.2f)
                red *= vignetteFactor
                green *= vignetteFactor
                blue *= vignetteFactor

                if (visual.grainIntensity > 0.0) {
                    val cellX = x / grainCell
                    val cellY = y / grainCell
                    val noise = deterministicNoise(cellX, cellY, seed) * visual.grainIntensity.toFloat() * 22f
                    red += noise
                    green += noise
                    blue += noise
                }

                val blurAmount = visual.edgeSoftness.toFloat().coerceIn(0f, 0.5f)
                val sharpenAmount = (visual.sharpness.toFloat() - 1f).coerceIn(-0.5f, 0.5f)
                if (blurAmount > 0f || sharpenAmount != 0f) {
                    val average = neighborLuminance(input, width, height, x, y)
                    val detail = luminance - average
                    val adjustment = detail * sharpenAmount - detail * blurAmount
                    red += adjustment
                    green += adjustment
                    blue += adjustment
                }

                val alpha = original and -0x1000000
                output[index] = alpha or (clamp8(red) shl 16) or (clamp8(green) shl 8) or clamp8(blue)
            }
        }
        return output
    }

    private fun neighborLuminance(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Float {
        var total = 0f
        var count = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = (x + dx).coerceIn(0, width - 1)
            val ny = (y + dy).coerceIn(0, height - 1)
            val pixel = pixels[ny * width + nx]
            total += 0.2126f * ((pixel shr 16) and 0xff) + 0.7152f * ((pixel shr 8) and 0xff) + 0.0722f * (pixel and 0xff)
            count++
        }
        return total / count
    }

    private fun deterministicNoise(x: Int, y: Int, seed: Int): Float {
        var value = seed xor (x * 0x45d9f3b) xor (y * 0x119de1f3)
        value = (value xor (value ushr 16)) * 0x45d9f3b
        value = (value xor (value ushr 16)) * 0x45d9f3b
        value = value xor (value ushr 16)
        return (value and 0xffff) / 32767.5f - 1f
    }

    private fun clamp8(value: Float) = value.toInt().coerceIn(0, 255)

    private companion object {
        const val MAX_DEVELOPED_EDGE = 3000
    }
}

private class MediaStorePhotoLibrary(context: Context) : MediaLibraryPort {
    private val appContext = context.applicationContext

    override suspend fun saveOriginal(photo: CapturedPhoto): GalleryLocation = GalleryLocation(photo.handle)

    override suspend fun saveDeveloped(photo: DevelopedPhoto): GalleryLocation = withContext(Dispatchers.IO) {
        val file = File(photo.handle)
        require(file.isFile) { "Developed image file does not exist" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Theia_revealed_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Théia")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Théia")
                check(dir.exists() || dir.mkdirs()) { "Could not create Théia gallery folder" }
                put(MediaStore.Images.Media.DATA, File(dir, "Theia_revealed_${System.currentTimeMillis()}.jpg").absolutePath)
            }
        }
        val uri = appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create a developed gallery image")
        try {
            appContext.contentResolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("Could not write developed gallery image")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appContext.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
            GalleryLocation(uri.toString())
        } catch (failure: Throwable) {
            appContext.contentResolver.delete(uri, null, null)
            throw failure
        } finally {
            file.delete()
        }
    }

    override suspend fun delete(location: GalleryLocation) {
        val uri = Uri.parse(location.value)
        if (uri.scheme == "content") appContext.contentResolver.delete(uri, null, null)
        else File(location.value).delete()
    }
}
