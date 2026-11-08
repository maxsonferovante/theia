import AVFoundation
import AudioToolbox
import CoreMotion
import Photos
import SwiftUI
import UIKit

/// Platform-facing recipe snapshot. The app can map the shared CaptureRecipe to this value.
struct NativeCaptureRecipe: Codable, Equatable, Hashable, Sendable {
    let cameraBodyID: String
    let lensID: String
    let filmStockID: String
    let lensProfileVersion: Int
    let renderingSchemaVersion: Int

    init(cameraBodyID: String, lensID: String, filmStockID: String, lensProfileVersion: Int = 1, renderingSchemaVersion: Int = 1) {
        self.cameraBodyID = cameraBodyID
        self.lensID = lensID
        self.filmStockID = filmStockID
        self.lensProfileVersion = lensProfileVersion
        self.renderingSchemaVersion = renderingSchemaVersion
    }

    private enum CodingKeys: String, CodingKey { case cameraBodyID, lensID, filmStockID, lensProfileVersion, renderingSchemaVersion }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        cameraBodyID = try values.decode(String.self, forKey: .cameraBodyID)
        lensID = try values.decode(String.self, forKey: .lensID)
        filmStockID = try values.decode(String.self, forKey: .filmStockID)
        lensProfileVersion = try values.decodeIfPresent(Int.self, forKey: .lensProfileVersion) ?? 1
        renderingSchemaVersion = try values.decodeIfPresent(Int.self, forKey: .renderingSchemaVersion) ?? 1
    }

    var stableID: String { "\(cameraBodyID)|\(lensID)|\(filmStockID)" }
}

/// A captured original saved to Photos, with the recipe used at the time of capture.
struct NativeCaptureRecord: Equatable {
    let localIdentifier: String
    let recipe: NativeCaptureRecipe
    let capturedAt: Date
    let geotagMetadata: LocalGeotagMetadata?
}

