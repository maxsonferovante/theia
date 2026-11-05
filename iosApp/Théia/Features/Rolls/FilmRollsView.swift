import Foundation
import Photos
import SwiftUI
import TheiaShared

enum NativeFilmRollCapacity: Int, Codable, CaseIterable, Identifiable {
    case exposures24 = 24
    case exposures36 = 36

    var id: Int { rawValue }
}

/// Durable roll state. The recipe is captured at roll creation and never changes.
struct NativeFilmRoll: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let capacity: NativeFilmRollCapacity
    let createdAt: Date
    let recipe: NativeCaptureRecipe
    var captureIDs: [String] = []
    var instantDevelopedIDs: Set<String> = []
    var developedAt: Date?

    private var domainCapacity: TheiaShared.FilmRollCapacity {
        capacity == .exposures24 ? .exposures24 : .exposures36
    }

    private var domainRoll: TheiaShared.FilmRoll {
        TheiaShared.FilmRoll(
            id: id,
            capacity: domainCapacity,
            createdAtEpochMillis: Int64(createdAt.timeIntervalSince1970 * 1_000),
            photoIds: captureIDs,
            developedAtEpochMillis: nil
        )
    }

    var remainingExposures: Int { max(0, Int(domainRoll.capacity.exposures) - captureIDs.count) }
    var isFull: Bool { captureIDs.count == Int(domainRoll.capacity.exposures) }
    var canCapture: Bool { developedAt == nil && !isFull }
    var isDeveloped: Bool { developedAt != nil }
    var visibleCaptureIDs: Set<String> {
        isDeveloped ? Set(captureIDs) : instantDevelopedIDs
    }

    func recording(_ captureID: String) throws -> NativeFilmRoll {
        guard developedAt == nil else { throw NativeFilmRollError.closedOrFull }
        let updated = try domainRoll.recordPhoto(photoId: captureID)
        return copy(captureIDs: updated.photoIds.compactMap { $0 as? String })
    }

    func developing(at date: Date) throws -> NativeFilmRoll {
        guard developedAt == nil else { throw NativeFilmRollError.closedOrFull }
        let updated = try domainRoll.develop(atEpochMillis: Int64(date.timeIntervalSince1970 * 1_000))
        return copy(
            captureIDs: updated.photoIds.compactMap { $0 as? String },
            instantDevelopedIDs: instantDevelopedIDs.union(captureIDs),
            developedAt: date
        )
    }

    private func copy(
        captureIDs: [String],
        instantDevelopedIDs: Set<String>? = nil,
        developedAt: Date? = nil
    ) -> NativeFilmRoll {
        NativeFilmRoll(
            id: id,
            capacity: capacity,
            createdAt: createdAt,
            recipe: recipe,
            captureIDs: captureIDs,
            instantDevelopedIDs: instantDevelopedIDs ?? self.instantDevelopedIDs,
            developedAt: developedAt ?? self.developedAt
        )
    }
}

protocol NativeFilmRollRepository: Sendable {
    func allRolls() async throws -> [NativeFilmRoll]
    func create(capacity: NativeFilmRollCapacity, recipe: NativeCaptureRecipe, at date: Date) async throws -> NativeFilmRoll
    func appendCapture(_ captureID: String, to rollID: String) async throws -> NativeFilmRoll
    func markInstantDeveloped(_ captureID: String, in rollID: String) async throws
    func finishDevelopment(_ rollID: String, at date: Date, allowEarly: Bool) async throws -> NativeFilmRoll
    func remove(_ rollID: String) async throws
}

