import AVFoundation
import CoreMedia
import Photos
import SwiftUI
import UIKit
import CoreImage

struct NativeVideoCaptureRecord: Equatable {
    let localIdentifier: String
    let developedLocalIdentifier: String?
    let recipe: NativeCaptureRecipe
    let capturedAt: Date
    let durationSeconds: Int
    let includesAudio: Bool
    let videoStyleID: String
}

private struct VideoStyleOption: Identifiable {
    let id: String
    let localizationKey: String
}

private enum VideoStyleOptions {
    static let equipmentRecipeID = "equipment-recipe"
    static let choices = [
        VideoStyleOption(id: equipmentRecipeID, localizationKey: "video_style_recipe"),
        VideoStyleOption(id: "cinematic-warm", localizationKey: "video_style_cinematic_warm"),
        VideoStyleOption(id: "moonlit-monochrome", localizationKey: "video_style_moonlit_monochrome"),
    ]
}

/// Native short-video capture. Video does not use or advance a film roll.
struct VideoCaptureView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let recipe: NativeCaptureRecipe
    var onVideoSaved: (NativeVideoCaptureRecord) -> Void = { _ in }

    @StateObject private var recorder = VideoCaptureModel()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if recorder.cameraReady {
                VideoPreview(image: recorder.previewFrame).ignoresSafeArea()
                    .overlay(alignment: .topLeading) {
                        Text("\(recipe.cameraBodyID) · \(recipe.lensID) · \(recipe.filmStockID)")
                            .font(.caption.weight(.medium))
                            .lineLimit(1)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(.black.opacity(0.58), in: Capsule())
                            .padding(16)
                    }
                    .overlay(alignment: .bottom) { controls }
            } else {
                permissionView
            }

            if recorder.isSaving {
                ProgressView(NSLocalizedString("video_saving", comment: "Progress while saving a video"))
                    .tint(.white)
                    .padding(20)
                    .background(.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 16))
                    .foregroundStyle(.white)
            }
        }
        .preferredColorScheme(.dark)
        .task { recorder.prepareCameraPermissionState(recipe: recipe) }
        .onDisappear { recorder.stopSession() }
        .onChange(of: recorder.savedRecord) { _, record in
            if let record { onVideoSaved(record) }
        }
        .alert(recorder.alertTitle, isPresented: $recorder.showAlert) {
            if recorder.showSettingsAction {
                Button(NSLocalizedString("capture_open_settings", comment: "Open system settings action")) { recorder.openSettings() }
            }
            Button(NSLocalizedString("capture_ok", comment: "Dismiss alert action"), role: .cancel) { }
        } message: {
            Text(recorder.alertMessage)
        }
        .transaction { transaction in
            if reduceMotion { transaction.animation = nil }
        }
    }

    private var controls: some View {
        VStack(spacing: 20) {
            Picker(NSLocalizedString("video_style_label", comment: "Choose a video visual style"), selection: Binding(
                get: { recorder.selectedVideoStyleID },
                set: { recorder.selectVideoStyle($0, recipe: recipe) }
            )) {
                ForEach(VideoStyleOptions.choices) { choice in
                    Text(NSLocalizedString(choice.localizationKey, comment: "Video visual style option"))
                        .tag(choice.id)
                }
            }
            .pickerStyle(.segmented)
            .disabled(recorder.isRecording || recorder.isSaving)
            .accessibilityLabel(NSLocalizedString("video_style_label", comment: "Choose a video visual style"))

            HStack {
                Label(NSLocalizedString(recorder.includesAudio ? "video_audio_on" : "video_audio_off", comment: "Audio recording status"), systemImage: recorder.includesAudio ? "mic.fill" : "mic.slash")
                    .font(.subheadline.weight(.medium))
                Spacer()
                Toggle(NSLocalizedString("video_audio_toggle", comment: "Opt in to microphone recording"), isOn: Binding(
                    get: { recorder.includesAudio },
                    set: { enabled in Task { await recorder.setAudioEnabled(enabled) } }
                ))
                .labelsHidden()
                .disabled(recorder.isRecording)
                .accessibilityLabel(NSLocalizedString("video_audio_accessibility", comment: "Audio toggle accessibility label"))
                .accessibilityValue(NSLocalizedString(recorder.includesAudio ? "video_audio_on" : "video_audio_off", comment: "Audio toggle current value"))
            }

            HStack {
                Text(NSLocalizedString(recorder.isRecording ? "video_recording_status" : "video_ready_status", comment: "Video recording state"))
                    .font(.caption.weight(.semibold))
                    .accessibilityLabel(NSLocalizedString(recorder.isRecording ? "video_recording_status" : "video_ready_status", comment: "Video recording status"))
                Spacer()
                Text(String(format: NSLocalizedString("video_duration_format", comment: "Elapsed video duration"), recorder.elapsedSeconds))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.white.opacity(0.84))
                Text(recorder.framesPerSecond > 0
                     ? String(format: NSLocalizedString("video_quality_format", comment: "Selected video quality"), recorder.framesPerSecond)
                     : NSLocalizedString("video_quality_device_default", comment: "The device-selected video frame rate"))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.84))
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(String(format: NSLocalizedString(recorder.isRecording ? "video_recording_duration_accessibility" : "video_duration_accessibility", comment: "Video recording status and duration"), recorder.elapsedSeconds))

            Button {
                recorder.isRecording ? recorder.stopRecording() : recorder.startRecording(recipe: recipe)
            } label: {
                ZStack {
                    Circle().stroke(.white, lineWidth: 4).frame(width: 74, height: 74)
                    if recorder.isRecording {
                        RoundedRectangle(cornerRadius: 7).fill(.red).frame(width: 28, height: 28)
                    } else {
                        Circle().fill(.red).frame(width: 56, height: 56)
                    }
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
            }
            .disabled(recorder.isSaving || !recorder.cameraReady)
            .accessibilityLabel(NSLocalizedString(recorder.isRecording ? "video_stop" : "video_start", comment: "Video recording control accessibility label"))
        }
        .padding(.horizontal, 28)
        .padding(.top, 20)
        .padding(.bottom, 32)
        .background(LinearGradient(colors: [.clear, .black.opacity(0.76)], startPoint: .top, endPoint: .bottom))
        .foregroundStyle(.white)
    }

    private var permissionView: some View {
        VStack(spacing: 16) {
            Image(systemName: "video").font(.system(size: 38)).foregroundStyle(Color(red: 0.92, green: 0.68, blue: 0.21))
            Text(NSLocalizedString(recorder.cameraDenied ? "capture_camera_access_off" : recorder.shouldExplainCameraPermission ? "capture_camera_permission_heading" : "capture_preparing_camera", comment: "Camera permission or loading title"))
                .font(.title3.weight(.semibold))
            Text(NSLocalizedString(recorder.cameraDenied
                                   ? "video_camera_permission_denied"
                                   : recorder.shouldExplainCameraPermission ? "video_camera_permission_rationale" : "video_audio_default_off", comment: "Video capture permission explanation"))
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.75))
            if recorder.cameraDenied {
                Button(NSLocalizedString("capture_open_settings", comment: "Open system settings action")) { recorder.openSettings() }
                    .buttonStyle(.borderedProminent)
            } else if recorder.shouldExplainCameraPermission {
                Button(NSLocalizedString("capture_continue", comment: "Continue to camera permission prompt")) {
                    Task { await recorder.requestCameraPermission() }
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(32)
        .foregroundStyle(.white)
    }
}

