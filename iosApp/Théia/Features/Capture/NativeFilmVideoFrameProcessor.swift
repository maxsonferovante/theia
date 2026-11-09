import AVFoundation
import CoreImage
import Foundation
import QuartzCore

/// Produces a throttled, profile-matched preview from the video data output.
/// The recording output remains untouched and is later developed to a second Photos asset.
final class NativeFilmVideoFrameProcessor: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, @unchecked Sendable {
    @MainActor var onPreviewFrame: ((CGImage?) -> Void)?
    @MainActor var onProcessingError: (() -> Void)?

    private let context = CIContext(options: [.cacheIntermediates: false])
    private let parametersLock = NSLock()
    private var parameters: NativeResolvedVisualParameters?
    private var hasReportedRenderFailure = false
    private var lastPreviewTime: CFTimeInterval = 0
    // Match the capture target. AVCaptureVideoDataOutput discards late frames,
    // so a slow renderer cannot build an unbounded queue.
    private let minimumPreviewInterval: CFTimeInterval = 1.0 / 30.0

    func configure(parameters: NativeResolvedVisualParameters?) {
        parametersLock.lock()
        defer { parametersLock.unlock() }
        self.parameters = parameters
        hasReportedRenderFailure = false
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        let now = CACurrentMediaTime()
        parametersLock.lock()
        let currentParameters = parameters
        parametersLock.unlock()
        guard now - lastPreviewTime >= minimumPreviewInterval,
              let parameters = currentParameters,
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastPreviewTime = now

        do {
            let input = CIImage(cvPixelBuffer: pixelBuffer)
            let outputImage = try NativeFilmImageProcessor.applyVisualProfile(to: input, parameters: parameters)
            guard let frame = context.createCGImage(outputImage, from: outputImage.extent.integral) else {
                throw NativeFilmImageProcessorError.renderingFailed
            }
            Task { @MainActor [weak self] in self?.onPreviewFrame?(frame) }
        } catch {
            parametersLock.lock()
            let shouldReport = !hasReportedRenderFailure
            hasReportedRenderFailure = true
            parametersLock.unlock()
            if shouldReport {
                Task { @MainActor [weak self] in
                    self?.onPreviewFrame?(nil)
                    self?.onProcessingError?()
                }
            }
        }
    }
}