/// Native still capture screen. Camera access is requested when this screen appears;
/// photo-library add access is requested only after the user taps the shutter.
struct CameraCaptureView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage("geotagging_enabled") private var geotaggingEnabled = false
    @AppStorage("shutter_sound_enabled") private var shutterSoundEnabled = true
    let recipe: NativeCaptureRecipe
    @Binding var latestPhotoVisible: Bool
    var onCaptureSaved: (NativeCaptureRecord) -> Void = { _ in }

    @StateObject private var camera = CameraCaptureModel()
    @StateObject private var geotagLocation = GeotagLocationAdapter()
    @StateObject private var horizon = HorizonLevelMonitor()
    @State private var isResolvingGeotag = false
    @State private var isLatestPhotoPresented = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if camera.isAuthorized {
                CameraPreview(session: camera.session, image: camera.previewFrame, onFocus: camera.focus(at:))
                    .ignoresSafeArea()
                    .overlay(alignment: .top) {
                        if camera.showGrid {
                            ViewfinderGrid()
                                .stroke(.white.opacity(0.45), lineWidth: 0.7)
                                .ignoresSafeArea()
                                .accessibilityHidden(true)
                        }
                    }
                    .overlay(alignment: .center) {
                        if camera.showHorizonLevel, let roll = horizon.rollAngle {
                            HorizonLevelIndicator(rollAngle: roll)
                                .accessibilityLabel(String(format: NSLocalizedString("capture_horizon_value", comment: "Horizon level angle"), abs(roll * 180 / .pi)))
                                .accessibilityHidden(false)
                        }
                    }
                    .overlay(alignment: .topLeading) {
                        Text("\(recipe.cameraBodyID) · \(recipe.lensID) · \(recipe.filmStockID)")
                            .font(.caption.weight(.medium))
                            .lineLimit(1)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(.black.opacity(0.58), in: Capsule())
                            .padding(.top, 12)
                            .padding(.leading, 16)
                    }
                    .overlay(alignment: .bottom) { controls }
            } else {
                permissionState
            }

            if camera.isSaving {
                ProgressView(NSLocalizedString("capture_saving_original", comment: "Progress while saving a captured photo"))
                    .tint(.white)
                    .padding(20)
                    .background(.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 16))
                    .foregroundStyle(.white)
            }

            if isResolvingGeotag {
                ProgressView(NSLocalizedString("capture_geotag_in_progress", comment: "Progress while requesting optional location"))
                    .tint(.white)
                    .padding(18)
                    .background(.black.opacity(0.78), in: RoundedRectangle(cornerRadius: 16))
                    .foregroundStyle(.white)
            }
        }
        .preferredColorScheme(.dark)
        .task(id: recipe) { camera.prepareCameraPermissionState(recipe: recipe) }
        .modifier(HardwareCaptureEventModifier(
            isEnabled: camera.isAuthorized && camera.session.isRunning && !camera.isCapturing && !camera.isSaving && !isResolvingGeotag,
            action: captureFromShutter
        ))
        .onDisappear { camera.stopSession() }
        .onAppear { horizon.start() }
        .onDisappear { horizon.stop() }
        .onChange(of: camera.savedRecord) { _, record in
            if let record {
                latestPhotoVisible = false
                onCaptureSaved(record)
            }
        }
        .alert(NSLocalizedString("capture_camera_title", comment: "Capture alert title"), isPresented: $camera.showError) {
            Button(NSLocalizedString("capture_ok", comment: "Dismiss alert action"), role: .cancel) { }
        } message: {
            Text(camera.errorMessage)
        }
        .transaction { transaction in
            if reduceMotion { transaction.animation = nil }
        }
        .sheet(isPresented: $isLatestPhotoPresented) {
            LatestPhotoSheet()
        }
    }

    private var controls: some View {
        VStack(spacing: 18) {
            HStack(spacing: 6) {
                Button {
                    camera.toggleGrid()
                } label: {
                    Image(systemName: "grid")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 48)
                        .background(.black.opacity(0.52), in: Circle())
                }
                .accessibilityLabel(NSLocalizedString(camera.showGrid ? "capture_hide_grid" : "capture_show_grid", comment: "Grid control accessibility label"))

                Button {
                    camera.toggleHorizonLevel()
                } label: {
                    Image(systemName: "level")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 48)
                        .background(.black.opacity(0.52), in: Circle())
                }
                .accessibilityLabel(NSLocalizedString("capture_horizon_toggle", comment: "Horizon level toggle"))
                .accessibilityValue(NSLocalizedString(camera.showHorizonLevel ? "capture_state_on" : "capture_state_off", comment: "Horizon level state"))

                Spacer()

                Button {
                    camera.cycleFlashMode()
                } label: {
                    Image(systemName: camera.flashMode.symbol)
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 48)
                        .background(.black.opacity(0.52), in: Circle())
                }
                .accessibilityLabel(String(format: NSLocalizedString("capture_flash_format", comment: "Flash mode accessibility label"), NSLocalizedString(camera.flashMode.localizationKey, comment: "Flash mode")))

                Button {
                    camera.switchCamera()
                } label: {
                    Image(systemName: "camera.rotate")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 48)
                        .background(.black.opacity(0.52), in: Circle())
                }
                .accessibilityLabel(NSLocalizedString("capture_switch_camera", comment: "Switch camera accessibility label"))

                if latestPhotoVisible {
                    Button { isLatestPhotoPresented = true } label: {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 18, weight: .medium))
                            .frame(width: 44, height: 48)
                            .background(.black.opacity(0.52), in: Circle())
                    }
                    .accessibilityLabel(NSLocalizedString("capture_latest_photo", comment: "Open latest photo accessibility label"))
                }
            }

            HStack(spacing: 18) {
                Text(String(format: "%.1f×", camera.zoomFactor))
                    .font(.caption.monospacedDigit())
                    .frame(width: 40)
                Slider(value: $camera.zoomFactor, in: camera.minimumZoom...camera.maximumZoom) { editing in
                    if !editing { camera.applyZoom() }
                }
                .accessibilityLabel(NSLocalizedString("capture_zoom", comment: "Zoom control accessibility label"))
            }

            HStack(spacing: 12) {
                Button {
                    camera.cycleTimer()
                } label: {
                    Label(camera.timerLocalizationKey, systemImage: "timer")
                        .font(.caption.weight(.semibold))
                        .frame(minWidth: 78, minHeight: 48)
                        .background(.black.opacity(0.52), in: Capsule())
                }
                .accessibilityLabel(NSLocalizedString("capture_timer", comment: "Capture timer control"))
                .accessibilityValue(NSLocalizedString(camera.timerLocalizationKey, comment: "Current capture timer"))

                if camera.supportsFocusExposureLock {
                    Button {
                        camera.toggleFocusExposureLock()
                    } label: {
                        Label(
                            NSLocalizedString(camera.isFocusExposureLocked ? "capture_locked" : "capture_lock", comment: "Focus and exposure lock state"),
                            systemImage: camera.isFocusExposureLocked ? "lock.fill" : "lock.open"
                        )
                        .font(.caption.weight(.semibold))
                        .frame(minWidth: 88, minHeight: 48)
                        .background(.black.opacity(0.52), in: Capsule())
                    }
                    .accessibilityLabel(NSLocalizedString("capture_focus_exposure_lock", comment: "Focus and exposure lock control"))
                    .accessibilityValue(NSLocalizedString(camera.isFocusExposureLocked ? "capture_locked" : "capture_unlocked", comment: "Focus and exposure lock state"))
                }

                Text(camera.photoDimensionsLabel)
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(.white.opacity(0.86))
                    .accessibilityLabel(NSLocalizedString("capture_photo_resolution", comment: "Maximum photo resolution"))
                    .accessibilityValue(camera.photoDimensionsLabel)
            }

            HStack(spacing: 12) {
                Text(NSLocalizedString("capture_exposure", comment: "Exposure compensation slider"))
                    .font(.caption)
                    .frame(width: 72, alignment: .leading)
                Slider(value: $camera.exposureBias, in: camera.minimumExposureBias...camera.maximumExposureBias) { editing in
                    if !editing { camera.applyExposureBias() }
                }
                .disabled(!camera.supportsExposureCompensation)
                .accessibilityLabel(NSLocalizedString("capture_exposure", comment: "Exposure compensation slider"))
                .accessibilityValue(String(format: NSLocalizedString("capture_exposure_value", comment: "Exposure compensation value"), camera.exposureBias))
            }

            Button {
                if camera.countdownSeconds != nil {
                    camera.cancelCountdown()
                    return
                }
                captureFromShutter()
            } label: {
                ZStack {
                    Circle().stroke(.white, lineWidth: 4).frame(width: 74, height: 74)
                    if let countdown = camera.countdownSeconds {
                        Text("\(countdown)")
                            .font(.title2.bold().monospacedDigit())
                            .foregroundStyle(.white)
                    } else {
                        Circle().fill(.white).frame(width: 60, height: 60)
                    }
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
            }
            .disabled(camera.isSaving || (camera.isCapturing && camera.countdownSeconds == nil) || isResolvingGeotag)
            .accessibilityLabel(NSLocalizedString(camera.countdownSeconds == nil ? "capture_take_photo" : "capture_cancel_timer", comment: "Photo shutter accessibility label"))
        }
        .padding(.horizontal, 28)
        .padding(.top, 20)
        .padding(.bottom, 32)
        .background(LinearGradient(colors: [.clear, .black.opacity(0.72)], startPoint: .top, endPoint: .bottom))
        .foregroundStyle(.white)
    }

    private func captureFromShutter() {
        guard camera.isAuthorized, !camera.isCapturing, !camera.isSaving, !isResolvingGeotag else { return }
        Task {
            isResolvingGeotag = geotaggingEnabled
            let metadata = geotaggingEnabled
                ? await geotagLocation.requestOneShotLocationAfterOptIn()
                : nil
            isResolvingGeotag = false
            await camera.capture(
                recipe: recipe,
                geotagMetadata: metadata,
                timerSeconds: camera.timerSeconds,
                playShutterSound: shutterSoundEnabled
            )
        }
    }

    @ViewBuilder
    private var permissionState: some View {
        VStack(spacing: 16) {
            Image(systemName: camera.permissionDenied ? "camera.fill" : "camera")
                .font(.system(size: 38))
                .foregroundStyle(Color(red: 0.92, green: 0.68, blue: 0.21))
            Text(NSLocalizedString(camera.permissionDenied ? "capture_camera_access_off" : camera.shouldExplainCameraPermission ? "capture_camera_permission_heading" : "capture_preparing_camera", comment: "Camera permission or loading title"))
                .font(.title3.weight(.semibold))
            Text(NSLocalizedString(camera.permissionDenied
                                   ? "capture_camera_permission_denied"
                                   : "capture_camera_permission_rationale", comment: "Camera permission explanation"))
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.75))
            if camera.permissionDenied {
                Button(NSLocalizedString("capture_open_settings", comment: "Open system settings action")) { camera.openSettings() }
                    .buttonStyle(.borderedProminent)
            } else if camera.shouldExplainCameraPermission {
                Button(NSLocalizedString("capture_continue", comment: "Continue to camera permission prompt")) {
                    Task { await camera.requestCameraPermission() }
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(32)
        .foregroundStyle(.white)
    }
}

