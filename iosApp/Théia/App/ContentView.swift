import SwiftUI

private enum TheiaPalette {
    static let solarGold = Color(red: 0xEA / 255, green: 0xAE / 255, blue: 0x35 / 255)
    static let ivory = Color(red: 0xE7 / 255, green: 0xD7 / 255, blue: 0xB7 / 255)
    static let deepSky = Color(red: 0x18 / 255, green: 0x23 / 255, blue: 0x44 / 255)
    static let cosmicViolet = Color(red: 0x2D / 255, green: 0x2D / 255, blue: 0x57 / 255)
}

struct ContentView: View {
    @AppStorage("onboarding_completed") private var onboardingCompleted = false
    @AppStorage("language_tag") private var languageTag = Locale.current.language.languageCode?.identifier == "pt" ? "pt-BR" : "en"
    @AppStorage("instant_development") private var instantDevelopmentEnabled = false
    @State private var currentSelection: WorkbenchRecipeSelection?
    @State private var showCapture = false
    @State private var latestCapture: NativeCaptureRecord?
    @State private var latestPhotoVisible = true
    @State private var hasOpenedDefaultCamera = false
    private let albumRepository = JSONAlbumMetadataRepository()
    private let geotagRepository = JSONLocalGeotagMetadataRepository()
    private let filmRollRepository = JSONNativeFilmRollRepository()

    var body: some View {
        NavigationStack {
            if onboardingCompleted {
                ZStack {
                    TheiaPalette.deepSky.ignoresSafeArea()
                    VStack(spacing: 24) {
                        Text("Θ")
                            .font(.system(size: 76, weight: .light, design: .serif))
                            .foregroundStyle(TheiaPalette.solarGold)
                            .accessibilityHidden(true)
                        Text("Théia")
                            .font(.system(size: 38, weight: .medium, design: .serif))
                            .foregroundStyle(TheiaPalette.ivory)
                        Text("ios_development_build")
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .tracking(2)
                            .foregroundStyle(TheiaPalette.solarGold)
                        Text("ios_build_description")
                            .font(.body)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(TheiaPalette.ivory)
                            .padding(24)
                            .frame(maxWidth: .infinity)
                            .background(TheiaPalette.cosmicViolet, in: RoundedRectangle(cornerRadius: 24))
                        NavigationLink {
                            WorkbenchView(
                                cameraBodies: BuiltInCatalog.cameraBodies,
                                lenses: BuiltInCatalog.lenses,
                                filmStocks: BuiltInCatalog.filmStocks,
                                currentSelection: currentSelection
                            ) { selection in
                                currentSelection = selection
                                showCapture = true
                            }
                        } label: {
                            Label("open_workbench", systemImage: "camera.aperture")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(Color(red: 18 / 255, green: 21 / 255, blue: 34 / 255))
                                .frame(maxWidth: .infinity, minHeight: 54)
                                .background(TheiaPalette.solarGold, in: Capsule())
                        }
                        .accessibilityIdentifier("home.open-workbench")
                        .accessibilityHint(Text("Opens the equipment catalog to choose a camera body, lens, and film."))
                        NavigationLink {
                            AlbumView(repository: albumRepository)
                        } label: {
                            Label("open_album", systemImage: "photo.on.rectangle.angled")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(TheiaPalette.ivory)
                                .frame(maxWidth: .infinity, minHeight: 54)
                                .background(TheiaPalette.cosmicViolet, in: Capsule())
                        }
                        .accessibilityIdentifier("home.open-album")
                        .accessibilityHint(Text("Opens your on-device photo and video album."))
                        NavigationLink {
                            FilmRollsView(
                                repository: filmRollRepository,
                                albumRepository: albumRepository,
                                currentRecipe: activeNativeRecipe,
                                developOriginal: { originalID, recipe in
                                    try await NativeFilmRollImageDevelopment.develop(originalAssetID: originalID, recipe: recipe)
                                },
                                onCaptureSaved: { record, rollID in persistInAlbum(record, rollID: rollID) }
                            )
                        } label: {
                            Label("open_rolls", systemImage: "film.stack")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(TheiaPalette.ivory)
                                .frame(maxWidth: .infinity, minHeight: 54)
                                .background(TheiaPalette.cosmicViolet, in: Capsule())
                        }
                        .accessibilityIdentifier("home.open-rolls")
                        .accessibilityHint(Text("Opens your persistent film rolls."))
                        NavigationLink {
                            VideoCaptureView(recipe: activeNativeRecipe) { record in
                                persistInAlbum(record)
                            }
                        } label: {
                            Label("open_video", systemImage: "video")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(TheiaPalette.ivory)
                        }
                        .accessibilityIdentifier("home.open-video")
                        .accessibilityHint(Text("Starts a video recording flow with optional audio."))
                        NavigationLink {
                            TheiaSettingsView()
                        } label: {
                            Label("open_settings", systemImage: "gearshape")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(TheiaPalette.ivory)
                        }
                        .accessibilityIdentifier("home.open-settings")
                        .accessibilityHint(Text("Opens language, privacy, and capture preferences."))
                    }
                    .padding(28)
            }
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(isPresented: $showCapture) {
                    CameraCaptureView(
                        recipe: activeNativeRecipe,
                        latestPhotoVisible: $latestPhotoVisible,
                        onCaptureSaved: { record in
                            latestCapture = record
                            persistDefaultStillCapture(record)
                        }
                    )
                    .accessibilityIdentifier("capture.still-screen")
                    .toolbar(.visible, for: .navigationBar)
                }
            } else {
                TheiaOnboardingView {
                    onboardingCompleted = true
                    showCapture = true
                    hasOpenedDefaultCamera = true
                }
            }
        }
        .preferredColorScheme(.dark)
        .environment(\.locale, Locale(identifier: languageTag))
        .task {
            guard onboardingCompleted, !hasOpenedDefaultCamera else { return }
            hasOpenedDefaultCamera = true
            await refreshLatestPhotoVisibility()
            showCapture = true
        }
        .onChange(of: showCapture) { _, isShown in
            guard isShown else { return }
            Task { await refreshLatestPhotoVisibility() }
        }
    }

