package app.theia.domain.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

const val CURRENT_DOMAIN_SCHEMA_VERSION: Int = 1

@Serializable
private data class VersionedEnvelope<T>(
    val schemaVersion: Int,
    val payload: T,
)

class UnsupportedSchemaVersionException(
    val actualVersion: Int,
    val supportedVersion: Int = CURRENT_DOMAIN_SCHEMA_VERSION,
) : IllegalArgumentException(
    "Unsupported domain schema version $actualVersion; this build supports version $supportedVersion",
)

class VersionedJsonCodec(
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    inline fun <reified T> encode(value: T): String = encode(serializer<T>(), value)

    fun <T> encode(serializer: KSerializer<T>, value: T): String =
        json.encodeToString(
            VersionedEnvelope.serializer(serializer),
            VersionedEnvelope(CURRENT_DOMAIN_SCHEMA_VERSION, value),
        )

    inline fun <reified T> decode(encoded: String): T = decode(serializer<T>(), encoded)

    fun <T> decode(deserializer: KSerializer<T>, encoded: String): T {
        val envelope = json.decodeFromString(VersionedEnvelope.serializer(deserializer), encoded)
        if (envelope.schemaVersion != CURRENT_DOMAIN_SCHEMA_VERSION) {
            throw UnsupportedSchemaVersionException(envelope.schemaVersion)
        }
        return envelope.payload
    }
}
