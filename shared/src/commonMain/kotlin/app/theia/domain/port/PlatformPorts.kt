package app.theia.domain.port

import app.theia.domain.model.PermissionStatus

enum class DevicePermission {
    CAMERA,
    PHOTO_LIBRARY,
    MICROPHONE,
    LOCATION,
}

interface PermissionPort {
    fun currentStatus(permission: DevicePermission): PermissionStatus

    suspend fun request(permission: DevicePermission): PermissionStatus
}

enum class LifecycleState {
    ACTIVE,
    INACTIVE,
    BACKGROUND,
    TERMINATED,
}

fun interface LifecycleSubscription {
    fun cancel()
}

interface AppLifecyclePort {
    fun currentState(): LifecycleState

    fun observeState(listener: (LifecycleState) -> Unit): LifecycleSubscription
}