private struct HardwareCaptureEventModifier: ViewModifier {
    let isEnabled: Bool
    let action: () -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.onCameraCaptureEvent(isEnabled: isEnabled) { event in
                if event.phase == .ended { action() }
            }
        } else {
            content
        }
    }
}

@MainActor
private final class CameraCaptureModel: ObservableObject {
    private enum TimerMode: Int, CaseIterable {
        case off = 0
        case threeSeconds = 3
        case tenSeconds = 10

        var localizationKey: String {
            switch self {
            case .off: "capture_timer_off"
            case .threeSeconds: "capture_timer_3s"
            case .tenSeconds: "capture_timer_10s"
            }
        }
    }

    enum FlashMode: CaseIterable {
        case off, auto, on

        var photoMode: AVCaptureDevice.FlashMode {
            switch self {
            case .off: .off
            case .auto: .auto
            case .on: .on
            }
        }

        var symbol: String {
            switch self {
            case .off: "bolt.slash"
            case .auto: "bolt.badge.automatic"
            case .on: "bolt.fill"
            }
        }

        var accessibilityName: String {
            switch self {
            case .off: "off"
            case .auto: "automatic"
            case .on: "on"
            }
        }

        var localizationKey: String {
            switch self {
            case .off: "capture_flash_off"
            case .auto: "capture_flash_auto"
            case .on: "capture_flash_on"
            }
        }
    }

