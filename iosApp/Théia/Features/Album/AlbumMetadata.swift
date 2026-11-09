import Foundation
import Photos

enum TheiaPhotoDestination: Sendable {
    case originals
    case revealed

    var albumTitle: String {
        switch self {
        case .originals: "Originais"
        case .revealed: "Reveladas"
        }
    }
}

/// Saves each new photo in an album nested under a single Théia Photos folder.
/// The serial queue prevents simultaneous first saves from creating duplicate collections.
enum TheiaPhotoLibraryOrganizer {
    private static let folderTitle = "Théia"
    private static let queue = DispatchQueue(label: "app.theia.photo-library-organizer")

    static func requestReadWriteAuthorization() async -> PHAuthorizationStatus {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .notDetermined else { return status }
        return await PHPhotoLibrary.requestAuthorization(for: .readWrite)
    }

    static func hasReadWriteAccess(_ status: PHAuthorizationStatus) -> Bool {
        status == .authorized || status == .limited
    }

    static func save(
        data: Data,
        originalFilename: String?,
        destination: TheiaPhotoDestination
    ) async throws -> String {
        try await Task.detached(priority: .utility) {
            try queue.sync {
                try saveSynchronously(data: data, originalFilename: originalFilename, destination: destination)
            }
        }.value
    }

    private static func saveSynchronously(
        data: Data,
        originalFilename: String?,
        destination: TheiaPhotoDestination
    ) throws -> String {
        let library = PHPhotoLibrary.shared()
        let folder = try ensureFolder(in: library)
        let album = try ensureAlbum(destination.albumTitle, in: folder, library: library)
        var assetIdentifier: String?
        var assetWasAddedToAlbum = false

        try library.performChangesAndWait {
            let assetRequest = PHAssetCreationRequest.forAsset()
            let options = PHAssetResourceCreationOptions()
            options.originalFilename = originalFilename
            assetRequest.addResource(with: .photo, data: data, options: options)
            guard let placeholder = assetRequest.placeholderForCreatedAsset,
                  let albumRequest = PHAssetCollectionChangeRequest(for: album) else { return }
            albumRequest.addAssets([placeholder] as NSArray)
            assetIdentifier = placeholder.localIdentifier
            assetWasAddedToAlbum = true
        }

        guard assetWasAddedToAlbum, let assetIdentifier else {
            throw OrganizerError.assetCouldNotBeAdded
        }
        return assetIdentifier
    }

    private static func ensureFolder(in library: PHPhotoLibrary) throws -> PHCollectionList {
        if let existing = findFolder() { return existing }

        try library.performChangesAndWait {
            _ = PHCollectionListChangeRequest.creationRequestForCollectionList(withTitle: folderTitle)
        }

        guard let created = findFolder() else { throw OrganizerError.folderCouldNotBeCreated }
        return created
    }

    private static func ensureAlbum(
        _ title: String,
        in folder: PHCollectionList,
        library: PHPhotoLibrary
    ) throws -> PHAssetCollection {
        if let existing = findAlbum(title, in: folder) { return existing }
        var albumWasAddedToFolder = false

        try library.performChangesAndWait {
            let albumRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: title)
            guard let folderRequest = PHCollectionListChangeRequest(for: folder) else { return }
            folderRequest.addChildCollections([albumRequest.placeholderForCreatedAssetCollection] as NSArray)
            albumWasAddedToFolder = true
        }

        guard albumWasAddedToFolder, let created = findAlbum(title, in: folder) else {
            throw OrganizerError.albumCouldNotBeCreated
        }
        return created
    }

    private static func findFolder() -> PHCollectionList? {
        let collections = PHCollectionList.fetchTopLevelUserCollections(with: nil)
        for index in 0..<collections.count {
            guard let folder = collections.object(at: index) as? PHCollectionList,
                  folder.localizedTitle == folderTitle else { continue }
            return folder
        }
        return nil
    }

    private static func findAlbum(_ title: String, in folder: PHCollectionList) -> PHAssetCollection? {
        let children = PHCollection.fetchCollections(in: folder, options: nil)
        for index in 0..<children.count {
            guard let album = children.object(at: index) as? PHAssetCollection,
                  album.localizedTitle == title else { continue }
            return album
        }
        return nil
    }

    private enum OrganizerError: Error {
        case folderCouldNotBeCreated
        case albumCouldNotBeCreated
        case assetCouldNotBeAdded
    }
}

