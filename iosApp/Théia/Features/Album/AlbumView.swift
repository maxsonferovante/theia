import Photos
import SwiftUI
import UIKit

struct AlbumView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model: AlbumViewModel

    init(repository: any AlbumMetadataRepository = JSONAlbumMetadataRepository()) {
        _model = StateObject(wrappedValue: AlbumViewModel(repository: repository))
    }

    var body: some View {
        NavigationStack {
            Group {
                if model.isLoading {
                    ProgressView("Loading album…")
                } else if model.groupedCaptures.isEmpty {
                    ContentUnavailableView(
                        model.searchText.isEmpty ? "Your album is empty" : "No matching captures",
                        systemImage: model.searchText.isEmpty ? "photo.on.rectangle.angled" : "magnifyingglass",
                        description: Text(model.searchText.isEmpty
                                         ? "Photos you capture with Théia will appear here."
                                         : "Try another film stock, lens, camera, or date.")
                    )
                } else {
                    List {
                        ForEach(model.groupedCaptures, id: \.title) { group in
                            Section(group.title) {
                                ForEach(group.captures) { capture in
                                    AlbumRow(
                                        capture: capture,
                                        onOpen: { model.selectedCapture = capture },
                                        onFavorite: { Task { await model.toggleFavorite(capture) } },
                                        onDelete: { model.requestDelete(capture) }
                                    )
                                        .swipeActions(edge: .leading) {
                                            Button {
                                                Task { await model.toggleFavorite(capture) }
                                            } label: {
                                                Label(capture.isFavorite ? "Unfavorite" : "Favorite",
                                                      systemImage: capture.isFavorite ? "heart.slash" : "heart")
                                            }
                                            .tint(.pink)
                                        }
                                        .swipeActions(edge: .trailing) {
                                            Button(role: .destructive) {
                                                model.requestDelete(capture)
                                            } label: {
                                                Label("Delete", systemImage: "trash")
                                            }
                                        }
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Album")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Picker("Group by", selection: $model.grouping) {
                        ForEach(AlbumGrouping.allCases) { grouping in
                            Text(LocalizedStringKey(grouping.rawValue)).tag(grouping)
                        }
                    }
                    .labelsHidden()
                    .accessibilityLabel("Group captures by")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        model.favoriteOnly.toggle()
                    } label: {
                        Image(systemName: model.favoriteOnly ? "heart.fill" : "heart")
                            .foregroundStyle(model.favoriteOnly ? .pink : .primary)
                    }
                    .accessibilityLabel(model.favoriteOnly ? "Show all captures" : "Show favorites")
                }
            }
            .searchable(text: $model.searchText, prompt: "Film, lens, camera, or date")
            .task { await model.load() }
            .sheet(item: $model.selectedCapture) { capture in
                AlbumDetailView(capture: capture, onFavorite: {
                    Task { await model.toggleFavorite(capture) }
                }, onDelete: {
                    model.selectedCapture = nil
                    model.requestDelete(capture)
                }, onRemoveLocation: {
                    try await model.removeLocation(capture)
                })
            }
            .confirmationDialog(
                "Remove this capture?",
                isPresented: $model.isShowingDeleteConfirmation,
                presenting: model.pendingDeletion
            ) { capture in
                Button("Remove from Théia only", role: .destructive) {
                    Task { await model.remove(capture, includingPhotosItem: false) }
                }
                if capture.galleryLocalIdentifier != nil || capture.developedGalleryLocalIdentifier != nil {
                    Button("Remove from Théia and Photos", role: .destructive) {
                        Task { await model.remove(capture, includingPhotosItem: true) }
                    }
                }
                Button("Cancel", role: .cancel) { model.pendingDeletion = nil }
            } message: { capture in
                Text(capture.galleryLocalIdentifier == nil
                     ? "This deletes the capture metadata from Théia. There is no linked Photos copy to delete."
                     : "Remove from Théia only keeps its Photos copies. Remove from Théia and Photos deletes this entry and its linked original and developed copies.")
            }
            .alert("Album", isPresented: $model.showError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(model.errorMessage)
            }
            .transaction { transaction in
                if reduceMotion { transaction.animation = nil }
            }
        }
    }
}

private struct AlbumRow: View {
    let capture: AlbumCapture
    let onOpen: () -> Void
    let onFavorite: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                AlbumThumbnail(localIdentifier: capture.developedGalleryLocalIdentifier ?? capture.galleryLocalIdentifier)
                    .frame(width: 72, height: 72)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 5) {
                    Text(capture.filmStockName).font(.headline)
                    Text("\(capture.bodyName) · \(capture.lensName)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        Text(capture.capturedAt.formatted(date: .abbreviated, time: .shortened))
                        if let rollID = capture.rollID {
                            Text("Roll \(rollID)")
                        }
                        if !capture.rollIsDeveloped, capture.rollID != nil {
                            Text("Undeveloped").foregroundStyle(.orange)
                        } else if capture.rollIsDeveloped {
                            Text("Developed")
                        }
                        if capture.kind == .video { Text("Video") }
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                if capture.isFavorite {
                    Label("Favorite", systemImage: "heart.fill")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.pink)
                }
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(Text("Opens capture details in the Lab."))
        .accessibilityAction(named: Text(capture.isFavorite ? "Remove from favorites" : "Add to favorites"), onFavorite)
        .accessibilityAction(named: Text("Delete capture"), onDelete)
    }
}

