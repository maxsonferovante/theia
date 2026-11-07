import CoreImage
import Foundation
import ImageIO
import Photos
import TheiaShared
import UniformTypeIdentifiers

/// Value transfer type mirroring shared `ResolvedVisualParameters` (schema v1).
/// The caller supplies values resolved from the shared film/body/lens profile catalog.
struct NativeResolvedVisualParameters: Equatable, Sendable {
    let schemaVersion: Int
    let filmProfileID: String
    let filmProfileVersion: Int
    let filmStockID: String
    let cameraBodyID: String
    let lensID: String
    let exposureBiasEV: Double
    let contrast: Double
    let saturation: Double
    let warmth: Double
    let greenMagenta: Double
    let highlightRollOff: Double
    let shadowLift: Double
    let grainIntensity: Double
    let grainScale: Double
    let halation: Double
    let vignette: Double
    let bloom: Double
    let edgeSoftness: Double
    let distortion: Double
    let sharpness: Double

    init(
        schemaVersion: Int,
        filmProfileID: String,
        filmProfileVersion: Int,
        filmStockID: String,
        cameraBodyID: String,
        lensID: String,
        exposureBiasEV: Double,
        contrast: Double,
        saturation: Double,
        warmth: Double,
        greenMagenta: Double,
        highlightRollOff: Double,
        shadowLift: Double,
        grainIntensity: Double,
        grainScale: Double,
        halation: Double,
        vignette: Double,
        bloom: Double,
        edgeSoftness: Double,
        distortion: Double,
        sharpness: Double
    ) throws {
        guard schemaVersion == 1, filmProfileVersion > 0, !filmProfileID.isEmpty,
              !filmStockID.isEmpty, !cameraBodyID.isEmpty, !lensID.isEmpty else {
            throw NativeFilmImageProcessorError.unsupportedProfileVersion
        }
        guard (-1.5...1.5).contains(exposureBiasEV),
              (0.5...2.0).contains(contrast), (0.0...2.0).contains(saturation),
              (-1.0...1.0).contains(warmth), (-1.0...1.0).contains(greenMagenta),
              (0.0...1.0).contains(highlightRollOff), (0.0...0.45).contains(shadowLift),
              (0.0...1.0).contains(grainIntensity), (0.0...1.0).contains(grainScale),
              (0.0...1.0).contains(halation), (0.0...0.8).contains(vignette),
              (0.0...1.0).contains(bloom), (0.0...0.5).contains(edgeSoftness),
              (-0.15...0.15).contains(distortion), (0.5...1.5).contains(sharpness) else {
            throw NativeFilmImageProcessorError.invalidVisualParameters
        }
        self.schemaVersion = schemaVersion
        self.filmProfileID = filmProfileID
        self.filmProfileVersion = filmProfileVersion
        self.filmStockID = filmStockID
        self.cameraBodyID = cameraBodyID
        self.lensID = lensID
        self.exposureBiasEV = exposureBiasEV
        self.contrast = contrast
        self.saturation = saturation
        self.warmth = warmth
        self.greenMagenta = greenMagenta
        self.highlightRollOff = highlightRollOff
        self.shadowLift = shadowLift
        self.grainIntensity = grainIntensity
        self.grainScale = grainScale
        self.halation = halation
        self.vignette = vignette
        self.bloom = bloom
        self.edgeSoftness = edgeSoftness
        self.distortion = distortion
        self.sharpness = sharpness
    }
}