enum AlbumMediaKind: String, Codable, CaseIterable, Sendable {
    case photo
    case video
}

enum AlbumGrouping: String, CaseIterable, Identifiable {
    case date = "Date"
    case roll = "Roll"
    case film = "Film stock"
    case camera = "Camera"

    var id: String { rawValue }
}

struct AlbumCapture: Codable, Identifiable, Hashable, Sendable {
    var id: String
    var kind: AlbumMediaKind
    var capturedAt: Date
    var bodyID: String
    var bodyName: String
    var lensID: String
    var lensName: String
    var filmStockID: String
    var filmStockName: String
    var recipeID: String
    var rollID: String?
    var rollIsDeveloped: Bool
    var galleryLocalIdentifier: String?
    var developedGalleryLocalIdentifier: String?
    var isFavorite: Bool
    var settings: [String: String]
    var authorizedLatitude: Double?
    var authorizedLongitude: Double?

    init(
        id: String = UUID().uuidString,
        kind: AlbumMediaKind = .photo,
        capturedAt: Date,
        bodyID: String,
        bodyName: String,
        lensID: String,
        lensName: String,
        filmStockID: String,
        filmStockName: String,
        recipeID: String,
        rollID: String? = nil,
        rollIsDeveloped: Bool = false,
        galleryLocalIdentifier: String? = nil,
        developedGalleryLocalIdentifier: String? = nil,
        isFavorite: Bool = false,
        settings: [String: String] = [:],
        authorizedLatitude: Double? = nil,
        authorizedLongitude: Double? = nil
    ) {
        self.id = id
        self.kind = kind
        self.capturedAt = capturedAt
        self.bodyID = bodyID
        self.bodyName = bodyName
        self.lensID = lensID
        self.lensName = lensName
        self.filmStockID = filmStockID
        self.filmStockName = filmStockName
        self.recipeID = recipeID
        self.rollID = rollID
        self.rollIsDeveloped = rollIsDeveloped
        self.galleryLocalIdentifier = galleryLocalIdentifier
        self.developedGalleryLocalIdentifier = developedGalleryLocalIdentifier
        self.isFavorite = isFavorite
        self.settings = settings
        self.authorizedLatitude = authorizedLatitude
        self.authorizedLongitude = authorizedLongitude
    }

    var searchText: String {
        [bodyID, bodyName, lensID, lensName, filmStockID, filmStockName, rollID ?? "", recipeID]
            .joined(separator: " ")
    }

    func groupTitle(for grouping: AlbumGrouping) -> String {
        switch grouping {
        case .date:
            capturedAt.formatted(date: .long, time: .omitted)
        case .roll:
            rollID.map { String(format: NSLocalizedString("Roll %@", comment: "Roll group title"), $0) }
                ?? NSLocalizedString("No roll", comment: "Group without an assigned roll")
        case .film:
            filmStockName
        case .camera:
            bodyName
        }
    }
}

/// Durable, offline storage for Théia's local album metadata.
protocol AlbumMetadataRepository: Sendable {
    func allCaptures() async throws -> [AlbumCapture]
    func save(_ capture: AlbumCapture) async throws
    func remove(id: String) async throws
}

actor JSONAlbumMetadataRepository: AlbumMetadataRepository {
    private let fileURL: URL

    init(fileURL: URL? = nil) {
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            let directory = support.appendingPathComponent("Theia", isDirectory: true)
            self.fileURL = directory.appendingPathComponent("album.json")
        }
    }

    func allCaptures() async throws -> [AlbumCapture] {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return [] }
        let data = try Data(contentsOf: fileURL)
        return try JSONDecoder.album.decode([AlbumCapture].self, from: data)
    }

    func save(_ capture: AlbumCapture) async throws {
        var captures = try await allCaptures()
        if let index = captures.firstIndex(where: { $0.id == capture.id }) {
            captures[index] = capture
        } else {
            captures.append(capture)
        }
        try persist(captures)
    }

    func remove(id: String) async throws {
        let captures = try await allCaptures()
        try persist(captures.filter { $0.id != id })
    }

    private func persist(_ captures: [AlbumCapture]) throws {
        let directory = fileURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var resourceValues = URLResourceValues()
        resourceValues.isExcludedFromBackup = true
        var localDirectory = directory
        try? localDirectory.setResourceValues(resourceValues)
        let data = try JSONEncoder.album.encode(captures)
        try data.write(to: fileURL, options: .atomic)
    }
}