struct AlbumDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shareURL: URL?
    @State private var isPreparingShare = false
    @State private var isShareSheetPresented = false
    @State private var shareError: String?
    @State private var isShareErrorPresented = false
    @State private var geotagMetadata: LocalGeotagMetadata?
    @State private var isRemoveLocationConfirmationPresented = false
    @State private var locationError: String?
    @State private var isLocationErrorPresented = false
    @State private var isMediaViewerPresented = false
    let capture: AlbumCapture
    var onFavorite: () -> Void
    var onDelete: () -> Void
    var onRemoveLocation: () async throws -> Void
    private let geotagRepository = JSONLocalGeotagMetadataRepository()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Button { isMediaViewerPresented = true } label: {
                        ZStack {
                            AlbumThumbnail(
                                localIdentifier: capture.developedGalleryLocalIdentifier ?? capture.galleryLocalIdentifier,
                                contentMode: .fit
                            )
                            if capture.kind == .video {
                                Image(systemName: "play.circle.fill")
                                    .font(.system(size: 58))
                                    .symbolRenderingMode(.hierarchical)
                                    .foregroundStyle(.white)
                                    .shadow(radius: 8)
                                    .accessibilityHidden(true)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 240)
                        .background(Color.black.opacity(0.08), in: RoundedRectangle(cornerRadius: 18))
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                    }
                    .buttonStyle(.plain)
                    .disabled(capture.developedGalleryLocalIdentifier == nil && capture.galleryLocalIdentifier == nil)
                    .accessibilityLabel(Text(capture.kind == .video ? "album_viewer_open_video" : "album_viewer_open_photo"))
                    .accessibilityHint(Text("album_viewer_open_hint"))

                    VStack(alignment: .leading, spacing: 6) {
                        Text(capture.filmStockName).font(.title2.weight(.semibold))
                        Text(capture.capturedAt.formatted(date: .long, time: .shortened))
                            .foregroundStyle(.secondary)
                    }

                    metadataSection("Recipe") {
                        detailRow("Camera", capture.bodyName)
                        detailRow("Lens", capture.lensName)
                        detailRow("Film stock", capture.filmStockName)
                        detailRow("Recipe ID", capture.recipeID)
                    }
                    metadataSection("Roll") {
                        detailRow("Roll", capture.rollID ?? "Not assigned")
                        if capture.rollID != nil {
                            detailRow("Development", capture.rollIsDeveloped ? "Developed" : "Undeveloped")
                        }
                    }
                    if !capture.settings.isEmpty {
                        metadataSection("Capture settings") {
                            ForEach(capture.settings.keys.sorted(), id: \.self) { key in
                                detailRow(key, capture.settings[key] ?? "")
                            }
                        }
                    }
                    if let geotagMetadata {
                        metadataSection("Location") {
                            detailRow("Authorized geotag", String(format: "%.5f, %.5f", geotagMetadata.coordinate.latitude, geotagMetadata.coordinate.longitude))
                            detailRow(NSLocalizedString("album_geotag_recorded", comment: "Geotag recorded date label"), geotagMetadata.recordedAt.formatted(date: .long, time: .shortened))
                            if let precision = geotagMetadata.precisionMeters {
                                detailRow(NSLocalizedString("album_geotag_accuracy", comment: "Geotag accuracy label"), String(format: "±%.0f m", precision))
                            }
                            OfflineLocationPlotView(
                                locations: [geotagMetadata.coordinate],
                                plotTitle: NSLocalizedString("album_location_plot_title", comment: "Album geotag coordinate plot title")
                            )
                            Button(role: .destructive) {
                                isRemoveLocationConfirmationPresented = true
                            } label: {
                                Label("album_remove_saved_location", systemImage: "location.slash")
                                    .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                            }
                            .accessibilityHint(Text("album_remove_saved_location_hint"))
                        }
                    }
                }
                .padding(20)
            }
            .navigationTitle("Lab")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Done") { dismiss() }
                        .accessibilityHint(Text("Closes capture details."))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onFavorite) {
                        Image(systemName: capture.isFavorite ? "heart.fill" : "heart")
                            .foregroundStyle(capture.isFavorite ? .pink : .primary)
                    }
                    .accessibilityLabel(capture.isFavorite ? "Unfavorite capture" : "Favorite capture")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await prepareShare() }
                    } label: {
                        if isPreparingShare {
                            ProgressView()
                        } else {
                            Image(systemName: "square.and.arrow.up")
                        }
                    }
                    .disabled(isPreparingShare || capture.galleryLocalIdentifier == nil)
                    .accessibilityLabel("Share capture")
                    .accessibilityHint(Text("Shares the media file and its camera recipe."))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .destructive, action: onDelete) {
                        Image(systemName: "trash")
                    }
                    .accessibilityLabel("Delete capture")
                }
            }
            .transaction { transaction in
                if reduceMotion { transaction.animation = nil }
            }
            .sheet(isPresented: $isShareSheetPresented, onDismiss: removeShareFile) {
                if let shareURL {
                    NativeShareSheet(activityItems: [shareURL, shareDescription])
                }
            }
            .fullScreenCover(isPresented: $isMediaViewerPresented) {
                if let identifier = capture.developedGalleryLocalIdentifier ?? capture.galleryLocalIdentifier {
                    AlbumMediaViewer(localIdentifier: identifier, kind: capture.kind)
                }
            }
            .alert("Unable to share capture", isPresented: $isShareErrorPresented) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(shareError ?? "The capture could not be exported.")
            }
            .onDisappear(perform: removeShareFile)
            .task { await loadGeotag() }
            .confirmationDialog(
                "album_remove_saved_location_confirmation_title",
                isPresented: $isRemoveLocationConfirmationPresented,
                titleVisibility: .visible
            ) {
                Button("album_remove_saved_location_confirmation_action", role: .destructive) {
                    Task { await removeGeotag() }
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("album_remove_saved_location_confirmation_message")
            }
            .alert("Location", isPresented: $isLocationErrorPresented) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(locationError ?? "The saved location could not be removed.")
            }
        }
    }

    @ViewBuilder
    private func metadataSection<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline)
            VStack(spacing: 8, content: content)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).foregroundStyle(.secondary)
            Spacer(minLength: 14)
            Text(value).multilineTextAlignment(.trailing)
        }
        .font(.subheadline)
    }

    private var shareDescription: String {
        String(
            format: NSLocalizedString("album_share_recipe_format", comment: "Compact recipe text shared with a media asset"),
            capture.bodyName,
            capture.lensName,
            capture.filmStockName
        )
    }

    @MainActor
    private func prepareShare() async {
        guard let identifier = capture.developedGalleryLocalIdentifier ?? capture.galleryLocalIdentifier else { return }
        isPreparingShare = true
        defer { isPreparingShare = false }
        do {
            shareURL = try await AlbumPhotoLibrary.exportAsset(localIdentifier: identifier, kind: capture.kind)
            isShareSheetPresented = true
        } catch {
            shareError = error.localizedDescription
            isShareErrorPresented = true
        }
    }

    private func removeShareFile() {
        if let shareURL { try? FileManager.default.removeItem(at: shareURL) }
        shareURL = nil
    }

    @MainActor
    private func loadGeotag() async {
        guard capture.authorizedLatitude != nil, capture.authorizedLongitude != nil else { return }
        do {
            if let saved = try await geotagRepository.exportJSON(for: capture.id) {
                let decoder = JSONDecoder()
                decoder.dateDecodingStrategy = .iso8601
                geotagMetadata = try decoder.decode(LocalGeotagMetadata.self, from: saved)
            } else if let latitude = capture.authorizedLatitude, let longitude = capture.authorizedLongitude {
                geotagMetadata = LocalGeotagMetadata(
                    coordinate: LocalGeotagCoordinate(latitude: latitude, longitude: longitude),
                    recordedAt: capture.capturedAt,
                    precisionMeters: nil
                )
            }
        } catch {
            locationError = error.localizedDescription
            isLocationErrorPresented = true
        }
    }

    @MainActor
    private func removeGeotag() async {
        do {
            try await onRemoveLocation()
            geotagMetadata = nil
        } catch {
            locationError = error.localizedDescription
            isLocationErrorPresented = true
        }
    }
}