    private let engine = CameraCaptureEngine()
    var session: AVCaptureSession { engine.session }
    @Published private(set) var isAuthorized = false
    @Published private(set) var permissionDenied = false
    @Published private(set) var shouldExplainCameraPermission = false
    @Published private(set) var isCapturing = false
    @Published private(set) var isSaving = false
    @Published var showGrid = false
    @Published var showHorizonLevel = true
    @Published var zoomFactor: Double = 1
    @Published private(set) var minimumZoom: Double = 1
    @Published private(set) var maximumZoom: Double = 1
    @Published private(set) var flashMode: FlashMode = .off
    @Published var exposureBias: Double = 0
    @Published private(set) var minimumExposureBias: Double = 0
    @Published private(set) var maximumExposureBias: Double = 0
    @Published private(set) var supportsExposureCompensation = false
    @Published private(set) var supportsFocusExposureLock = false
    @Published private(set) var isFocusExposureLocked = false
    @Published private(set) var timerSeconds = 0
    @Published private(set) var countdownSeconds: Int?
    @Published private(set) var photoDimensionsLabel = "—"
    @Published var showError = false
    @Published private(set) var errorMessage = ""
    @Published private(set) var savedRecord: NativeCaptureRecord?
    @Published private(set) var previewFrame: CGImage?

    private let sessionQueue = DispatchQueue(label: "app.theia.camera.session")
    private let frameProcessor = NativeFilmVideoFrameProcessor()
    private var pendingRecipe: NativeCaptureRecipe?
    private var photoDelegate: PhotoCaptureDelegate?
    private var countdownCancelled = false

    var permissionDeniedForUI: Bool { permissionDenied }
    var timerLocalizationKey: String { TimerMode(rawValue: timerSeconds)?.localizationKey ?? "capture_timer_off" }

    init() {
        frameProcessor.onPreviewFrame = { [weak self] image in self?.previewFrame = image }
        frameProcessor.onProcessingError = { [weak self] in
            self?.previewFrame = nil
            self?.present(NativeFilmImageProcessorError.renderingFailed)
        }
    }

    func prepareCameraPermissionState(recipe: NativeCaptureRecipe) {
        do {
            guard let parameters = try NativeFilmVisualProfileCatalog.resolve(recipe: recipe) else {
                throw NativeFilmImageProcessorError.unsupportedProfileVersion
            }
            frameProcessor.configure(parameters: parameters)
        } catch {
            present(error)
            return
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndStart()
        case .notDetermined:
            shouldExplainCameraPermission = true
        case .denied, .restricted:
            permissionDenied = true
        @unknown default:
            permissionDenied = true
        }
    }

    func requestCameraPermission() async {
        shouldExplainCameraPermission = false
        let allowed = await AVCaptureDevice.requestAccess(for: .video)
        permissionDenied = !allowed
        if allowed { configureAndStart() }
    }

    func toggleGrid() { showGrid.toggle() }
    func toggleHorizonLevel() { showHorizonLevel.toggle() }

    func cycleTimer() {
        let modes = TimerMode.allCases
        let current = modes.firstIndex(where: { $0.rawValue == timerSeconds }) ?? 0
        timerSeconds = modes[(current + 1) % modes.count].rawValue
    }

    func cancelCountdown() { countdownCancelled = true }

