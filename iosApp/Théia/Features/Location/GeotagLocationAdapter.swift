import CoreLocation
import Foundation
import SwiftUI

struct LocalGeotagCoordinate: Codable, Equatable, Sendable, Identifiable {
    var latitude: Double
    var longitude: Double
    var id: String { "\(latitude),\(longitude)" }

    init(latitude: Double, longitude: Double) {
        self.latitude = min(90, max(-90, latitude))
        self.longitude = min(180, max(-180, longitude))
    }
}

/// App-local location payload. Callers should create and persist this only after the user opts in.
struct LocalGeotagMetadata: Codable, Equatable, Sendable {
    let coordinate: LocalGeotagCoordinate
    let recordedAt: Date
    let precisionMeters: Double?

    var withoutLocation: LocalGeotagMetadata? { nil }

    func exportJSONData() throws -> Data {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(self)
    }
}

/// File-backed location storage. It has no sync or network operation by design.
protocol LocalGeotagMetadataRepository: Sendable {
    func save(_ metadata: LocalGeotagMetadata, for captureID: String) async throws
    func exportJSON(for captureID: String) async throws -> Data?
    func removeLocation(for captureID: String) async throws
    func removeAllLocations() async throws
}

actor JSONLocalGeotagMetadataRepository: LocalGeotagMetadataRepository {
    private let fileURL: URL

    init(fileURL: URL? = nil) {
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            self.fileURL = support.appendingPathComponent("Theia", isDirectory: true)
                .appendingPathComponent("geotags.json")
        }
    }

    func save(_ metadata: LocalGeotagMetadata, for captureID: String) async throws {
        var values = try load()
        values[captureID] = metadata
        try persist(values)
    }

    func exportJSON(for captureID: String) async throws -> Data? {
        guard let metadata = try load()[captureID] else { return nil }
        return try metadata.exportJSONData()
    }

    func removeLocation(for captureID: String) async throws {
        var values = try load()
        values.removeValue(forKey: captureID)
        try persist(values)
    }

    func removeAllLocations() async throws {
        try persist([:])
    }

    private func load() throws -> [String: LocalGeotagMetadata] {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return [:] }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([String: LocalGeotagMetadata].self, from: Data(contentsOf: fileURL))
    }

    private func persist(_ values: [String: LocalGeotagMetadata]) throws {
        var directory = fileURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var directoryValues = URLResourceValues()
        directoryValues.isExcludedFromBackup = true
        try? directory.setResourceValues(directoryValues)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        try encoder.encode(values).write(to: fileURL, options: .atomic)
        var fileValues = URLResourceValues()
        fileValues.isExcludedFromBackup = true
        var localFileURL = fileURL
        try? localFileURL.setResourceValues(fileValues)
    }
}

enum GeotagAuthorizationState: Equatable {
    case notRequested
    case awaitingUserDecision
    case authorized
    case denied
    case restricted
    case unavailable
}

/// One-shot location adapter. It never requests permission or location until an explicit call.
@MainActor
final class GeotagLocationAdapter: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var authorizationState: GeotagAuthorizationState = .notRequested
    @Published private(set) var latestMetadata: LocalGeotagMetadata?
    @Published private(set) var isRequestingLocation = false
    @Published private(set) var errorMessage: String?

    private let manager: CLLocationManager
    private var hasExplicitRequestPending = false
    private var pendingContinuation: CheckedContinuation<LocalGeotagMetadata?, Never>?

    override init() {
        manager = CLLocationManager()
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        synchronizeAuthorizationState()
    }

    /// Invoke only after the user explicitly enables or requests a photo geotag.
    func requestOneShotLocationAfterOptIn() async -> LocalGeotagMetadata? {
        await withCheckedContinuation { continuation in
            guard !hasExplicitRequestPending else { continuation.resume(returning: nil); return }
            pendingContinuation = continuation
            guard CLLocationManager.locationServicesEnabled() else {
                authorizationState = .unavailable
                errorMessage = NSLocalizedString("Location services are turned off on this device.", comment: "Location services unavailable")
                finishRequest(with: nil)
                return
            }
            hasExplicitRequestPending = true
            errorMessage = nil
            switch manager.authorizationStatus {
            case .authorizedAlways, .authorizedWhenInUse:
                authorizationState = .authorized
                requestLocationNow()
            case .notDetermined:
                authorizationState = .awaitingUserDecision
                manager.requestWhenInUseAuthorization()
            case .denied:
                authorizationState = .denied
                errorMessage = NSLocalizedString("Allow location access in Settings to add an optional geotag.", comment: "Location permission denied")
                finishRequest(with: nil)
            case .restricted:
                authorizationState = .restricted
                errorMessage = NSLocalizedString("Location access is restricted on this device.", comment: "Location permission restricted")
                finishRequest(with: nil)
            @unknown default:
                authorizationState = .unavailable
                finishRequest(with: nil)
            }
        }
    }

    /// Erases the adapter's in-memory location. Persisted metadata owners should replace their record with `nil`.
    func removeLocationFromMemory() {
        latestMetadata = nil
        errorMessage = nil
        hasExplicitRequestPending = false
        isRequestingLocation = false
        manager.stopUpdatingLocation()
        finishRequest(with: nil)
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        synchronizeAuthorizationState()
        guard hasExplicitRequestPending else { return }
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            authorizationState = .authorized
            requestLocationNow()
        case .denied:
            authorizationState = .denied
            errorMessage = NSLocalizedString("Allow location access in Settings to add an optional geotag.", comment: "Location permission denied")
            finishRequest(with: nil)
        case .restricted:
            authorizationState = .restricted
            errorMessage = NSLocalizedString("Location access is restricted on this device.", comment: "Location permission restricted")
            finishRequest(with: nil)
        case .notDetermined: break
        @unknown default:
            authorizationState = .unavailable
            finishRequest(with: nil)
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard hasExplicitRequestPending, let location = locations.last else { return }
        let metadata = LocalGeotagMetadata(
            coordinate: LocalGeotagCoordinate(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude
            ),
            recordedAt: location.timestamp,
            precisionMeters: max(0, location.horizontalAccuracy)
        )
        latestMetadata = metadata
        manager.stopUpdatingLocation()
        finishRequest(with: metadata)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        errorMessage = error.localizedDescription
        finishRequest(with: nil)
    }

    private func requestLocationNow() {
        guard hasExplicitRequestPending else { return }
        isRequestingLocation = true
        manager.requestLocation()
    }

    private func finishRequest(with metadata: LocalGeotagMetadata?) {
        hasExplicitRequestPending = false
        isRequestingLocation = false
        pendingContinuation?.resume(returning: metadata)
        pendingContinuation = nil
    }

    private func synchronizeAuthorizationState() {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse: authorizationState = .authorized
        case .notDetermined: authorizationState = .notRequested
        case .denied: authorizationState = .denied
        case .restricted: authorizationState = .restricted
        @unknown default: authorizationState = .unavailable
        }
    }
}