@MainActor
private final class VideoCaptureModel: ObservableObject {
    @Published private(set) var previewFrame: CGImage?
    @Published private(set) var cameraReady = false
    @Published private(set) var cameraDenied = false
    @Published private(set) var shouldExplainCameraPermission = false
    @Published private(set) var includesAudio = false
    @Published private(set) var selectedVideoStyleID = VideoStyleOptions.equipmentRecipeID
    @Published private(set) var isRecording = false
    @Published private(set) var isSaving = false
    @Published private(set) var elapsedSeconds = 0
    @Published private(set) var framesPerSecond = 30
    @Published private(set) var savedRecord: NativeVideoCaptureRecord?
    @Published var showAlert = false
    @Published private(set) var alertTitle = NSLocalizedString("video_title", comment: "Video capture alert title")
    @Published private(set) var alertMessage = ""
    @Published private(set) var showSettingsAction = false

    private let sessionQueue = DispatchQueue(label: "app.theia.video.session")
    private let resources = VideoSessionResources()
    private let frameProcessor = NativeFilmVideoFrameProcessor()
    private var recordingDelegate: VideoFileRecordingDelegate?
    private var recordingTask: Task<Void, Never>?
    private var recordingStartedAt: Date?
    private var pendingRecipe: NativeCaptureRecipe?
    private var pendingAudio = false
    private var pendingVideoStyleID = VideoStyleOptions.equipmentRecipeID