    private func persistInAlbum(_ record: NativeCaptureRecord, rollID: String? = nil) {
        guard let body = BuiltInCatalog.cameraBodies.first(where: { $0.id == record.recipe.cameraBodyID }),
              let lens = BuiltInCatalog.lenses.first(where: { $0.id == record.recipe.lensID }),
              let film = BuiltInCatalog.filmStocks.first(where: { $0.id == record.recipe.filmStockID }) else { return }
        let capture = AlbumCapture(
            id: record.localIdentifier,
            capturedAt: record.capturedAt,
            bodyID: body.id,
            bodyName: localizedCatalogValue(body.nameKey),
            lensID: lens.id,
            lensName: localizedCatalogValue(lens.nameKey),
            filmStockID: film.id,
            filmStockName: localizedCatalogValue(film.nameKey),
            recipeID: record.recipe.stableID,
            rollID: rollID,
            rollIsDeveloped: rollID == nil,
            galleryLocalIdentifier: record.localIdentifier,
            settings: ["Capture": "Original", "Recipe": record.recipe.stableID],
            authorizedLatitude: record.geotagMetadata?.coordinate.latitude,
            authorizedLongitude: record.geotagMetadata?.coordinate.longitude
        )
        Task {
            if let rollID,
               var existing = try? await albumRepository.allCaptures().first(where: { $0.id == record.localIdentifier }) {
                existing.rollID = rollID
                existing.authorizedLatitude = record.geotagMetadata?.coordinate.latitude
                existing.authorizedLongitude = record.geotagMetadata?.coordinate.longitude
                try? await albumRepository.save(existing)
            } else {
                try? await albumRepository.save(capture)
            }
            if let geotagMetadata = record.geotagMetadata {
                try? await geotagRepository.save(geotagMetadata, for: record.localIdentifier)
            }
        }
    }