private struct NativeShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) { }
}

private struct AlbumThumbnail: View {
    let localIdentifier: String?
    var contentMode: ContentMode = .fill

    @State private var image: UIImage?

    var body: some View {
        GeometryReader { geometry in
            Group {
                if let image {
                    Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode)
                } else {
                    ZStack {
                        Color(red: 0.09, green: 0.11, blue: 0.18)
                        Image(systemName: "photo")
                            .font(.system(size: 28))
                            .foregroundStyle(.white.opacity(0.55))
                    }
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
            .task(id: localIdentifier) { await loadThumbnail(size: geometry.size) }
        }
    }

    @MainActor
    private func loadThumbnail(size: CGSize) async {
        guard let localIdentifier else { image = nil; return }
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: [localIdentifier], options: nil)
        guard let asset = assets.firstObject else { image = nil; return }
        let options = PHImageRequestOptions()
        options.deliveryMode = .opportunistic
        options.resizeMode = .fast
        PHImageManager.default().requestImage(
            for: asset,
            targetSize: CGSize(width: max(1, size.width * 2), height: max(1, size.height * 2)),
            contentMode: .aspectFill,
            options: options
        ) { result, _ in
            if let result { Task { @MainActor in image = result } }
        }
    }
}

@MainActor
private final class AlbumViewModel: ObservableObject {
    struct Group: Identifiable {
        let title: String
        let captures: [AlbumCapture]
        var id: String { title }
    }