    init() {
        frameProcessor.onPreviewFrame = { [weak self] image in self?.previewFrame = image }
    }

    func prepareCameraPermissionState(recipe: NativeCaptureRecipe) {
        do {
            guard let parameters = try NativeFilmVisualProfileCatalog.resolve(recipe: recipe, videoStyleID: selectedVideoStyleID) else {
                throw NativeFilmImageProcessorError.unsupportedProfileVersion
            }
            frameProcessor.configure(parameters: parameters)
        } catch {
            present(NSLocalizedString("video_profile_unavailable", comment: "Film profile unavailable"), settings: false)
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configureSession()
        case .notDetermined:
            shouldExplainCameraPermission = true
        case .denied, .restricted: cameraDenied = true
        @unknown default: cameraDenied = true
        }
    }

    func selectVideoStyle(_ styleID: String, recipe: NativeCaptureRecipe) {
        guard !isRecording, !isSaving, selectedVideoStyleID != styleID else { return }
        do {
            guard let parameters = try NativeFilmVisualProfileCatalog.resolve(recipe: recipe, videoStyleID: styleID) else {
                throw NativeFilmImageProcessorError.unsupportedProfileVersion
            }
            selectedVideoStyleID = styleID
            frameProcessor.configure(parameters: parameters)
        } catch {
            present(NSLocalizedString("video_profile_unavailable", comment: "Film profile unavailable"), settings: false)
        }
    }

    func requestCameraPermission() async {
        shouldExplainCameraPermission = false
        let allowed = await AVCaptureDevice.requestAccess(for: .video)
        cameraDenied = !allowed
        if allowed { configureSession() }
    }