    func stopSession() {
        let engine = engine
        sessionQueue.async { [weak self, engine] in
            guard engine.session.isRunning else { return }
            engine.session.stopRunning()
            Task { @MainActor in self?.isAuthorized = false }
        }
    }

    func cycleFlashMode() {
        let modes = FlashMode.allCases
        guard let index = modes.firstIndex(of: flashMode) else { return }
        flashMode = modes[(index + 1) % modes.count]
    }

    func switchCamera() {
        let engine = engine
        sessionQueue.async { [weak self, engine] in
            let nextPosition: AVCaptureDevice.Position = engine.currentInput?.device.position == .back ? .front : .back
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: nextPosition),
                  let newInput = try? AVCaptureDeviceInput(device: device) else { return }
            engine.session.beginConfiguration()
            if let currentInput = engine.currentInput { engine.session.removeInput(currentInput) }
            if engine.session.canAddInput(newInput) {
                engine.session.addInput(newInput)
                engine.currentInput = newInput
                engine.activeDevice = device
                if let connection = engine.videoDataOutput.connection(with: .video), connection.isVideoMirroringSupported {
                    connection.automaticallyAdjustsVideoMirroring = false
                    connection.isVideoMirrored = nextPosition == .front
                }
                self?.updateZoomRange(device)
                self?.updateCaptureCapabilities(device, photoOutput: engine.photoOutput)
            } else if let currentInput = engine.currentInput, engine.session.canAddInput(currentInput) {
                engine.session.addInput(currentInput)
            }
            engine.session.commitConfiguration()
        }
    }

    func focus(at point: CGPoint) {
        let engine = engine
        sessionQueue.async { [weak self, engine] in
            guard let device = engine.activeDevice else { return }
            do {
                try device.lockForConfiguration()
                if device.isFocusPointOfInterestSupported {
                    device.focusPointOfInterest = point
                    device.focusMode = device.isFocusModeSupported(.autoFocus) ? .autoFocus : .continuousAutoFocus
                }
                if device.isExposurePointOfInterestSupported {
                    device.exposurePointOfInterest = point
                    device.exposureMode = device.isExposureModeSupported(.continuousAutoExposure) ? .continuousAutoExposure : .autoExpose
                }
                device.unlockForConfiguration()
            } catch { Task { @MainActor in self?.present(error) } }
        }
    }

    func applyZoom() {
        let factor = zoomFactor
        let engine = engine
        sessionQueue.async { [weak self, engine] in
            guard let device = engine.activeDevice else { return }
            do {
                try device.lockForConfiguration()
                device.videoZoomFactor = min(max(factor, 1), device.activeFormat.videoMaxZoomFactor)
                device.unlockForConfiguration()
            } catch { Task { @MainActor in self?.present(error) } }
        }
    }

    func applyExposureBias() {
        let requested = Float(exposureBias)
        let engine = engine
        sessionQueue.async { [weak self, engine] in
            guard let device = engine.activeDevice else { return }
            do {
                try device.lockForConfiguration()
                let clamped = min(max(requested, device.minExposureTargetBias), device.maxExposureTargetBias)
                device.setExposureTargetBias(clamped, completionHandler: nil)
                device.unlockForConfiguration()
            } catch { Task { @MainActor in self?.present(error) } }
        }
    }

    func toggleFocusExposureLock() {
        let engine = engine
        let isLocked = isFocusExposureLocked
        sessionQueue.async { [weak self, engine] in
            guard let device = engine.activeDevice else { return }
            do {
                try device.lockForConfiguration()
                if isLocked {
                    if device.isFocusModeSupported(.continuousAutoFocus) { device.focusMode = .continuousAutoFocus }
                    else if device.isFocusModeSupported(.autoFocus) { device.focusMode = .autoFocus }
                    if device.isExposureModeSupported(.continuousAutoExposure) { device.exposureMode = .continuousAutoExposure }
                    else if device.isExposureModeSupported(.autoExpose) { device.exposureMode = .autoExpose }
                    Task { @MainActor in self?.isFocusExposureLocked = false }
                } else {
                    var lockedSomething = false
                    if device.isFocusModeSupported(.locked) {
                        device.setFocusModeLocked(lensPosition: device.lensPosition, completionHandler: nil)
                        lockedSomething = true
                    }
                    if device.isExposureModeSupported(.locked) {
                        device.exposureMode = .locked
                        lockedSomething = true
                    }
                    Task { @MainActor in self?.isFocusExposureLocked = lockedSomething }
                }
                device.unlockForConfiguration()
            } catch { Task { @MainActor in self?.present(error) } }
        }
    }

    func capture(
        recipe: NativeCaptureRecipe,
        geotagMetadata: LocalGeotagMetadata? = nil,
        timerSeconds: Int = 0,
        playShutterSound: Bool = true
    ) async {
        guard !isCapturing, !isSaving else { return }
        isCapturing = true
        countdownCancelled = false
        do {
            try await requestPhotoLibraryAccess()
            guard PHPhotoLibrary.authorizationStatus(for: .addOnly) == .authorized || PHPhotoLibrary.authorizationStatus(for: .addOnly) == .limited else {
                throw CameraError.photoPermissionDenied
            }
            if timerSeconds > 0 {
                for second in stride(from: timerSeconds, through: 1, by: -1) {
                    countdownSeconds = second
                    try? await Task.sleep(for: .seconds(1))
                    if countdownCancelled {
                        countdownSeconds = nil
                        isCapturing = false
                        return
                    }
                }
                countdownSeconds = nil
            }
            pendingRecipe = recipe
            let delegate = PhotoCaptureDelegate { [weak self] result in
                Task { @MainActor in
                    guard let self else { return }
                    self.isCapturing = false
                    switch result {
                    case .success(let data):
                        let metadataData = embeddingRecipeMetadata(data, recipe: recipe, geotagMetadata: geotagMetadata)
                        await self.savePhoto(metadataData, recipe: recipe, geotagMetadata: geotagMetadata)
                    case .failure(let error): self.present(error)
                    }
                    self.photoDelegate = nil
                }
            }
            photoDelegate = delegate
            let engine = engine
            let requestedFlashMode = flashMode.photoMode
            sessionQueue.async { [weak self, engine, delegate] in
                guard engine.session.isRunning else {
                    Task { @MainActor in
                        self?.isCapturing = false
                        self?.present(CameraError.sessionUnavailable)
                    }
                    return
                }
                let settings = AVCapturePhotoSettings()
                settings.flashMode = engine.activeDevice?.hasFlash == true ? requestedFlashMode : .off
                settings.maxPhotoDimensions = engine.photoOutput.maxPhotoDimensions
                // The system shutter sound uses the device's current silent/volume behavior; no audio session is changed.
                if playShutterSound { AudioServicesPlaySystemSound(1108) }
                engine.photoOutput.capturePhoto(with: settings, delegate: delegate)
            }
        } catch {
            countdownSeconds = nil
            isCapturing = false
            present(error)
        }
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func configureAndStart() {
        let engine = engine
        let frameProcessor = frameProcessor
        let sessionQueue = sessionQueue
        sessionQueue.async { [weak self, engine] in
            guard !engine.session.isRunning else {
                Task { @MainActor in self?.isAuthorized = true }
                return
            }
            if engine.currentInput != nil {
                engine.session.startRunning()
                Task { @MainActor in self?.isAuthorized = true }
                return
            }
            engine.session.beginConfiguration()
            engine.session.sessionPreset = .photo
            engine.videoDataOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)]
            engine.videoDataOutput.alwaysDiscardsLateVideoFrames = true
            engine.videoDataOutput.setSampleBufferDelegate(frameProcessor, queue: sessionQueue)
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device),
                  engine.session.canAddInput(input), engine.session.canAddOutput(engine.photoOutput),
                  engine.session.canAddOutput(engine.videoDataOutput) else {
                engine.session.commitConfiguration()
                Task { @MainActor in self?.present(CameraError.cameraUnavailable) }
                return
            }
            engine.session.addInput(input)
            engine.session.addOutput(engine.photoOutput)
            engine.session.addOutput(engine.videoDataOutput)
            if let connection = engine.videoDataOutput.connection(with: .video), connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            engine.currentInput = input
            engine.activeDevice = device
            self?.updateZoomRange(device)
            self?.updateCaptureCapabilities(device, photoOutput: engine.photoOutput)
            engine.session.commitConfiguration()
            engine.session.startRunning()
            Task { @MainActor in self?.isAuthorized = true }
        }
    }

    nonisolated private func updateZoomRange(_ device: AVCaptureDevice) {
        let maxZoom = min(Double(device.activeFormat.videoMaxZoomFactor), 10)
        Task { @MainActor in
            minimumZoom = 1
            maximumZoom = max(1, maxZoom)
            zoomFactor = min(max(zoomFactor, minimumZoom), maximumZoom)
        }
    }

    nonisolated private func updateCaptureCapabilities(_ device: AVCaptureDevice, photoOutput: AVCapturePhotoOutput) {
        let dimensions = device.activeFormat.supportedMaxPhotoDimensions.max {
            Int($0.width) * Int($0.height) < Int($1.width) * Int($1.height)
        }
        if let dimensions {
            photoOutput.maxPhotoDimensions = dimensions
            Task { @MainActor in photoDimensionsLabel = "\(dimensions.width)×\(dimensions.height)" }
        } else {
            Task { @MainActor in photoDimensionsLabel = "—" }
        }

        let minimumBias = device.minExposureTargetBias
        let maximumBias = device.maxExposureTargetBias
        let canLock = device.isFocusModeSupported(.locked) || device.isExposureModeSupported(.locked)
        Task { @MainActor in
            supportsExposureCompensation = maximumBias > minimumBias
            minimumExposureBias = Double(minimumBias)
            maximumExposureBias = Double(maximumBias)
            exposureBias = min(max(exposureBias, minimumExposureBias), maximumExposureBias)
            supportsFocusExposureLock = canLock
            isFocusExposureLocked = false
        }
    }

    private func requestPhotoLibraryAccess() async throws {
        let status = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        if status == .authorized || status == .limited { return }
        if status == .notDetermined {
            let newStatus = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
            if newStatus == .authorized || newStatus == .limited { return }
        }
        throw CameraError.photoPermissionDenied
    }

    private func savePhoto(_ data: Data, recipe: NativeCaptureRecipe, geotagMetadata: LocalGeotagMetadata?) async {
        isSaving = true
        defer { isSaving = false }
        do {
            let identifier = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
                var placeholderIdentifier: String?
                PHPhotoLibrary.shared().performChanges {
                    let request = PHAssetCreationRequest.forAsset()
                    request.addResource(with: .photo, data: data, options: nil)
                    placeholderIdentifier = request.placeholderForCreatedAsset?.localIdentifier
                } completionHandler: { success, error in
                    if let error { continuation.resume(throwing: error) }
                    else if success, let placeholderIdentifier { continuation.resume(returning: placeholderIdentifier) }
                    else { continuation.resume(throwing: CameraError.saveFailed) }
                }
            }
            savedRecord = NativeCaptureRecord(
                localIdentifier: identifier,
                recipe: recipe,
                capturedAt: Date(),
                geotagMetadata: geotagMetadata
            )
        } catch { present(error) }
    }

    private func present(_ error: Error) {
        errorMessage = error.localizedDescription
        showError = true
    }

    private enum CameraError: LocalizedError {
        case cameraUnavailable, sessionUnavailable, photoPermissionDenied, saveFailed

        var errorDescription: String? {
            switch self {
            case .cameraUnavailable: NSLocalizedString("capture_error_camera_unavailable", comment: "Camera unavailable error")
            case .sessionUnavailable: NSLocalizedString("capture_error_session_unavailable", comment: "Camera session error")
            case .photoPermissionDenied: NSLocalizedString("capture_error_photo_permission", comment: "Photo library permission error")
            case .saveFailed: NSLocalizedString("capture_error_photo_save", comment: "Photo save error")
            }
        }
    }
}