actor JSONNativeFilmRollRepository: NativeFilmRollRepository {
    private let fileURL: URL

    init(fileURL: URL? = nil) {
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            self.fileURL = support.appendingPathComponent("Theia", isDirectory: true)
                .appendingPathComponent("film-rolls.json")
        }
    }

    func allRolls() async throws -> [NativeFilmRoll] {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return [] }
        return try JSONDecoder.rolls.decode([NativeFilmRoll].self, from: Data(contentsOf: fileURL))
    }

    func create(capacity: NativeFilmRollCapacity, recipe: NativeCaptureRecipe, at date: Date = .now) async throws -> NativeFilmRoll {
        let roll = NativeFilmRoll(id: UUID().uuidString, capacity: capacity, createdAt: date, recipe: recipe)
        try await save(roll)
        return roll
    }

    func appendCapture(_ captureID: String, to rollID: String) async throws -> NativeFilmRoll {
        var rolls = try await allRolls()
        guard let index = rolls.firstIndex(where: { $0.id == rollID }) else { throw NativeFilmRollError.notFound }
        let updated = try rolls[index].recording(captureID)
        rolls[index] = updated
        try persist(rolls)
        return rolls[index]
    }

    func markInstantDeveloped(_ captureID: String, in rollID: String) async throws {
        var rolls = try await allRolls()
        guard let index = rolls.firstIndex(where: { $0.id == rollID }) else { throw NativeFilmRollError.notFound }
        guard rolls[index].captureIDs.contains(captureID) else { throw NativeFilmRollError.captureNotInRoll }
        rolls[index].instantDevelopedIDs.insert(captureID)
        try persist(rolls)
    }

    func finishDevelopment(_ rollID: String, at date: Date = .now, allowEarly: Bool = false) async throws -> NativeFilmRoll {
        var rolls = try await allRolls()
        guard let index = rolls.firstIndex(where: { $0.id == rollID }) else { throw NativeFilmRollError.notFound }
        guard !rolls[index].isDeveloped else { return rolls[index] }
        guard allowEarly || rolls[index].isFull else { throw NativeFilmRollError.notFull }
        rolls[index] = try rolls[index].developing(at: date)
        try persist(rolls)
        return rolls[index]
    }

    func remove(_ rollID: String) async throws {
        try persist(try await allRolls().filter { $0.id != rollID })
    }

    private func save(_ roll: NativeFilmRoll) async throws {
        var rolls = try await allRolls()
        rolls.append(roll)
        try persist(rolls)
    }

    private func persist(_ rolls: [NativeFilmRoll]) throws {
        let directory = fileURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var excludedDirectory = directory
        try? excludedDirectory.setResourceValues(values)
        try JSONEncoder.rolls.encode(rolls).write(to: fileURL, options: .atomic)
    }
}

private extension JSONDecoder {
    static var rolls: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}

private extension JSONEncoder {
    static var rolls: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }
}

enum NativeFilmRollError: LocalizedError {
    case notFound, closedOrFull, duplicateCapture, captureNotInRoll, notFull, photosPermissionDenied, assetUnavailable, saveFailed

    var errorDescription: String? {
        switch self {
        case .notFound: NSLocalizedString("roll_error_not_found", comment: "Film roll no longer exists")
        case .closedOrFull: NSLocalizedString("roll_error_closed", comment: "Film roll is developed or full")
        case .duplicateCapture: NSLocalizedString("roll_error_duplicate", comment: "Capture already belongs to this roll")
        case .captureNotInRoll: NSLocalizedString("roll_error_capture_missing", comment: "Capture does not belong to this roll")
        case .notFull: NSLocalizedString("roll_error_not_full", comment: "Film roll is not full")
        case .photosPermissionDenied: NSLocalizedString("roll_error_photos_permission", comment: "Photos add permission denied")
        case .assetUnavailable: NSLocalizedString("roll_error_asset", comment: "Original Photos item unavailable")
        case .saveFailed: NSLocalizedString("roll_error_save", comment: "Developed copy could not be saved")
        }
    }
}

/// Renders an original to a separate Photos asset. Implement with the versioned native film renderer.
struct NativeDevelopedAsset: Sendable {
    let localIdentifier: String
    let metadata: NativeDevelopedMetadata
}

typealias NativeRollDevelop = (_ originalAssetID: String, _ recipe: NativeCaptureRecipe) async throws -> NativeDevelopedAsset

enum NativeFilmRollImageDevelopment {
    static func develop(originalAssetID: String, recipe: NativeCaptureRecipe) async throws -> NativeDevelopedAsset {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        let access = status == .notDetermined
            ? await PHPhotoLibrary.requestAuthorization(for: .readWrite)
            : status
        guard access == .authorized || access == .limited else {
            throw NativeFilmImageProcessorError.photoLibraryReadDenied
        }
        guard let parameters = try NativeFilmVisualProfileCatalog.resolve(recipe: recipe) else {
            throw NativeFilmImageProcessorError.unsupportedProfileVersion
        }
        let artifact = try await NativeFilmImageProcessor.developPhoto(
            assetLocalIdentifier: originalAssetID,
            recipe: recipe,
            parameters: parameters
        )
        let localIdentifier = try await NativeFilmImageProcessor.saveDevelopedCopy(artifact)
        return NativeDevelopedAsset(localIdentifier: localIdentifier, metadata: artifact.metadata)
    }
}