    /// Routes captures from the home camera into the latest compatible open roll,
    /// creating the first 24-exposure roll automatically when needed.
    private func persistDefaultStillCapture(_ record: NativeCaptureRecord) {
        Task {
            do {
                let existing = try await filmRollRepository.allRolls()
                    .filter { $0.recipe == record.recipe && $0.canCapture }
                    .max { $0.createdAt < $1.createdAt }
                let roll: NativeFilmRoll
                if let existing {
                    roll = existing
                } else {
                    roll = try await filmRollRepository.create(
                        capacity: .exposures24,
                        recipe: record.recipe,
                        at: record.capturedAt
                    )
                }
                let updatedRoll = try await filmRollRepository.appendCapture(record.localIdentifier, to: roll.id)
                var capture = makeAlbumCapture(record, rollID: updatedRoll.id, isDeveloped: updatedRoll.isDeveloped)
                try await albumRepository.save(capture)
                if let geotag = record.geotagMetadata {
                    try await geotagRepository.save(geotag, for: record.localIdentifier)
                }
                if instantDevelopmentEnabled {
                    let developed = try await NativeFilmRollImageDevelopment.develop(
                        originalAssetID: record.localIdentifier,
                        recipe: record.recipe
                    )
                    capture.developedGalleryLocalIdentifier = developed.localIdentifier
                    capture.settings["Development recipe"] = developed.metadata.recipeID
                    capture.settings["Film profile"] = "\(developed.metadata.filmProfileID) v\(developed.metadata.filmProfileVersion)"
                    try await albumRepository.save(capture)
                    try await filmRollRepository.markInstantDeveloped(record.localIdentifier, in: updatedRoll.id)
                    latestPhotoVisible = true
                }
            } catch {
                // Keep the original in Photos even if local roll metadata or development fails.
            }
        }
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
            let roll = try await filmRollRepository.allRolls().first { $0.id == rollID }
            latestPhotoVisible = roll?.visibleCaptureIDs.contains(latest.id) ?? true
        } catch {
            latestPhotoVisible = false
        }
    }

    private func makeAlbumCapture(_ record: NativeCaptureRecord, rollID: String?, isDeveloped: Bool) -> AlbumCapture {
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
            rollID: rollID,
            rollIsDeveloped: isDeveloped,
            galleryLocalIdentifier: record.localIdentifier,
            settings: ["Capture": localizedCatalogValue("roll_original_capture"), "Recipe": record.recipe.stableID],
            authorizedLatitude: record.geotagMetadata?.coordinate.latitude,
            authorizedLongitude: record.geotagMetadata?.coordinate.longitude
        )
    }

    private func persistInAlbum(_ record: NativeVideoCaptureRecord) {
        guard let body = BuiltInCatalog.cameraBodies.first(where: { $0.id == record.recipe.cameraBodyID }),
              let lens = BuiltInCatalog.lenses.first(where: { $0.id == record.recipe.lensID }),
              let film = BuiltInCatalog.filmStocks.first(where: { $0.id == record.recipe.filmStockID }) else { return }
        let capture = AlbumCapture(
            id: record.localIdentifier,
            kind: .video,
            capturedAt: record.capturedAt,
            bodyID: body.id,
            bodyName: localizedCatalogValue(body.nameKey),
            lensID: lens.id,
            lensName: localizedCatalogValue(lens.nameKey),
            filmStockID: film.id,
            filmStockName: localizedCatalogValue(film.nameKey),
            recipeID: record.recipe.stableID,
            galleryLocalIdentifier: record.localIdentifier,
            developedGalleryLocalIdentifier: record.developedLocalIdentifier,
            settings: ["Duration": "\(record.durationSeconds)s", "Audio": record.includesAudio ? "On" : "Off"]
        )
        Task { try? await albumRepository.save(capture) }
    }

    private var activeNativeRecipe: NativeCaptureRecipe {
        let selection = currentSelection ?? WorkbenchRecipeSelection(
            cameraBodyID: BuiltInCatalog.cameraBodies[0].id,
            lensID: BuiltInCatalog.lenses[0].id,
            filmStockID: BuiltInCatalog.filmStocks[0].id
        )
        return NativeCaptureRecipe(
            cameraBodyID: selection.cameraBodyID,
            lensID: selection.lensID,
            filmStockID: selection.filmStockID
        )
    }

    private func localizedCatalogValue(_ key: String) -> String {
        let resourceKey = key.replacingOccurrences(of: ".", with: "_").replacingOccurrences(of: "-", with: "_")
        let selectedBundle = Bundle.main.path(forResource: languageTag, ofType: "lproj")
            .flatMap { Bundle(path: $0) } ?? .main
        return selectedBundle.localizedString(forKey: resourceKey, value: nil, table: nil)
    }
}