/// AVFoundation session configuration and device selection are accessed on the capture session queue.
/// The session is also handed to AVCaptureVideoPreviewLayer for presentation; AVCaptureSession owns
/// synchronization for that read-only preview connection.
private final class CameraCaptureEngine: @unchecked Sendable {
    let session = AVCaptureSession()
    let photoOutput = AVCapturePhotoOutput()
    let videoDataOutput = AVCaptureVideoDataOutput()
    var activeDevice: AVCaptureDevice?
    var currentInput: AVCaptureDeviceInput?
}

private final class PhotoCaptureDelegate: NSObject, AVCapturePhotoCaptureDelegate, @unchecked Sendable {
    private let completion: (Result<Data, Error>) -> Void

    init(completion: @escaping (Result<Data, Error>) -> Void) { self.completion = completion }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        if let error { completion(.failure(error)); return }
        guard let data = photo.fileDataRepresentation() else {
            completion(.failure(CaptureDataError.missingRepresentation))
            return
        }
        completion(.success(data))
    }

    private enum CaptureDataError: LocalizedError {
        case missingRepresentation
        var errorDescription: String? { NSLocalizedString("capture_error_photo_prepare", comment: "Photo processing error") }
    }
}

fileprivate struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    let image: CGImage?
    let onFocus: (CGPoint) -> Void

    fileprivate func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.previewLayer.videoGravity = .resizeAspectFill
        view.previewLayer.session = session
        view.filteredImage.image = image.map { UIImage(cgImage: $0) }
        view.onFocus = onFocus
        return view
    }

    fileprivate func updateUIView(_ view: PreviewUIView, context: Context) {
        view.previewLayer.session = session
        view.filteredImage.image = image.map { UIImage(cgImage: $0) }
        view.onFocus = onFocus
    }

    fileprivate final class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
        var onFocus: ((CGPoint) -> Void)?
        let filteredImage = UIImageView()

        override init(frame: CGRect) {
            super.init(frame: frame)
            filteredImage.contentMode = .scaleAspectFill
            filteredImage.clipsToBounds = true
            filteredImage.isUserInteractionEnabled = false
            addSubview(filteredImage)
            let tap = UITapGestureRecognizer(target: self, action: #selector(focus(_:)))
            addGestureRecognizer(tap)
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        override func layoutSubviews() {
            super.layoutSubviews()
            filteredImage.frame = bounds
        }

        @objc private func focus(_ gesture: UITapGestureRecognizer) {
            let point = gesture.location(in: self)
            onFocus?(previewLayer.captureDevicePointConverted(fromLayerPoint: point))
        }
    }
}