/// Adapts the shared profile resolver to the Core Image renderer's validated value type.
enum NativeFilmVisualProfileCatalog {
    static func resolve(
        recipe: NativeCaptureRecipe,
        videoStyleID: String = "equipment-recipe"
    ) throws -> NativeResolvedVisualParameters? {
        let sharedRecipeID = Self.sharedRecipeID(
            bodyID: recipe.cameraBodyID,
            lensID: recipe.lensID,
            filmStockID: recipe.filmStockID
        )
        let sharedRecipe = CaptureRecipe(
            bodyId: recipe.cameraBodyID,
            lensId: recipe.lensID,
            filmStockId: recipe.filmStockID,
            id: sharedRecipeID
        )
        guard let resolved = BuiltInVideoStyles.shared.resolve(recipe: sharedRecipe, styleId: videoStyleID) else {
            return nil
        }
        return try NativeResolvedVisualParameters(
            schemaVersion: Int(resolved.schemaVersion),
            filmProfileID: resolved.filmProfileId,
            filmProfileVersion: Int(resolved.filmProfileVersion),
            filmStockID: recipe.filmStockID,
            cameraBodyID: recipe.cameraBodyID,
            lensID: recipe.lensID,
            exposureBiasEV: resolved.exposureBiasEv,
            contrast: resolved.contrast,
            saturation: resolved.saturation,
            warmth: resolved.warmth,
            greenMagenta: resolved.greenMagenta,
            highlightRollOff: resolved.highlightRollOff,
            shadowLift: resolved.shadowLift,
            grainIntensity: resolved.grainIntensity,
            grainScale: resolved.grainScale,
            halation: resolved.halation,
            vignette: resolved.vignette,
            bloom: resolved.bloom,
            edgeSoftness: resolved.edgeSoftness,
            distortion: resolved.distortion,
            sharpness: resolved.sharpness
        )
    }

    /// Mirrors CaptureRecipeId.forEquipment; IDs in the built-in catalog are ASCII, and
    /// Kotlin String.length matches Swift UTF-16 code-unit length for arbitrary identifiers.
    private static func sharedRecipeID(bodyID: String, lensID: String, filmStockID: String) -> String {
        func encoded(_ value: String) -> String { "\(value.utf16.count):\(value)" }
        return "recipe-v1:\(encoded(bodyID))\(encoded(lensID))\(encoded(filmStockID))"
    }
}

struct NativeDevelopedMetadata: Codable, Equatable, Sendable {
    let version: Int
    let recipeID: String
    let cameraBodyID: String
    let lensID: String
    let filmStockID: String
    let filmProfileID: String
    let filmProfileVersion: Int
}

/// New JPEG bytes and their compact development record. `data` is safe to save as a new Photos asset.
struct DevelopedImageArtifact: Sendable {
    let data: Data
    let metadata: NativeDevelopedMetadata
}

enum NativeFilmImageProcessorError: Error {
    case photoNotFound
    case photoLibraryReadDenied
    case photoLibraryAddDenied
    case photoDataUnavailable
    case invalidVisualParameters
    case recipeProfileMismatch
    case unsupportedProfileVersion
    case renderingFailed
    case imageEncodingFailed
    case photosSaveFailed
}

/// Applies deterministic Core Image rendering to a Photos original without modifying it.
/// Core Image work runs on a detached utility task. Seeded film grain is stable for a given profile.
enum NativeFilmImageProcessor {
    static func developPhoto(
        assetLocalIdentifier: String,
        recipe: NativeCaptureRecipe,
        parameters: NativeResolvedVisualParameters
    ) async throws -> DevelopedImageArtifact {
        guard parameters.filmStockID == recipe.filmStockID,
              parameters.cameraBodyID == recipe.cameraBodyID,
              parameters.lensID == recipe.lensID else {
            throw NativeFilmImageProcessorError.recipeProfileMismatch
        }

        let data = try await Self.originalData(localIdentifier: assetLocalIdentifier)
        let bodyID = recipe.cameraBodyID
        let lensID = recipe.lensID
        let filmID = recipe.filmStockID
        let recipeID = recipe.stableID

        return try await Task.detached(priority: .userInitiated) {
            try renderOriginalData(
                data,
                recipeMetadata: NativeDevelopedMetadata(
                    version: 1,
                    recipeID: recipeID,
                    cameraBodyID: bodyID,
                    lensID: lensID,
                    filmStockID: filmID,
                    filmProfileID: parameters.filmProfileID,
                    filmProfileVersion: parameters.filmProfileVersion
                ),
                parameters: parameters
            )
        }.value
    }

