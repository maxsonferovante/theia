import AVFoundation
import CoreImage
import Foundation
import QuartzCore

/// Produces a throttled, profile-matched preview from the video data output.
/// The recording output remains untouched and is later developed to a second Photos asset.
final class NativeFilmVideoFrameProcessor: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, @unchecked Sendable {
    @MainActor var onPreviewFrame: ((CGImage) -> Void)?

    private let context = CIContext(options: [.cacheIntermediates: false])
    private var parameters: NativeResolvedVisualParameters?
    private var lastPreviewTime: CFTimeInterval = 0
    private let minimumPreviewInterval: CFTimeInterval = 1.0 / 15.0

    func configure(parameters: NativeResolvedVisualParameters?) {
        self.parameters = parameters
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        let now = CACurrentMediaTime()
        guard now - lastPreviewTime >= minimumPreviewInterval,
              let parameters,
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastPreviewTime = now

        let input = CIImage(cvPixelBuffer: pixelBuffer)
        let outputImage = NativeFilmImageProcessor.applyVisualProfile(to: input, parameters: parameters)
        guard let frame = context.createCGImage(outputImage, from: outputImage.extent.integral) else { return }
        Task { @MainActor [weak self] in self?.onPreviewFrame?(frame) }
    }
}