private struct ViewfinderGrid: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        for fraction in [1.0 / 3.0, 2.0 / 3.0] {
            path.move(to: CGPoint(x: rect.width * fraction, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.width * fraction, y: rect.maxY))
            path.move(to: CGPoint(x: rect.minX, y: rect.height * fraction))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.height * fraction))
        }
        return path
    }
}

@MainActor
private final class HorizonLevelMonitor: ObservableObject {
    @Published private(set) var rollAngle: Double?
    private let motion = CMMotionManager()

    func start() {
        guard motion.isDeviceMotionAvailable, !motion.isDeviceMotionActive else { return }
        motion.deviceMotionUpdateInterval = 1.0 / 20.0
        motion.startDeviceMotionUpdates(to: .main) { [weak self] data, _ in
            guard let angle = data?.attitude.roll else { return }
            Task { @MainActor in self?.rollAngle = angle }
        }
    }

    func stop() {
        motion.stopDeviceMotionUpdates()
        rollAngle = nil
    }
}

private struct HorizonLevelIndicator: View {
    let rollAngle: Double

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Capsule()
                    .fill(.white.opacity(0.9))
                    .frame(width: min(geometry.size.width * 0.5, 190), height: 2)
                    .rotationEffect(.radians(-rollAngle))
                Circle()
                    .stroke(.white, lineWidth: 1.5)
                    .frame(width: 14, height: 14)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(height: 48)
        .accessibilityElement()
        .accessibilityLabel(String(format: NSLocalizedString("capture_horizon_value", comment: "Horizon level angle"), abs(rollAngle * 180 / .pi)))
    }
}