    func setAudioEnabled(_ enabled: Bool) async {
        guard enabled != includesAudio else { return }
        guard enabled else {
            includesAudio = false
            pendingAudio = false
            removeAudioInput()
            return
        }

        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .authorized:
            await installAudioInput()
        case .notDetermined:
            let allowed = await AVCaptureDevice.requestAccess(for: .audio)
            if allowed { await installAudioInput() }
            else { present(NSLocalizedString("video_microphone_denied", comment: "Microphone permission denied"), settings: false) }
        case .denied, .restricted:
            present(NSLocalizedString("video_microphone_settings", comment: "Microphone permission recovery explanation"), settings: true)
        @unknown default:
            present(NSLocalizedString("video_microphone_unavailable", comment: "Microphone unavailable"), settings: false)
        }
    }

    func startRecording(recipe: NativeCaptureRecipe) {
        guard !isRecording, cameraReady else { return }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("theia-video-\(UUID().uuidString).mov")
        try? FileManager.default.removeItem(at: url)
        pendingRecipe = recipe
        pendingAudio = includesAudio
        pendingVideoStyleID = selectedVideoStyleID
        elapsedSeconds = 0
        recordingStartedAt = Date()
        let delegate = VideoFileRecordingDelegate { [weak self] outputURL, error in
            Task { @MainActor in
                guard let self else { return }
                self.isRecording = false
                self.recordingTask?.cancel()
                self.recordingTask = nil
                if let error { self.present(String(format: NSLocalizedString("video_record_error_format", comment: "Recording error"), error.localizedDescription), settings: false) }
                else if let outputURL, let recipe = self.pendingRecipe {
                    await self.saveVideo(
                        outputURL,
                        recipe: recipe,
                        includesAudio: self.pendingAudio,
                        videoStyleID: self.pendingVideoStyleID
                    )
                }
                self.recordingDelegate = nil
            }
        }
        recordingDelegate = delegate
        isRecording = true
        let resources = resources
        sessionQueue.async {
            guard !resources.movieOutput.isRecording else { return }
            resources.movieOutput.startRecording(to: url, recordingDelegate: delegate)
        }
        recordingTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                guard let self, self.isRecording else { return }
                self.elapsedSeconds = min(60, Int(Date().timeIntervalSince(self.recordingStartedAt ?? Date())))
                if self.elapsedSeconds >= 60 { self.stopRecording(); return }
            }
        }
    }

    func stopRecording() {
        guard isRecording else { return }
        let resources = resources
        sessionQueue.async {
            guard resources.movieOutput.isRecording else { return }
            resources.movieOutput.stopRecording()
        }
    }

    func stopSession() {
        recordingTask?.cancel()
        let resources = resources
        sessionQueue.async { [weak self] in
            guard resources.session.isRunning else { return }
            if resources.movieOutput.isRecording { resources.movieOutput.stopRecording() }
            resources.session.stopRunning()
            Task { @MainActor in self?.cameraReady = false }
        }
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func configureSession() {
        let resources = resources
        let frameProcessor = frameProcessor
        let sessionQueue = sessionQueue
        sessionQueue.async { [weak self] in
            guard !resources.session.isRunning else { Task { @MainActor in self?.cameraReady = true }; return }
            if resources.videoInput != nil {
                resources.session.startRunning()
                Task { @MainActor in self?.cameraReady = true }
                return
            }

            resources.session.beginConfiguration()
            if resources.session.canSetSessionPreset(.hd1920x1080) { resources.session.sessionPreset = .hd1920x1080 }
            else { resources.session.sessionPreset = .high }
            resources.videoDataOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
            ]
            resources.videoDataOutput.alwaysDiscardsLateVideoFrames = true
            resources.videoDataOutput.setSampleBufferDelegate(frameProcessor, queue: sessionQueue)
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device),
                  resources.session.canAddInput(input), resources.session.canAddOutput(resources.movieOutput),
                  resources.session.canAddOutput(resources.videoDataOutput) else {
                resources.session.commitConfiguration()
                Task { @MainActor in self?.present(NSLocalizedString("video_camera_unavailable", comment: "No compatible camera error"), settings: false) }
                return
            }
            resources.session.addInput(input)
            let frameRate = Self.configureFrameRate(device)
            resources.session.addOutput(resources.movieOutput)
            resources.session.addOutput(resources.videoDataOutput)
            if let connection = resources.videoDataOutput.connection(with: .video), connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            if let connection = resources.movieOutput.connection(with: .video), connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            resources.videoInput = input
            resources.session.commitConfiguration()
            resources.session.startRunning()
            Task { @MainActor in
                self?.framesPerSecond = frameRate.framesPerSecond
                if frameRate.usedFallback {
                    self?.present(NSLocalizedString("video_frame_rate_fallback", comment: "Frame rate fallback information"), settings: false)
                }
                self?.cameraReady = true
            }
        }
    }

    private nonisolated static func configureFrameRate(_ device: AVCaptureDevice) -> (framesPerSecond: Int, usedFallback: Bool) {
        let ranges = device.activeFormat.videoSupportedFrameRateRanges
        let supportedRates = ranges.flatMap { range -> [Int] in
            let upperBound = min(30, Int(floor(range.maxFrameRate)))
            let lowerBound = Int(ceil(range.minFrameRate))
            guard lowerBound <= upperBound else { return [] }
            return Array(lowerBound...upperBound)
        }
        guard let target = supportedRates.max() else { return (0, true) }

        do {
            try device.lockForConfiguration()
            defer { device.unlockForConfiguration() }
            let frameDuration = CMTime(value: 1, timescale: CMTimeScale(target))
            device.activeVideoMinFrameDuration = frameDuration
            device.activeVideoMaxFrameDuration = frameDuration
            return (target, target != 30)
        } catch {
            // Do not report a rate that the device did not accept.
            return (0, true)
        }
    }

    private func installAudioInput() async {
        let resources = resources
        let alreadyInstalled = await withCheckedContinuation { continuation in
            sessionQueue.async { continuation.resume(returning: resources.audioInput != nil) }
        }
        guard !alreadyInstalled else { includesAudio = true; return }
        guard let device = AVCaptureDevice.default(for: .audio),
              let input = try? AVCaptureDeviceInput(device: device) else {
            present(NSLocalizedString("video_microphone_unavailable", comment: "Microphone unavailable"), settings: false)
            return
        }
        let inputReference = VideoCaptureInputReference(input)
        await withCheckedContinuation { continuation in
            sessionQueue.async { [weak self] in
                resources.session.beginConfiguration()
                if resources.session.canAddInput(inputReference.value) {
                    resources.session.addInput(inputReference.value)
                    resources.audioInput = inputReference.value
                    Task { @MainActor in self?.includesAudio = true }
                } else {
                    Task { @MainActor in self?.present(NSLocalizedString("video_microphone_configuration_error", comment: "Microphone configuration error"), settings: false) }
                }
                resources.session.commitConfiguration()
                continuation.resume()
            }
        }
    }

    private func removeAudioInput() {
        let resources = resources
        sessionQueue.async {
            guard let audioInput = resources.audioInput else { return }
            resources.session.beginConfiguration()
            resources.session.removeInput(audioInput)
            resources.audioInput = nil
            resources.session.commitConfiguration()
        }
    }

    private func saveVideo(_ url: URL, recipe: NativeCaptureRecipe, includesAudio: Bool, videoStyleID: String) async {
        isSaving = true
        let developedURL = try? await movieWithFilmProfile(url, recipe: recipe, videoStyleID: videoStyleID)
        if developedURL == nil {
            present(NSLocalizedString("video_profile_export_fallback", comment: "Filtered video export failed; original will be saved"), settings: false)
        }
        let originalMetadataURL = try? await movieWithRecipeMetadata(url, recipe: recipe, videoStyleID: videoStyleID)
        let developedMetadataURL: URL?
        if let developedURL { developedMetadataURL = try? await movieWithRecipeMetadata(developedURL, recipe: recipe, videoStyleID: videoStyleID) }
        else { developedMetadataURL = nil }
        defer {
            isSaving = false
            try? FileManager.default.removeItem(at: url)
            if let originalMetadataURL { try? FileManager.default.removeItem(at: originalMetadataURL) }
            if let developedURL { try? FileManager.default.removeItem(at: developedURL) }
            if let developedMetadataURL { try? FileManager.default.removeItem(at: developedMetadataURL) }
        }
        do {
            let status = PHPhotoLibrary.authorizationStatus(for: .addOnly)
            if status == .notDetermined { _ = await PHPhotoLibrary.requestAuthorization(for: .addOnly) }
            let finalStatus = PHPhotoLibrary.authorizationStatus(for: .addOnly)
            guard finalStatus == .authorized || finalStatus == .limited else {
                throw VideoCaptureError.photoPermissionDenied
            }
            let originalIdentifier = try await saveVideoAsset(originalMetadataURL ?? url)
            var developedIdentifier: String?
            if let developedURL {
                do { developedIdentifier = try await saveVideoAsset(developedMetadataURL ?? developedURL) }
                catch { present(NSLocalizedString("video_developed_copy_save_fallback", comment: "Filtered copy save failed; original remains saved"), settings: false) }
            }
            let duration = max(1, min(60, Int(Date().timeIntervalSince(recordingStartedAt ?? Date()))))
            savedRecord = NativeVideoCaptureRecord(
                localIdentifier: originalIdentifier,
                developedLocalIdentifier: developedIdentifier,
                recipe: recipe,
                capturedAt: recordingStartedAt ?? Date(),
                durationSeconds: duration,
                includesAudio: includesAudio,
                videoStyleID: videoStyleID
            )
        } catch { present(error.localizedDescription, settings: false) }
    }

    private func saveVideoAsset(_ videoURL: URL) async throws -> String {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            var localIdentifier: String?
            PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                request.addResource(with: .video, fileURL: videoURL, options: nil)
                localIdentifier = request.placeholderForCreatedAsset?.localIdentifier
            } completionHandler: { success, error in
                if let error { continuation.resume(throwing: error) }
                else if success, let localIdentifier { continuation.resume(returning: localIdentifier) }
                else { continuation.resume(throwing: VideoCaptureError.saveFailed) }
            }
        }
    }

    private func movieWithFilmProfile(
        _ sourceURL: URL,
        recipe: NativeCaptureRecipe,
        videoStyleID: String
    ) async throws -> URL {
        guard let parameters = try NativeFilmVisualProfileCatalog.resolve(recipe: recipe, videoStyleID: videoStyleID) else {
            throw NativeFilmImageProcessorError.unsupportedProfileVersion
        }
        let asset = AVURLAsset(url: sourceURL)
        let context = CIContext(options: [.cacheIntermediates: false])
        let composition = AVVideoComposition(asset: asset, applyingCIFiltersWithHandler: { request in
            let filtered = NativeFilmImageProcessor.applyVisualProfile(to: request.sourceImage, parameters: parameters)
            request.finish(with: filtered, context: context)
        })
        guard let exporter = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetHighestQuality) else {
            throw VideoCaptureError.profileExportUnavailable
        }
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("theia-video-developed-\(UUID().uuidString).mov")
        exporter.outputURL = outputURL
        exporter.outputFileType = .mov
        exporter.videoComposition = composition
        exporter.shouldOptimizeForNetworkUse = false
        let exportReference = VideoExportSessionReference(exporter)
        do {
            try await withTaskCancellationHandler {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    exportReference.value.exportAsynchronously {
                        switch exportReference.value.status {
                        case .completed: continuation.resume()
                        case .cancelled: continuation.resume(throwing: CancellationError())
                        case .failed: continuation.resume(throwing: exportReference.value.error ?? VideoCaptureError.profileExportUnavailable)
                        default: continuation.resume(throwing: VideoCaptureError.profileExportUnavailable)
                        }
                    }
                }
            } onCancel: {
                exportReference.value.cancelExport()
            }
            return outputURL
        } catch {
            try? FileManager.default.removeItem(at: outputURL)
            throw error
        }
    }

    /// Attempts a passthrough QuickTime export that adds a compact recipe comment without transcoding.
    /// If the container or device cannot preserve metadata in passthrough, the original MOV is saved.
    private func movieWithRecipeMetadata(
        _ sourceURL: URL,
        recipe: NativeCaptureRecipe,
        videoStyleID: String
    ) async throws -> URL {
        let asset = AVURLAsset(url: sourceURL)
        guard let exporter = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetPassthrough) else {
            throw VideoCaptureError.metadataExportUnavailable
        }
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("theia-video-metadata-\(UUID().uuidString).mov")
        exporter.outputURL = outputURL
        exporter.outputFileType = .mov

        let payload: [String: Any] = [
            "version": 1,
            "id": recipe.stableID,
            "body": recipe.cameraBodyID,
            "lens": recipe.lensID,
            "film": recipe.filmStockID,
            "video_style": videoStyleID,
        ]
        let encoded = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
        let metadata = AVMutableMetadataItem()
        metadata.keySpace = .quickTimeMetadata
        metadata.key = AVMetadataKey.quickTimeMetadataKeyComment as NSString
        metadata.value = String(data: encoded, encoding: .utf8) as NSString?
        metadata.dataType = kCMMetadataBaseDataType_UTF8 as String
        exporter.metadata = [metadata]
        let exportReference = VideoExportSessionReference(exporter)

        do {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                exportReference.value.exportAsynchronously {
                    switch exportReference.value.status {
                    case .completed: continuation.resume()
                    case .failed: continuation.resume(throwing: exportReference.value.error ?? VideoCaptureError.metadataExportUnavailable)
                    case .cancelled: continuation.resume(throwing: VideoCaptureError.metadataExportUnavailable)
                    default: continuation.resume(throwing: VideoCaptureError.metadataExportUnavailable)
                    }
                }
            }
            return outputURL
        } catch {
            try? FileManager.default.removeItem(at: outputURL)
            throw error
        }
    }

    private func present(_ message: String, settings: Bool) {
        alertTitle = NSLocalizedString("video_title", comment: "Video capture alert title")
        alertMessage = message
        showSettingsAction = settings
        showAlert = true
    }

    private enum VideoCaptureError: LocalizedError {
        case photoPermissionDenied, saveFailed, metadataExportUnavailable, profileExportUnavailable
        var errorDescription: String? {
            switch self {
            case .photoPermissionDenied: NSLocalizedString("video_photo_permission_error", comment: "Photos permission error")
            case .saveFailed: NSLocalizedString("video_save_error", comment: "Video save error")
            case .metadataExportUnavailable: NSLocalizedString("video_metadata_export_fallback", comment: "Video metadata unavailable; original recording will be saved")
            case .profileExportUnavailable: NSLocalizedString("video_profile_export_fallback", comment: "Filtered video export failed; original recording will be saved")
            }
        }
    }
}

