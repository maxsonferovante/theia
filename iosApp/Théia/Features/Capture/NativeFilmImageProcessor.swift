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
    let lensProfileVersion: Int
    let radialSoftness: Double
    let chromaticAberration: Double
    let opticalFlare: Double

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
        sharpness: Double,
        lensProfileVersion: Int = 1,
        radialSoftness: Double = 0,
        chromaticAberration: Double = 0,
        opticalFlare: Double = 0
    ) throws {
        guard schemaVersion > 0, filmProfileVersion > 0, !filmProfileID.isEmpty,
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
              (-0.15...0.15).contains(distortion), (0.5...1.5).contains(sharpness),
              lensProfileVersion > 0, (0...1).contains(radialSoftness),
              (0...0.01).contains(chromaticAberration), (0...1).contains(opticalFlare) else {
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
        self.lensProfileVersion = lensProfileVersion
        self.radialSoftness = radialSoftness
        self.chromaticAberration = chromaticAberration
        self.opticalFlare = opticalFlare
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
            id: sharedRecipeID,
            lensProfileVersion: Int32(recipe.lensProfileVersion),
            renderingSchemaVersion: Int32(recipe.renderingSchemaVersion)
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
            sharpness: resolved.sharpness,
            lensProfileVersion: Int(resolved.lensProfileVersion),
            radialSoftness: resolved.radialSoftness,
            chromaticAberration: resolved.chromaticAberration,
            opticalFlare: resolved.opticalFlare
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
    let lensProfileVersion: Int
    let renderingAlgorithmID: String
    let renderingSchemaVersion: Int
}

/// New JPEG bytes and their compact development record. `data` is safe to save as a new Photos asset.
struct DevelopedImageArtifact: Sendable {
    let data: Data
    let metadata: NativeDevelopedMetadata
}

enum NativeFilmImageProcessorError: LocalizedError, Sendable {
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

    var errorDescription: String? {
        switch self {
        case .renderingFailed:
            return NSLocalizedString("capture_error_rendering_failed", comment: "Optical image rendering error")
        default:
            return NSLocalizedString("capture_error_processing_failed", comment: "Image processing error")
        }
    }
}

/// Applies deterministic Core Image rendering to a Photos original without modifying it.
/// Core Image work runs on a detached utility task. Seeded film grain is stable for a given profile.
enum NativeFilmImageProcessor {
    // The lens-specific optical transform uses normalized output coordinates,
    // matching the preview and offline renderers on Android.
    private static let disposableOpticsKernel = CIKernel(source: """
        vec4 readPixel(sampler image, vec2 point, vec4 bounds) {
            vec2 p = clamp(point, bounds.xy + vec2(0.5), bounds.xy + bounds.zw - vec2(0.5));
            return sample(image, samplerTransform(image, p));
        }
        kernel vec4 disposableOptics(sampler image, vec4 bounds, float distortion,
                                     float softness, float fringeAmount, float flare) {
            vec2 p = destCoord();
            vec2 center = (p - bounds.xy) / bounds.zw - vec2(0.5);
            float radius2 = 4.0 * dot(center, center);
            vec2 uv = (center * (1.0 + radius2 * distortion) + vec2(0.5)) * bounds.zw + bounds.xy;
            vec2 fringe = center * radius2 * fringeAmount * bounds.zw;
            vec4 color = readPixel(image, uv, bounds);
            color.r = readPixel(image, uv + fringe, bounds).r;
            color.b = readPixel(image, uv - fringe, bounds).b;
            float shortSide = min(bounds.z, bounds.w);
            float blur = shortSide * 0.006 * softness * min(radius2, 1.0);
            vec4 soft = (readPixel(image, uv + vec2(blur, 0.0), bounds) +
                         readPixel(image, uv - vec2(blur, 0.0), bounds) +
                         readPixel(image, uv + vec2(0.0, blur), bounds) +
                         readPixel(image, uv - vec2(0.0, blur), bounds)) * 0.25;
            color.rgb = mix(color.rgb, soft.rgb, clamp(softness * radius2, 0.0, 0.65));
            float spread = shortSide * 0.015;
            vec3 lights = (readPixel(image, uv + vec2(spread, 0.0), bounds).rgb +
                           readPixel(image, uv - vec2(spread, 0.0), bounds).rgb +
                           readPixel(image, uv + vec2(0.0, spread), bounds).rgb +
                           readPixel(image, uv - vec2(0.0, spread), bounds).rgb) * 0.25;
            color.rgb += clamp((lights - vec3(0.745)) / 0.255, 0.0, 1.0) * flare * 0.098;
            return color;
        }
        """)
    private static let normalizedVignetteKernel = CIKernel(source: """
        kernel vec4 normalizedVignette(sampler image, vec4 bounds, float amount) {
            vec2 point = destCoord();
            vec2 center = (point - bounds.xy) / bounds.zw - vec2(0.5);
            float radiusSquared = 4.0 * dot(center, center);
            float transition = clamp((radiusSquared - 0.18) / (0.75 - 0.18), 0.0, 1.0);
            float falloff = transition * transition * (3.0 - 2.0 * transition);
            vec4 color = sample(image, samplerTransform(image, point));
            color.rgb *= 1.0 - amount * falloff;
            return color;
        }
        """)

    static func developPhoto(
        assetLocalIdentifier: String,
        recipe: NativeCaptureRecipe,
        parameters: NativeResolvedVisualParameters
    ) async throws -> DevelopedImageArtifact {
        guard parameters.filmStockID == recipe.filmStockID,
              parameters.cameraBodyID == recipe.cameraBodyID,
              parameters.lensID == recipe.lensID,
              parameters.lensProfileVersion == recipe.lensProfileVersion else {
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
                    filmProfileVersion: parameters.filmProfileVersion,
                    lensProfileVersion: parameters.lensProfileVersion,
                    renderingAlgorithmID: "theia.visual-pipeline",
                    renderingSchemaVersion: parameters.schemaVersion
                ),
                parameters: parameters
            )
        }.value
    }

    /// Creates a distinct Photos asset and returns its local identifier. The source asset is never changed or deleted.
    static func saveDevelopedCopy(_ artifact: DevelopedImageArtifact) async throws -> String {
        let status = await TheiaPhotoLibraryOrganizer.requestReadWriteAuthorization()
        guard TheiaPhotoLibraryOrganizer.hasReadWriteAccess(status) else {
            throw NativeFilmImageProcessorError.photoLibraryAddDenied
        }
        let filename = "Theia-\(artifact.metadata.filmProfileID)-developed.jpg"
        do {
            return try await TheiaPhotoLibraryOrganizer.save(
                data: artifact.data,
                originalFilename: filename,
                destination: .revealed
            )
        } catch {
            throw NativeFilmImageProcessorError.photosSaveFailed
        }
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
        image = try applyVisualProfile(to: image, parameters: parameters)

        let context = CIContext(options: [.cacheIntermediates: false])
        guard let renderedImage = context.createCGImage(image, from: outputExtent) else {
            throw NativeFilmImageProcessorError.renderingFailed
        }
        let jpegData = try encodeJPEG(renderedImage, metadata: recipeMetadata)
        return DevelopedImageArtifact(data: jpegData, metadata: recipeMetadata)
    }

    /// Shared frame transform used by still development, the filtered live preview, and video export.
    static func applyVisualProfile(to source: CIImage, parameters: NativeResolvedVisualParameters) throws -> CIImage {
        switch parameters.schemaVersion {
        case 1:
            return try applyVisualProfileV1(to: source, parameters: parameters)
        default:
            throw NativeFilmImageProcessorError.unsupportedProfileVersion
        }
    }

    private static func applyVisualProfileV1(to source: CIImage, parameters: NativeResolvedVisualParameters) throws -> CIImage {
        let outputExtent = source.extent.integral
        var image = source
        let hasDisposableOptics = parameters.radialSoftness > 0 || parameters.chromaticAberration > 0 || parameters.opticalFlare > 0
        if hasDisposableOptics {
            guard let kernel = disposableOpticsKernel else { throw NativeFilmImageProcessorError.renderingFailed }
            let bounds = CIVector(x: outputExtent.minX, y: outputExtent.minY, z: outputExtent.width, w: outputExtent.height)
            guard let opticalImage = kernel.apply(
                extent: outputExtent,
                roiCallback: { _, rect in rect.insetBy(dx: -outputExtent.width * 0.2, dy: -outputExtent.height * 0.2) },
                arguments: [image, bounds, parameters.distortion, parameters.radialSoftness, parameters.chromaticAberration, parameters.opticalFlare]
            ) else { throw NativeFilmImageProcessorError.renderingFailed }
            image = opticalImage
        }
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

        if parameters.distortion != 0 && !hasDisposableOptics {
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
            if hasDisposableOptics {
                guard let kernel = normalizedVignetteKernel else { throw NativeFilmImageProcessorError.renderingFailed }
                let bounds = CIVector(x: outputExtent.minX, y: outputExtent.minY, z: outputExtent.width, w: outputExtent.height)
                guard let vignettedImage = kernel.apply(
                    extent: outputExtent,
                    roiCallback: { _, rect in rect },
                    arguments: [image, bounds, parameters.vignette]
                ) else { throw NativeFilmImageProcessorError.renderingFailed }
                image = vignettedImage
            } else {
                image = applying(
                    "CIVignette",
                    to: image,
                    values: [
                        kCIInputIntensityKey: parameters.vignette,
                        kCIInputRadiusKey: min(outputExtent.width, outputExtent.height) * 0.78,
                    ]
                )
            }
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
            "lens_profile_version": metadata.lensProfileVersion,
            "rendering_algorithm_id": metadata.renderingAlgorithmID,
            "rendering_schema_version": metadata.renderingSchemaVersion,
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