struct FilmRollsView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let repository: any NativeFilmRollRepository
    let albumRepository: any AlbumMetadataRepository
    let currentRecipe: NativeCaptureRecipe
    let onCaptureSaved: (NativeCaptureRecord, String) -> Void
    let developOriginal: NativeRollDevelop?

    @State private var rolls: [NativeFilmRoll] = []
    @State private var selectedCapacity = NativeFilmRollCapacity.exposures24
    @State private var isCreating = false
    @State private var captureRoll: NativeFilmRoll?
    @State private var selectedRoll: NativeFilmRoll?
    @State private var showError = false
    @State private var errorText = ""
    @State private var latestPhotoVisible = true
    @State private var developmentMessageKey: String?
    @State private var developmentMessageID = UUID()
    @AppStorage("instant_development") private var instantDevelopmentEnabled = false

    init(
        repository: any NativeFilmRollRepository = JSONNativeFilmRollRepository(),
        albumRepository: any AlbumMetadataRepository,
        currentRecipe: NativeCaptureRecipe,
        developOriginal: NativeRollDevelop? = nil,
        onCaptureSaved: @escaping (NativeCaptureRecord, String) -> Void
    ) {
        self.repository = repository
        self.albumRepository = albumRepository
        self.currentRecipe = currentRecipe
        self.developOriginal = developOriginal
        self.onCaptureSaved = onCaptureSaved
    }

    var body: some View {
        List {
            Section {
                Picker("roll_capacity", selection: $selectedCapacity) {
                    ForEach(NativeFilmRollCapacity.allCases) { capacity in
                        Text("\(capacity.rawValue) \(NSLocalizedString("roll_exposures", comment: "Exposures"))")
                            .tag(capacity)
                    }
                }
                .accessibilityLabel(Text("roll_capacity"))
                Button { Task { await createRoll() } } label: {
                    Label("roll_create", systemImage: "plus.circle.fill")
                        .frame(minHeight: 44)
                }
                .disabled(isCreating)
            } header: {
                Text("roll_new")
            } footer: {
                Text("roll_recipe_lock_explanation")
            }

            Section("roll_development_preferences") {
                Toggle("roll_instant_development", isOn: $instantDevelopmentEnabled)
                    .accessibilityHint(Text("roll_instant_development_hint"))
            }

            Section("rolls_section") {
                if rolls.isEmpty {
                    ContentUnavailableView("roll_empty_title", systemImage: "camera.macro", description: Text("roll_empty_description"))
                } else {
                    ForEach(rolls) { roll in
                        Button { selectedRoll = roll } label: {
                            rollRow(roll)
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .combine)
                        .accessibilityHint(Text("roll_open_hint"))
                    }
                    .onDelete(perform: deleteRolls)
                }
            }
        }
        .navigationTitle("rolls_title")
        .toolbar { EditButton() }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if let developmentMessageKey {
                DevelopmentConfirmationBanner(messageKey: developmentMessageKey)
                    .transition(reduceMotion ? .identity : .move(edge: .bottom).combined(with: .opacity))
            }
        }
        .task { await reload(); await refreshLatestPhotoVisibility() }
        .sheet(item: $captureRoll) { roll in
            NavigationStack {
                CameraCaptureView(recipe: roll.recipe, latestPhotoVisible: $latestPhotoVisible) { record in
                    Task { await addCapture(record, to: roll) }
                }
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("roll_done") { captureRoll = nil }
                    }
                }
            }
        }
        .sheet(item: $selectedRoll) { roll in
            NavigationStack {
                FilmRollDetailView(
                    roll: rolls.first(where: { $0.id == roll.id }) ?? roll,
                    repository: repository,
                    albumRepository: albumRepository,
                    developOriginal: developOriginal,
                    onCapture: { captureRoll = roll },
                    onUpdate: { await reload(); await refreshLatestPhotoVisibility() }
                )
            }
        }
        .alert("roll_error_title", isPresented: $showError) {
            Button("capture_ok", role: .cancel) { }
        } message: { Text(errorText) }
    }

    @ViewBuilder private func rollRow(_ roll: NativeFilmRoll) -> some View {
        HStack(spacing: 14) {
            Image(systemName: roll.isDeveloped ? "checkmark.seal.fill" : "film.stack")
                .font(.title2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(roll.createdAt.formatted(date: .abbreviated, time: .shortened)).font(.headline)
                Text("\(roll.captureIDs.count) / \(roll.capacity.rawValue) · \(roll.recipe.filmStockID)")
                    .font(.subheadline)
                Text(roll.isDeveloped ? "roll_status_developed" : "roll_status_unrevealed")
                    .font(.caption.weight(.semibold))
            }
            Spacer()
            if roll.canCapture {
                Button { captureRoll = roll } label: {
                    Image(systemName: "camera.fill").frame(width: 44, height: 44)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityLabel(Text("roll_capture"))
                .accessibilityHint(Text("roll_capture_hint"))
            }
        }
        .padding(.vertical, 5)
    }

    private func createRoll() async {
        isCreating = true
        defer { isCreating = false }
        do {
            _ = try await repository.create(capacity: selectedCapacity, recipe: currentRecipe, at: .now)
            await reload()
        } catch { present(error) }
    }

    private func addCapture(_ record: NativeCaptureRecord, to roll: NativeFilmRoll) async {
        do {
            let updated = try await repository.appendCapture(record.localIdentifier, to: roll.id)
            var capture = try await makeAlbumCapture(record, roll: updated)
            try await albumRepository.save(capture)
            onCaptureSaved(record, roll.id)
            if instantDevelopmentEnabled, let developOriginal {
                let developed = try await developOriginal(record.localIdentifier, updated.recipe)
                capture.developedGalleryLocalIdentifier = developed.localIdentifier
                capture.settings["Development recipe"] = developed.metadata.recipeID
                capture.settings["Film profile"] = "\(developed.metadata.filmProfileID) v\(developed.metadata.filmProfileVersion)"
                try await albumRepository.save(capture)
                try await repository.markInstantDeveloped(record.localIdentifier, in: updated.id)
                latestPhotoVisible = true
                showDevelopmentConfirmation("roll_photo_development_complete")
            }
            await reload()
        } catch { present(error) }
    }

    private func makeAlbumCapture(_ record: NativeCaptureRecord, roll: NativeFilmRoll) async throws -> AlbumCapture {
        let current = try await albumRepository.allCaptures().first { $0.id == record.localIdentifier }
        if let current { var updated = current; updated.rollID = roll.id; updated.rollIsDeveloped = roll.isDeveloped; return updated }
        let body = BuiltInCatalog.cameraBodies.first { $0.id == record.recipe.cameraBodyID }
        let lens = BuiltInCatalog.lenses.first { $0.id == record.recipe.lensID }
        let film = BuiltInCatalog.filmStocks.first { $0.id == record.recipe.filmStockID }
        return AlbumCapture(
            id: record.localIdentifier,
            capturedAt: record.capturedAt,
            bodyID: record.recipe.cameraBodyID,
            bodyName: body.map { localizedCatalogValue($0.nameKey) } ?? record.recipe.cameraBodyID,
            lensID: record.recipe.lensID,
            lensName: lens.map { localizedCatalogValue($0.nameKey) } ?? record.recipe.lensID,
            filmStockID: record.recipe.filmStockID,
            filmStockName: film.map { localizedCatalogValue($0.nameKey) } ?? record.recipe.filmStockID,
            recipeID: record.recipe.stableID,
            rollID: roll.id,
            rollIsDeveloped: roll.isDeveloped,
            galleryLocalIdentifier: record.localIdentifier,
            settings: ["Capture": NSLocalizedString("roll_original_capture", comment: "Original camera capture"), "Recipe": record.recipe.stableID],
            authorizedLatitude: record.geotagMetadata?.coordinate.latitude,
            authorizedLongitude: record.geotagMetadata?.coordinate.longitude
        )
    }

    private func localizedCatalogValue(_ key: String) -> String {
        let resourceKey = key.replacingOccurrences(of: ".", with: "_").replacingOccurrences(of: "-", with: "_")
        let languageTag = UserDefaults.standard.string(forKey: "language_tag") ?? "en"
        let bundle = Bundle.main.path(forResource: languageTag, ofType: "lproj")
            .flatMap { Bundle(path: $0) } ?? .main
        return bundle.localizedString(forKey: resourceKey, value: nil, table: nil)
    }

    private func reload() async {
        do { rolls = try await repository.allRolls().sorted { $0.createdAt > $1.createdAt } }
        catch { present(error) }
    }

    private func refreshLatestPhotoVisibility() async {
        do {
            guard let latest = try await albumRepository.allCaptures().max(by: { $0.capturedAt < $1.capturedAt }) else {
                latestPhotoVisible = true
                return
            }
            guard latest.developedGalleryLocalIdentifier == nil, let rollID = latest.rollID else {
                latestPhotoVisible = true
                return
            }
            let roll = try await repository.allRolls().first { $0.id == rollID }
            latestPhotoVisible = roll?.visibleCaptureIDs.contains(latest.id) ?? true
        } catch {
            latestPhotoVisible = false
        }
    }

    private func showDevelopmentConfirmation(_ messageKey: String) {
        let token = UUID()
        developmentMessageID = token
        if reduceMotion { developmentMessageKey = messageKey }
        else { withAnimation(.spring(response: 0.38, dampingFraction: 0.82)) { developmentMessageKey = messageKey } }
        Task {
            try? await Task.sleep(for: .seconds(4))
            guard developmentMessageID == token else { return }
            if reduceMotion { developmentMessageKey = nil }
            else { withAnimation(.easeOut(duration: 0.22)) { developmentMessageKey = nil } }
        }
    }

    private func deleteRolls(at offsets: IndexSet) {
        let ids = offsets.map { rolls[$0].id }
        Task {
            do {
                for id in ids {
                    let related = try await albumRepository.allCaptures().filter { $0.rollID == id }
                    for var capture in related {
                        capture.rollID = nil
                        capture.rollIsDeveloped = true
                        try await albumRepository.save(capture)
                    }
                    try await repository.remove(id)
                }
                await reload()
                await refreshLatestPhotoVisibility()
            }
            catch { present(error) }
        }
    }

    private func present(_ error: Error) {
        errorText = error.localizedDescription
        showError = true
    }
}