private extension JSONDecoder {
    static var album: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}

private extension JSONEncoder {
    static var album: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }
}

enum AlbumPhotoLibrary {
    static func exportAsset(localIdentifier: String, kind: AlbumMediaKind) async throws -> URL {
        let current = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        let status = current == .notDetermined
            ? await PHPhotoLibrary.requestAuthorization(for: .readWrite)
            : current
        guard status == .authorized || status == .limited else {
            throw AlbumPhotoLibraryError.sharePermissionDenied
        }
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: [localIdentifier], options: nil)
        guard let asset = assets.firstObject else { throw AlbumPhotoLibraryError.assetUnavailable }
        let resources = PHAssetResource.assetResources(for: asset)
        let expectedTypes: Set<PHAssetResourceType> = kind == .video ? [.video, .fullSizeVideo] : [.photo, .fullSizePhoto]
        guard let resource = resources.first(where: { expectedTypes.contains($0.type) }) else {
            throw AlbumPhotoLibraryError.assetUnavailable
        }

        let originalExtension = URL(fileURLWithPath: resource.originalFilename).pathExtension
        let fileExtension = originalExtension.isEmpty ? (kind == .video ? "mov" : "jpg") : originalExtension
        let exportURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("theia-share-\(UUID().uuidString).\(fileExtension)")
        let options = PHAssetResourceRequestOptions()
        options.isNetworkAccessAllowed = false
        do {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                PHAssetResourceManager.default().writeData(for: resource, toFile: exportURL, options: options) { error in
                    if let error { continuation.resume(throwing: error) }
                    else { continuation.resume() }
                }
            }
            return exportURL
        } catch {
            try? FileManager.default.removeItem(at: exportURL)
            throw error
        }
    }

    static func deleteAsset(localIdentifier: String) async throws {
        try await deleteAssets(localIdentifiers: [localIdentifier])
    }

    static func deleteAssets(localIdentifiers: [String]) async throws {
        let current = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        let status: PHAuthorizationStatus
        if current == .notDetermined {
            status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        } else {
            status = current
        }
        guard status == .authorized || status == .limited else {
            throw AlbumPhotoLibraryError.permissionDenied
        }
        let identifiers = Array(Set(localIdentifiers)).filter { !$0.isEmpty }
        guard !identifiers.isEmpty else { return }
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: identifiers, options: nil)
        guard assets.count > 0 else { return }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.deleteAssets(assets)
            } completionHandler: { success, error in
                if let error { continuation.resume(throwing: error) }
                else if success { continuation.resume() }
                else { continuation.resume(throwing: AlbumPhotoLibraryError.deleteFailed) }
            }
        }
    }
}

enum AlbumPhotoLibraryError: LocalizedError {
    case permissionDenied
    case deleteFailed
    case sharePermissionDenied
    case assetUnavailable

    var errorDescription: String? {
        switch self {
        case .permissionDenied:
            NSLocalizedString(
                "Photo library access is needed to remove the selected Photos item. Théia data was kept.",
                comment: "Album delete permission failure",
            )
        case .deleteFailed:
            NSLocalizedString(
                "The Photos item could not be removed. Théia data was kept.",
                comment: "Album delete failure",
            )
        case .sharePermissionDenied:
            NSLocalizedString("Allow Photos access to share this capture.", comment: "Album share permission failure")
        case .assetUnavailable:
            NSLocalizedString("This Photos item is unavailable on this device. Download it to Photos before sharing.", comment: "Album share asset missing")
        }
    }
}