    @Published private(set) var captures: [AlbumCapture] = []
    @Published var grouping: AlbumGrouping = .date
    @Published var favoriteOnly = false
    @Published var searchText = ""
    @Published var selectedCapture: AlbumCapture?
    @Published var pendingDeletion: AlbumCapture?
    @Published var isShowingDeleteConfirmation = false
    @Published private(set) var isLoading = true
    @Published var showError = false
    @Published private(set) var errorMessage = ""

    private let repository: any AlbumMetadataRepository
    private let geotagRepository = JSONLocalGeotagMetadataRepository()

    init(repository: any AlbumMetadataRepository) { self.repository = repository }

    var groupedCaptures: [Group] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let visible = captures.filter { capture in
            let visibleAfterDevelopment = capture.kind == .video || capture.rollID == nil || capture.rollIsDeveloped || capture.developedGalleryLocalIdentifier != nil
            guard visibleAfterDevelopment else { return false }
            return (!favoriteOnly || capture.isFavorite) &&
            (query.isEmpty || capture.searchText.localizedCaseInsensitiveContains(query) ||
             capture.capturedAt.formatted(date: .long, time: .omitted).localizedCaseInsensitiveContains(query))
        }
        let grouped = Dictionary(grouping: visible) { $0.groupTitle(for: grouping) }
        return grouped.keys.sorted(by: groupOrder).map { title in
            Group(title: title, captures: (grouped[title] ?? []).sorted { $0.capturedAt > $1.capturedAt })
        }
    }

    func load() async {
        isLoading = true
        defer { isLoading = false }
        do { captures = try await repository.allCaptures().sorted { $0.capturedAt > $1.capturedAt } }
        catch { present(error) }
    }

    func toggleFavorite(_ capture: AlbumCapture) async {
        guard let index = captures.firstIndex(where: { $0.id == capture.id }) else { return }
        var updated = captures[index]
        updated.isFavorite.toggle()
        do {
            try await repository.save(updated)
            captures[index] = updated
            if selectedCapture?.id == updated.id { selectedCapture = updated }
        } catch { present(error) }
    }

    func requestDelete(_ capture: AlbumCapture) {
        pendingDeletion = capture
        isShowingDeleteConfirmation = true
    }

    func remove(_ capture: AlbumCapture, includingPhotosItem: Bool) async {
        do {
            if includingPhotosItem {
                try await AlbumPhotoLibrary.deleteAssets(
                    localIdentifiers: [capture.galleryLocalIdentifier, capture.developedGalleryLocalIdentifier].compactMap { $0 }
                )
            }
            try await repository.remove(id: capture.id)
            captures.removeAll { $0.id == capture.id }
            pendingDeletion = nil
        } catch { present(error) }
    }

    func removeLocation(_ capture: AlbumCapture) async throws {
        var updated = capture
        updated.authorizedLatitude = nil
        updated.authorizedLongitude = nil
        try await repository.save(updated)
        do {
            try await geotagRepository.removeLocation(for: capture.id)
        } catch {
            try? await repository.save(capture)
            throw error
        }
        if let index = captures.firstIndex(where: { $0.id == capture.id }) { captures[index] = updated }
        if selectedCapture?.id == capture.id { selectedCapture = updated }
    }

    private func groupOrder(_ left: String, _ right: String) -> Bool {
        if grouping == .date { return left.localizedStandardCompare(right) == .orderedDescending }
        return left.localizedStandardCompare(right) == .orderedAscending
    }

    private func present(_ error: Error) {
        errorMessage = error.localizedDescription
        showError = true
    }
}