/// All mutable AVFoundation capture state is accessed only from `sessionQueue`.
private final class VideoSessionResources: @unchecked Sendable {
    let session = AVCaptureSession()
    let movieOutput = AVCaptureMovieFileOutput()
    let videoDataOutput = AVCaptureVideoDataOutput()
    var videoInput: AVCaptureDeviceInput?
    var audioInput: AVCaptureDeviceInput?
}

private final class VideoCaptureInputReference: @unchecked Sendable {
    let value: AVCaptureDeviceInput
    init(_ value: AVCaptureDeviceInput) { self.value = value }
}

private final class VideoExportSessionReference: @unchecked Sendable {
    let value: AVAssetExportSession
    init(_ value: AVAssetExportSession) { self.value = value }
}

private final class VideoFileRecordingDelegate: NSObject, AVCaptureFileOutputRecordingDelegate, @unchecked Sendable {
    private let completion: (URL?, Error?) -> Void
    init(completion: @escaping (URL?, Error?) -> Void) { self.completion = completion }

    func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL, from connections: [AVCaptureConnection], error: Error?) {
        if let error { completion(nil, error) }
        else { completion(outputFileURL, nil) }
    }
}

private struct VideoPreview: View {
    let image: CGImage?

    var body: some View {
        GeometryReader { geometry in
            Group {
                if let image {
                    Image(decorative: image, scale: 1)
                        .resizable()
                        .scaledToFill()
                } else {
                    Color.black
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
        }
        .background(.black)
    }
}