    /// Creates a distinct Photos asset and returns its local identifier. The source asset is never changed or deleted.
    static func saveDevelopedCopy(_ artifact: DevelopedImageArtifact) async throws -> String {
        try await requestAddOnlyAuthorizationIfNeeded()
        let imageData = artifact.data
        let filename = "Theia-\(artifact.metadata.filmProfileID)-developed.jpg"

        return try await Task.detached(priority: .utility) {
            var createdIdentifier: String?
            do {
                try PHPhotoLibrary.shared().performChangesAndWait {
                    let request = PHAssetCreationRequest.forAsset()
                    let options = PHAssetResourceCreationOptions()
                    options.originalFilename = filename
                    request.addResource(with: .photo, data: imageData, options: options)
                    createdIdentifier = request.placeholderForCreatedAsset?.localIdentifier
                }
            } catch {
                throw NativeFilmImageProcessorError.photosSaveFailed
            }
            guard let createdIdentifier else { throw NativeFilmImageProcessorError.photosSaveFailed }
            return createdIdentifier
        }.value
    }

    private static func originalData(localIdentifier: String) async throws -> Data {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .authorized || status == .limited else {
            throw NativeFilmImageProcessorError.photoLibraryReadDenied
        }
        let result = PHAsset.fetchAssets(withLocalIdentifiers: [localIdentifier], options: nil)
        guard let asset = result.firstObject else { throw NativeFilmImageProcessorError.photoNotFound }
        let options = PHImageRequestOptions()
        options.version = .original
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .none
        // Development is fully on-device and must not trigger an iCloud download.
        options.isNetworkAccessAllowed = false

        return try await withCheckedThrowingContinuation { continuation in
            PHImageManager.default().requestImageDataAndOrientation(for: asset, options: options) { data, _, _, info in
                if let error = info?[PHImageErrorKey] as? Error {
                    continuation.resume(throwing: error)
                } else if (info?[PHImageCancelledKey] as? Bool) == true {
                    continuation.resume(throwing: CancellationError())
                } else if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: NativeFilmImageProcessorError.photoDataUnavailable)
                }
            }
        }
    }

    private static func requestAddOnlyAuthorizationIfNeeded() async throws {
        let current = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        if current == .authorized { return }
        guard current == .notDetermined else { throw NativeFilmImageProcessorError.photoLibraryAddDenied }
        let requested = await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { continuation.resume(returning: $0) }
        }
        guard requested == .authorized else { throw NativeFilmImageProcessorError.photoLibraryAddDenied }
    }

    private static func renderOriginalData(
        _ data: Data,
        recipeMetadata: NativeDevelopedMetadata,
        parameters: NativeResolvedVisualParameters
    ) throws -> DevelopedImageArtifact {
        guard var image = CIImage(data: data, options: [.applyOrientationProperty: true]) else {
            throw NativeFilmImageProcessorError.photoDataUnavailable
        }
        let outputExtent = image.extent.integral
        guard !outputExtent.isNull, !outputExtent.isInfinite, outputExtent.width > 0, outputExtent.height > 0 else {
            throw NativeFilmImageProcessorError.renderingFailed
        }
        image = applyVisualProfile(to: image, parameters: parameters)

        let context = CIContext(options: [.cacheIntermediates: false])
        guard let renderedImage = context.createCGImage(image, from: outputExtent) else {
            throw NativeFilmImageProcessorError.renderingFailed
        }
        let jpegData = try encodeJPEG(renderedImage, metadata: recipeMetadata)
        return DevelopedImageArtifact(data: jpegData, metadata: recipeMetadata)
    }

    /// Shared frame transform used by still development, the filtered live preview, and video export.
    static func applyVisualProfile(to source: CIImage, parameters: NativeResolvedVisualParameters) -> CIImage {
        let outputExtent = source.extent.integral
        var image = source
        image = applying(
            "CIExposureAdjust",
            to: image,
            values: [kCIInputEVKey: parameters.exposureBiasEV]
        )
        image = applying(
            "CIColorControls",
            to: image,
            values: [
                kCIInputContrastKey: parameters.contrast,
                kCIInputSaturationKey: parameters.saturation,
            ]
        )
        image = applying(
            "CITemperatureAndTint",
            to: image,
            values: [
                "inputNeutral": CIVector(x: 6_500, y: 0),
                "inputTargetNeutral": CIVector(
                    x: 6_500 + parameters.warmth * 1_100,
                    y: parameters.greenMagenta * 140
                ),
            ]
        )
        image = applying(
            "CIHighlightShadowAdjust",
            to: image,
            values: [
                "inputHighlightAmount": 1.0 - parameters.highlightRollOff * 0.65,
                "inputShadowAmount": parameters.shadowLift,
            ]
        )

        if parameters.distortion != 0 {
            image = applying(
                "CIBumpDistortion",
                to: image,
                values: [
                    "inputCenter": CIVector(x: outputExtent.midX, y: outputExtent.midY),
                    "inputRadius": min(outputExtent.width, outputExtent.height) * 0.8,
                    "inputScale": parameters.distortion,
                ]
            )
        }
        if parameters.edgeSoftness > 0 {
            image = applyingSoftEdgeBlend(image, extent: outputExtent, amount: parameters.edgeSoftness)
        }
        if parameters.sharpness != 1 {
            image = applying(
                "CISharpenLuminance",
                to: image,
                values: ["inputSharpness": (parameters.sharpness - 0.5) * 0.8]
            )
        }
        if parameters.halation > 0 {
            image = applying(
                "CIBloom",
                to: image,
                values: [
                    "inputRadius": 2 + parameters.halation * 7,
                    "inputIntensity": parameters.halation * 0.22,
                ]
            )
        }
        if parameters.bloom > 0 {
            image = applying(
                "CIBloom",
                to: image,
                values: [
                    "inputRadius": 5 + parameters.bloom * 22,
                    "inputIntensity": parameters.bloom * 0.18,
                ]
            )
        }
        if parameters.vignette > 0 {
            image = applying(
                "CIVignette",
                to: image,
                values: [
                    kCIInputIntensityKey: parameters.vignette,
                    kCIInputRadiusKey: min(outputExtent.width, outputExtent.height) * 0.78,
                ]
            )
        }
        if parameters.grainIntensity > 0 {
            image = applyingDeterministicGrain(
                image,
                extent: outputExtent,
                profileID: parameters.filmProfileID,
                scale: parameters.grainScale,
                intensity: parameters.grainIntensity
            )
        }

        return image.cropped(to: outputExtent)
    }

    private static func applying(_ name: String, to image: CIImage, values: [String: Any]) -> CIImage {
        guard let filter = CIFilter(name: name) else { return image }
        filter.setValue(image, forKey: kCIInputImageKey)
        values.forEach { filter.setValue($0.value, forKey: $0.key) }
        return filter.outputImage?.cropped(to: image.extent) ?? image
    }

    private static func applyingSoftEdgeBlend(_ image: CIImage, extent: CGRect, amount: Double) -> CIImage {
        guard let blur = CIFilter(name: "CIGaussianBlur") else { return image }
        blur.setValue(image, forKey: kCIInputImageKey)
        blur.setValue(0.5 + amount * 4.5, forKey: kCIInputRadiusKey)
        guard let blurred = blur.outputImage?.cropped(to: extent),
              let alpha = CIFilter(name: "CIColorMatrix"),
              let blend = CIFilter(name: "CISourceOverCompositing") else { return image }
        alpha.setValue(blurred, forKey: kCIInputImageKey)
        alpha.setValue(CIVector(x: 0, y: 0, z: 0, w: amount * 0.22), forKey: "inputAVector")
        guard let translucentBlur = alpha.outputImage else { return image }
        blend.setValue(translucentBlur, forKey: kCIInputImageKey)
        blend.setValue(image, forKey: kCIInputBackgroundImageKey)
        return blend.outputImage?.cropped(to: extent) ?? image
    }

    private static func applyingDeterministicGrain(
        _ image: CIImage,
        extent: CGRect,
        profileID: String,
        scale: Double,
        intensity: Double
    ) -> CIImage {
        let tileSide = 128
        var bytes = [UInt8](repeating: 0, count: tileSide * tileSide * 4)
        let seed = stableSeed(profileID)
        let blockSide = 1 + Int((1 - scale) * 3)
        let amplitude = 0.045 * intensity
        for y in 0..<tileSide {
            for x in 0..<tileSide {
                let sampleX = x / blockSide
                let sampleY = y / blockSide
                let sample = noise(seed: seed, x: sampleX, y: sampleY)
                let value = UInt8((0.5 + sample * amplitude).clamped(to: 0...1) * 255)
                let offset = (y * tileSide + x) * 4
                bytes[offset] = value
                bytes[offset + 1] = value
                bytes[offset + 2] = value
                bytes[offset + 3] = 255
            }
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData),
              let noiseImage = CGImage(
                width: tileSide,
                height: tileSide,
                bitsPerComponent: 8,
                bitsPerPixel: 32,
                bytesPerRow: tileSide * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider,
                decode: nil,
                shouldInterpolate: false,
                intent: .defaultIntent
              ),
              let tile = CIFilter(name: "CIAffineTile"),
              let softLight = CIFilter(name: "CISoftLightBlendMode") else { return image }
        tile.setValue(CIImage(cgImage: noiseImage), forKey: kCIInputImageKey)
        guard let tiled = tile.outputImage?.cropped(to: extent) else { return image }
        softLight.setValue(tiled, forKey: kCIInputImageKey)
        softLight.setValue(image, forKey: kCIInputBackgroundImageKey)
        return softLight.outputImage?.cropped(to: extent) ?? image
    }

    private static func stableSeed(_ string: String) -> UInt64 {
        string.utf8.reduce(UInt64(14_695_981_039_346_656_037)) { hash, byte in
            (hash ^ UInt64(byte)) &* 1_099_511_628_211
        }
    }

    private static func noise(seed: UInt64, x: Int, y: Int) -> Double {
        var value = seed ^ (UInt64(x) &* 0x9E37_79B9_7F4A_7C15) ^ (UInt64(y) &* 0xBF58_476D_1CE4_E5B9)
        value = (value ^ (value >> 30)) &* 0xBF58_476D_1CE4_E5B9
        value = (value ^ (value >> 27)) &* 0x94D0_49BB_1331_11EB
        value ^= value >> 31
        let unit = Double(value & 0xFFFF_FFFF) / Double(UInt32.max)
        return unit * 2 - 1
    }

    private static func encodeJPEG(_ image: CGImage, metadata: NativeDevelopedMetadata) throws -> Data {
        let destinationData = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            destinationData,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else { throw NativeFilmImageProcessorError.imageEncodingFailed }
        let encoderMetadata: [String: Any] = [
            "version": metadata.version,
            "recipe_id": metadata.recipeID,
            "body_id": metadata.cameraBodyID,
            "lens_id": metadata.lensID,
            "film_id": metadata.filmStockID,
            "film_profile_id": metadata.filmProfileID,
            "film_profile_version": metadata.filmProfileVersion,
            "filter_applied": true,
            "filter_applied_by": "theia",
        ]
        let encodedMetadata = try JSONSerialization.data(withJSONObject: encoderMetadata, options: [.sortedKeys])
        guard let metadataString = String(data: encodedMetadata, encoding: .utf8) else {
            throw NativeFilmImageProcessorError.imageEncodingFailed
        }
        let properties: [CFString: Any] = [
            kCGImagePropertyExifDictionary: [
                kCGImagePropertyExifUserComment: metadataString,
            ],
        ]
        CGImageDestinationAddImage(destination, image, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { throw NativeFilmImageProcessorError.imageEncodingFailed }
        return destinationData as Data
    }
}

private extension Double {
    func clamped(to range: ClosedRange<Double>) -> Double { min(max(self, range.lowerBound), range.upperBound) }
}
