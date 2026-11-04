import AVKit
import Photos
import SwiftUI
import UIKit

/// Full-screen, device-local viewer for an Album item's developed copy or original.
struct AlbumMediaViewer: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let localIdentifier: String
    let kind: AlbumMediaKind

    @State private var image: UIImage?
    @State private var player: AVPlayer?
    @State private var temporaryURL: URL?
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var zoomScale: CGFloat = 1
    @State private var imageOffset = CGSize.zero
    @GestureState private var magnification: CGFloat = 1
    @GestureState private var dragOffset = CGSize.zero

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black.ignoresSafeArea()
                if kind == .video {
                    if let player {
                        VideoPlayer(player: player)
                            .frame(width: geometry.size.width, height: geometry.size.height)
                            .accessibilityLabel(Text("album_viewer_video"))
                    } else if isLoading {
                        ProgressView("album_viewer_loading").tint(.white)
                    }
                } else if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .scaleEffect(min(6, max(1, zoomScale * magnification)))
                        .offset(x: imageOffset.width + dragOffset.width, y: imageOffset.height + dragOffset.height)
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .contentShape(Rectangle())
                        .gesture(zoomGesture.simultaneously(with: panGesture))
                        .onTapGesture(count: 2, perform: resetOrZoom)
                        .accessibilityLabel(Text("album_viewer_photo"))
                        .accessibilityHint(Text("album_viewer_photo_hint"))
                } else if isLoading {
                    ProgressView("album_viewer_loading").tint(.white)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white)
                        .padding(24)
                }

                VStack {
                    HStack {
                        Button { close() } label: {
                            Label("album_viewer_close", systemImage: "xmark")
                                .labelStyle(.iconOnly)
                                .font(.headline)
                                .frame(width: 48, height: 48)
                                .background(.ultraThinMaterial, in: Circle())
                        }
                        .accessibilityLabel(Text("album_viewer_close"))
                        .accessibilityHint(Text("album_viewer_close_hint"))
                        Spacer()
                        if kind == .photo, image != nil {
                            Button(action: resetZoom) {
                                Label("album_viewer_reset_zoom", systemImage: "arrow.up.left.and.arrow.down.right")
                                    .labelStyle(.iconOnly)
                                    .font(.headline)
                                    .frame(width: 48, height: 48)
                                    .background(.ultraThinMaterial, in: Circle())
                            }
                            .accessibilityLabel(Text("album_viewer_reset_zoom"))
                            .accessibilityValue(String(format: NSLocalizedString("album_viewer_zoom_value", comment: "Current photo zoom factor"), zoomScale))
                            .accessibilityHint(Text("album_viewer_reset_zoom_hint"))
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 16)
                    Spacer()
                    if kind == .photo, image != nil {
                        HStack(spacing: 28) {
                            Button { adjustZoom(by: 1 / 1.5) } label: {
                                Label("album_viewer_zoom_out", systemImage: "minus.magnifyingglass")
                                    .frame(width: 54, height: 54)
                                    .background(.ultraThinMaterial, in: Circle())
                            }
                            .disabled(zoomScale <= 1)
                            .accessibilityLabel(Text("album_viewer_zoom_out"))
                            Button { resetZoom() } label: {
                                Text(String(format: NSLocalizedString("album_viewer_zoom_value", comment: "Current photo zoom factor"), zoomScale))
                                    .font(.subheadline.monospacedDigit().weight(.semibold))
                                    .frame(minWidth: 72, minHeight: 48)
                            }
                            .accessibilityLabel(Text("album_viewer_reset_zoom"))
                            .accessibilityValue(String(format: NSLocalizedString("album_viewer_zoom_value", comment: "Current photo zoom factor"), zoomScale))
                            Button { adjustZoom(by: 1.5) } label: {
                                Label("album_viewer_zoom_in", systemImage: "plus.magnifyingglass")
                                    .frame(width: 54, height: 54)
                                    .background(.ultraThinMaterial, in: Circle())
                            }
                            .disabled(zoomScale >= 6)
                            .accessibilityLabel(Text("album_viewer_zoom_in"))
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 20)
                        .padding(.bottom, max(geometry.safeAreaInsets.bottom, 18))
                    }
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
        .statusBarHidden()
        .task { await loadMedia() }
        .onDisappear(perform: close)
        .transaction { transaction in
            if reduceMotion { transaction.animation = nil }
        }
    }

    private var zoomGesture: some Gesture {
        MagnifyGesture()
            .updating($magnification) { value, state, _ in state = value.magnification }
            .onEnded { value in
                zoomScale = min(6, max(1, zoomScale * value.magnification))
                if zoomScale == 1 { imageOffset = .zero }
            }
    }

    private var panGesture: some Gesture {
        DragGesture(minimumDistance: 8)
            .updating($dragOffset) { value, state, _ in
                guard zoomScale > 1 else { return }
                state = value.translation
            }
            .onEnded { value in
                guard zoomScale > 1 else { return }
                imageOffset.width += value.translation.width
                imageOffset.height += value.translation.height
            }
    }

    @MainActor
    private func loadMedia() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let url = try await AlbumPhotoLibrary.exportAsset(localIdentifier: localIdentifier, kind: kind)
            temporaryURL = url
            if kind == .video {
                let newPlayer = AVPlayer(url: url)
                player = newPlayer
                newPlayer.play()
            } else {
                image = UIImage(contentsOfFile: url.path)
                if image == nil { throw AlbumPhotoLibraryError.assetUnavailable }
            }
        } catch {
            errorMessage = NSLocalizedString("album_viewer_unavailable", comment: "On-device media cannot be opened")
        }
    }

    private func resetOrZoom() {
        if zoomScale > 1 { resetZoom() }
        else { adjustZoom(by: 2) }
    }

    private func adjustZoom(by factor: CGFloat) {
        let update = { zoomScale = min(6, max(1, zoomScale * factor)) }
        if reduceMotion { update() }
        else { withAnimation(.easeOut(duration: 0.18), update) }
        if zoomScale == 1 { imageOffset = .zero }
    }

    private func resetZoom() {
        let update = { zoomScale = 1; imageOffset = .zero }
        if reduceMotion { update() }
        else { withAnimation(.easeOut(duration: 0.18), update) }
    }

    private func close() {
        player?.pause()
        player = nil
        if let temporaryURL { try? FileManager.default.removeItem(at: temporaryURL) }
        temporaryURL = nil
    }
}
