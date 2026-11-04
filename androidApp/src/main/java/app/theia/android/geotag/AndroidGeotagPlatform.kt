package app.theia.android.geotag

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import app.theia.domain.model.GeotagMetadata
import app.theia.domain.model.GeotagMetadataId
import app.theia.domain.model.GalleryLocation
import app.theia.domain.model.PermissionStatus
import app.theia.domain.model.UserPreferences
import app.theia.domain.port.DeviceLocationPort
import app.theia.domain.port.DevicePermission
import app.theia.domain.port.LocalGeotaggedPhoto
import app.theia.domain.port.LocalPhotoLocationIndexPort
import app.theia.domain.port.PermissionPort
import app.theia.domain.port.PhotoMetadataPort
import app.theia.domain.usecase.AddGeotagUseCase
import app.theia.domain.usecase.LocalGeotaggedPhotosUseCase
import app.theia.domain.usecase.RemoveGeotagUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Runtime permission bridge; only the injected UI requester may open a permission prompt. */
class AndroidPermissionPort(
    context: Context,
    private val requestFromUi: suspend (DevicePermission) -> PermissionStatus,
) : PermissionPort {
    private val appContext = context.applicationContext

    override fun currentStatus(permission: DevicePermission): PermissionStatus {
        val manifestPermission = when (permission) {
            DevicePermission.CAMERA -> Manifest.permission.CAMERA
            DevicePermission.PHOTO_LIBRARY -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            DevicePermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
            DevicePermission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(appContext, manifestPermission) == PackageManager.PERMISSION_GRANTED) {
            return PermissionStatus.GRANTED
        }
        val requested = appContext.getSharedPreferences("theia_permission_requests", Context.MODE_PRIVATE)
            .getBoolean(manifestPermission, false)
        return if (requested) PermissionStatus.DENIED else PermissionStatus.NOT_REQUESTED
    }

    override suspend fun request(permission: DevicePermission): PermissionStatus = requestFromUi(permission)
}

/** Gets one device-local location fix. It never calls a geocoder, map, or remote service. */
class AndroidDeviceLocationPort(context: Context) : DeviceLocationPort {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override suspend fun currentLocation(): GeotagMetadata? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { providerName -> runCatching { locationManager.isProviderEnabled(providerName) }.getOrDefault(false) }
            ?: return null
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) currentLocationApi30(provider) else currentLocationLegacy(provider)
        } ?: return null
        return GeotagMetadata(
            id = GeotagMetadataId("geotag-${location.time}-${location.latitude}-${location.longitude}"),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            capturedAtEpochMillis = location.time.coerceAtLeast(0L),
        )
    }

    private suspend fun currentLocationApi30(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }
        try {
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(appContext),
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (error: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        } catch (error: IllegalArgumentException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun currentLocationLegacy(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (continuation.isActive) continuation.resume(location)
            }
            override fun onProviderDisabled(provider: String) {
                if (continuation.isActive) continuation.resume(null)
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (error: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        } catch (error: IllegalArgumentException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private companion object { const val LOCATION_TIMEOUT_MILLIS = 20_000L }
}

/** Writes GPS EXIF tags and a small app-private index; neither operation leaves the device. */
class AndroidPhotoGeotagMetadataPort(context: Context) : PhotoMetadataPort, LocalPhotoLocationIndexPort {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("theia_local_geotags", Context.MODE_PRIVATE)

    override suspend fun writeGeotag(location: GalleryLocation, geotag: GeotagMetadata) = withContext(Dispatchers.IO) {
        updateExif(location, geotag)
        synchronized(storageLock) {
            val records = readIndex().filterNot { it.photoLocation.value == location.value } + LocalGeotaggedPhoto(location, geotag)
            check(preferences.edit().putString(KEY_INDEX, records.toJsonArray().toString()).commit()) {
                "Could not save the local geotag index"
            }
        }
    }

    override suspend fun removeGeotag(location: GalleryLocation) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(location.value)
        val descriptor = requireNotNull(appContext.contentResolver.openFileDescriptor(uri, "rw")) { "Could not open photo metadata for editing" }
        descriptor.use { parcel ->
            val exif = ExifInterface(parcel.fileDescriptor)
            listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DOP,
            ).forEach { tag -> exif.setAttribute(tag, null) }
            exif.saveAttributes()
        }
        synchronized(storageLock) {
            val remaining = readIndex().filterNot { it.photoLocation.value == location.value }
            check(preferences.edit().putString(KEY_INDEX, remaining.toJsonArray().toString()).commit()) {
                "Could not remove the local geotag index entry"
            }
        }
    }

    override suspend fun photosWithGeotags(): List<LocalGeotaggedPhoto> = withContext(Dispatchers.IO) {
        synchronized(storageLock) { readIndex() }
    }

    private fun updateExif(location: GalleryLocation, geotag: GeotagMetadata) {
        val descriptor = requireNotNull(appContext.contentResolver.openFileDescriptor(Uri.parse(location.value), "rw")) {
            "Could not open photo metadata for editing"
        }
        descriptor.use { parcel ->
            val exif = ExifInterface(parcel.fileDescriptor)
            exif.setLatLong(geotag.latitude, geotag.longitude)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, SimpleDateFormat("yyyy:MM:dd", Locale.US).format(Date(geotag.capturedAtEpochMillis)))
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(geotag.capturedAtEpochMillis)))
            geotag.accuracyMeters?.let { exif.setAttribute(ExifInterface.TAG_GPS_DOP, it.toString()) }
            exif.saveAttributes()
        }
    }

    private fun readIndex(): List<LocalGeotaggedPhoto> = runCatching {
        val json = JSONArray(preferences.getString(KEY_INDEX, "[]"))
        buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                add(
                    LocalGeotaggedPhoto(
                        photoLocation = GalleryLocation(item.getString("uri")),
                        geotag = GeotagMetadata(
                            id = GeotagMetadataId(item.getString("id")),
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude"),
                            accuracyMeters = if (item.isNull("accuracy")) null else item.getDouble("accuracy"),
                            capturedAtEpochMillis = item.getLong("capturedAtEpochMillis"),
                        ),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun List<LocalGeotaggedPhoto>.toJsonArray() = JSONArray().also { array ->
        forEach { entry ->
            array.put(JSONObject()
                .put("uri", entry.photoLocation.value)
                .put("id", entry.geotag.id.value)
                .put("latitude", entry.geotag.latitude)
                .put("longitude", entry.geotag.longitude)
                .put("accuracy", entry.geotag.accuracyMeters ?: JSONObject.NULL)
                .put("capturedAtEpochMillis", entry.geotag.capturedAtEpochMillis))
        }
    }

    private companion object {
        const val KEY_INDEX = "photos"
        val storageLock = Any()
    }
}

/** Compose-friendly facade around the shared opt-in use cases. */
class AndroidGeotagService(
    context: Context,
    permissions: PermissionPort,
) {
    private val metadata = AndroidPhotoGeotagMetadataPort(context)
    private val add = AddGeotagUseCase(permissions, AndroidDeviceLocationPort(context), metadata)
    private val remove = RemoveGeotagUseCase(metadata)
    private val index = LocalGeotaggedPhotosUseCase(metadata)

    suspend fun addToPhoto(preferences: UserPreferences, photoUri: Uri): GeotagMetadata? =
        add.execute(preferences, GalleryLocation(photoUri.toString()))

    suspend fun removeFromPhoto(photoUri: Uri) = remove.execute(GalleryLocation(photoUri.toString()))

    suspend fun photosWithGeotags(): List<LocalGeotaggedPhoto> = index.execute()
}