private struct LatestPhotoSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var image: UIImage?
    @State private var isLoading = true
    @State private var message = NSLocalizedString("capture_latest_loading", comment: "Loading latest photo")

    var body: some View {
        NavigationStack {
            Group {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding()
                } else if isLoading {
                    ProgressView(message)
                } else {
                    ContentUnavailableView("capture_latest_unavailable", systemImage: "photo", description: Text(message))
                }
            }
            .navigationTitle("capture_latest_title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("capture_done") { dismiss() }
                        .frame(minWidth: 44, minHeight: 44)
                }
            }
            .task { await loadLatestPhoto() }
        }
    }

    @MainActor
    private func loadLatestPhoto() async {
        var status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if status == .notDetermined {
            status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        }
        guard status == .authorized || status == .limited else {
            message = NSLocalizedString("capture_latest_permission_denied", comment: "Latest photo permission required")
            isLoading = false
            return
        }

        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        guard let asset = PHAsset.fetchAssets(with: .image, options: options).firstObject else {
            message = NSLocalizedString("capture_latest_empty", comment: "No latest photo exists")
            isLoading = false
            return
        }
        let request = PHImageRequestOptions()
        request.deliveryMode = .highQualityFormat
        request.isNetworkAccessAllowed = false
        PHImageManager.default().requestImage(
            for: asset,
            targetSize: CGSize(width: max(1600, asset.pixelWidth), height: max(1600, asset.pixelHeight)),
            contentMode: .aspectFit,
            options: request
        ) { result, _ in
            Task { @MainActor in
                image = result
                message = result == nil
                    ? NSLocalizedString("capture_latest_unavailable", comment: "Latest photo unavailable")
                    : ""
                isLoading = false
            }
        }
    }
}