private struct FilmRollDetailView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let roll: NativeFilmRoll
    let repository: any NativeFilmRollRepository
    let albumRepository: any AlbumMetadataRepository
    let developOriginal: NativeRollDevelop?
    let onCapture: () -> Void
    let onUpdate: () async -> Void

    @State private var captures: [AlbumCapture] = []
    @State private var developing = false
    @State private var isEarlyConfirmationPresented = false
    @State private var showError = false
    @State private var errorText = ""
    @State private var developmentMessageKey: String?
    @State private var developmentMessageID = UUID()

    var body: some View {
        List {
            Section("roll_fixed_recipe") {
                LabeledContent("Camera", value: roll.recipe.cameraBodyID)
                LabeledContent("Lens", value: roll.recipe.lensID)
                LabeledContent("Film", value: roll.recipe.filmStockID)
            }
            Section("roll_progress") {
                ProgressView(value: Double(roll.captureIDs.count), total: Double(roll.capacity.rawValue))
                LabeledContent("roll_exposures_used", value: "\(roll.captureIDs.count) / \(roll.capacity.rawValue)")
                LabeledContent("roll_original_visibility", value: roll.isDeveloped ? NSLocalizedString("roll_visibility_revealed", comment: "Revealed") : NSLocalizedString("roll_visibility_locked", comment: "Locked"))
            }
            Section("roll_photos") {
                if captures.isEmpty { Text("roll_no_photos").foregroundStyle(.secondary) }
                ForEach(captures) { capture in
                    HStack {
                        Image(systemName: roll.visibleCaptureIDs.contains(capture.id) ? "eye.fill" : "eye.slash.fill")
                            .accessibilityHidden(true)
                        VStack(alignment: .leading) {
                            Text(capture.capturedAt.formatted(date: .abbreviated, time: .shortened))
                            Text(roll.visibleCaptureIDs.contains(capture.id) ? "roll_photo_revealed" : "roll_photo_locked")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if !roll.visibleCaptureIDs.contains(capture.id) {
                            Button("roll_develop_this") { Task { await developOne(capture) } }
                                .disabled(developing || developOriginal == nil)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            }
            Section {
                if roll.canCapture {
                    Button { onCapture() } label: { Label("roll_capture", systemImage: "camera") }
                }
                if !roll.isDeveloped {
                    Button { Task { await developWholeRoll(allowEarly: false) } } label: {
                        Label(roll.isFull ? "roll_develop_full" : "roll_develop_when_full", systemImage: "sparkles")
                    }
                    .disabled(!roll.isFull || developing || developOriginal == nil)
                    Button(role: .destructive) { isEarlyConfirmationPresented = true } label: {
                        Label("roll_develop_early", systemImage: "bolt.fill")
                    }
                    .disabled(roll.captureIDs.isEmpty || developing || developOriginal == nil)
                }
                if developOriginal == nil && !roll.captureIDs.isEmpty && !roll.isDeveloped {
                    Text("roll_renderer_unavailable").font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("roll_detail_title")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if let developmentMessageKey {
                DevelopmentConfirmationBanner(messageKey: developmentMessageKey)
                    .transition(reduceMotion ? .identity : .move(edge: .bottom).combined(with: .opacity))
            }
        }
        .task { await loadCaptures() }
        .confirmationDialog("roll_early_confirmation_title", isPresented: $isEarlyConfirmationPresented, titleVisibility: .visible) {
            Button("roll_develop_early_confirm", role: .destructive) { Task { await developWholeRoll(allowEarly: true) } }
            Button("capture_cancel", role: .cancel) { }
        } message: { Text("roll_early_confirmation_message") }
        .alert("roll_error_title", isPresented: $showError) {
            Button("capture_ok", role: .cancel) { }
        } message: { Text(errorText) }
    }

    private func loadCaptures() async {
        do {
            let all = try await albumRepository.allCaptures()
            captures = roll.captureIDs.compactMap { id in all.first { $0.id == id } }
        } catch { present(error) }
    }

    private func developOne(_ capture: AlbumCapture) async {
        guard let developOriginal else { return }
        developing = true
        defer { developing = false }
        do {
            guard let sourceID = capture.galleryLocalIdentifier else { throw NativeFilmRollError.assetUnavailable }
            let result = try await developOriginal(sourceID, roll.recipe)
            var updated = capture
            updated.developedGalleryLocalIdentifier = result.localIdentifier
            updated.settings["Development recipe"] = result.metadata.recipeID
            updated.settings["Film profile"] = "\(result.metadata.filmProfileID) v\(result.metadata.filmProfileVersion)"
            try await albumRepository.save(updated)
            try await repository.markInstantDeveloped(capture.id, in: roll.id)
            showDevelopmentConfirmation("roll_photo_development_complete")
            await loadCaptures()
            await onUpdate()
        } catch { present(error) }
    }

    private func showDevelopmentConfirmation(_ messageKey: String) {
        let token = UUID()
        developmentMessageID = token
        if reduceMotion { developmentMessageKey = messageKey }
        else { withAnimation(.spring(response: 0.38, dampingFraction: 0.82)) { developmentMessageKey = messageKey } }
        Task {
            try? await Task.sleep(for: .seconds(4))
            guard developmentMessageID == token else { return }
            if reduceMotion { developmentMessageKey = nil }
            else { withAnimation(.easeOut(duration: 0.22)) { developmentMessageKey = nil } }
        }
    }

    private func developWholeRoll(allowEarly: Bool) async {
        guard let developOriginal else { return }
        developing = true
        defer { developing = false }
        do {
            for capture in captures where capture.developedGalleryLocalIdentifier == nil {
                guard let sourceID = capture.galleryLocalIdentifier else { throw NativeFilmRollError.assetUnavailable }
                let result = try await developOriginal(sourceID, roll.recipe)
                var updated = capture
                updated.developedGalleryLocalIdentifier = result.localIdentifier
                updated.settings["Development recipe"] = result.metadata.recipeID
                updated.settings["Film profile"] = "\(result.metadata.filmProfileID) v\(result.metadata.filmProfileVersion)"
                try await albumRepository.save(updated)
                try await repository.markInstantDeveloped(capture.id, in: roll.id)
            }
            _ = try await repository.finishDevelopment(roll.id, at: .now, allowEarly: allowEarly)
            for var capture in captures {
                capture.rollIsDeveloped = true
                try await albumRepository.save(capture)
            }
            await loadCaptures()
            await onUpdate()
            showDevelopmentConfirmation(allowEarly ? "roll_early_development_complete" : "roll_development_complete")
        } catch { present(error) }
    }

    private func present(_ error: Error) {
        errorText = error.localizedDescription
        showError = true
    }
}

private struct DevelopmentConfirmationBanner: View {
    let messageKey: String

    var body: some View {
        Label(LocalizedStringKey(messageKey), systemImage: "checkmark.seal.fill")
            .font(.subheadline.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
            .padding(.horizontal, 18)
            .background(.regularMaterial)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.updatesFrequently)
    }
}
