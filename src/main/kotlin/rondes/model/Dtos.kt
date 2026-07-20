package rondes.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val badge: String, val pin: String)

@Serializable
data class LoginResponse(val token: String, val guardName: String, val role: String, val expiresAt: String)

@Serializable
data class ScanRequest(val tagUid: String, val scannedAt: String? = null)

@Serializable
data class ScanResponse(
    val roomId: Int,
    val roomName: String,
    val guardName: String,
    val scannedAt: String,
    val offlineSync: Boolean,
)

@Serializable
data class RoomStatusDto(
    val id: Int,
    val name: String,
    val building: String?,
    val floor: String?,
    val lastScanAt: String?,
    val lastGuardName: String?,
    val elapsedMinutes: Long?,
    val orangeThresholdMinutes: Int,
    val redThresholdMinutes: Int,
    val alertLevel: String,
    val patchUid: String?,
    val patchDamaged: Boolean,
)

@Serializable
data class RoomCreateRequest(
    val name: String,
    val building: String? = null,
    val floor: String? = null,
    val orangeThresholdMinutes: Int = 120,
    val redThresholdMinutes: Int = 360,
)

@Serializable
data class RoomUpdateRequest(
    val name: String? = null,
    val building: String? = null,
    val floor: String? = null,
    val orangeThresholdMinutes: Int? = null,
    val redThresholdMinutes: Int? = null,
)

@Serializable
data class PatchEnrollRequest(val tagUid: String, val roomId: Int)

@Serializable
data class PatchDamagedRequest(val damaged: Boolean)

@Serializable
data class PatchDto(
    val id: Int,
    val tagUid: String,
    val roomId: Int?,
    val roomName: String?,
    val active: Boolean,
    val damaged: Boolean,
)

@Serializable
data class HistoryEntryDto(
    val id: Int,
    val roomId: Int,
    val roomName: String,
    val guardName: String,
    val scannedAt: String,
    val offlineSync: Boolean,
)

@Serializable
data class GuardCreateRequest(val badge: String, val fullName: String, val pin: String, val role: String)

@Serializable
data class GuardDto(val id: Int, val badge: String, val fullName: String, val role: String, val active: Boolean)

@Serializable
data class ErrorResponse(val message: String)
