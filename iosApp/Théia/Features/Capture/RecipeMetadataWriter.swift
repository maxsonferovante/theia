import Foundation
import ImageIO

/// Adds a compact versioned CaptureRecipe description to image EXIF when the source format supports it.
func embeddingRecipeMetadata(
    _ data: Data,
    recipe: NativeCaptureRecipe,
    geotagMetadata: LocalGeotagMetadata? = nil
) -> Data {
    guard
        let source = CGImageSourceCreateWithData(data as CFData, nil),
        let type = CGImageSourceGetType(source),
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
    else { return data }

    var updated = properties
    var exif = updated[kCGImagePropertyExifDictionary] as? [CFString: Any] ?? [:]
    let compactRecipe: [String: Any] = [
        "version": 1,
        "id": recipe.stableID,
        "body": recipe.cameraBodyID,
        "lens": recipe.lensID,
        "film": recipe.filmStockID,
        "filter_applied": false,
        "filter_status": "original_unfiltered",
    ]
    guard let encoded = try? JSONSerialization.data(withJSONObject: compactRecipe),
          let json = String(data: encoded, encoding: .utf8) else { return data }
    exif[kCGImagePropertyExifUserComment] = json
    updated[kCGImagePropertyExifDictionary] = exif

    if let geotagMetadata {
        let coordinate = geotagMetadata.coordinate
        var gps = updated[kCGImagePropertyGPSDictionary] as? [CFString: Any] ?? [:]
        gps[kCGImagePropertyGPSLatitude] = abs(coordinate.latitude)
        gps[kCGImagePropertyGPSLatitudeRef] = coordinate.latitude < 0 ? "S" : "N"
        gps[kCGImagePropertyGPSLongitude] = abs(coordinate.longitude)
        gps[kCGImagePropertyGPSLongitudeRef] = coordinate.longitude < 0 ? "W" : "E"
        if let precision = geotagMetadata.precisionMeters {
            gps[kCGImagePropertyGPSHPositioningError] = precision
        }
        updated[kCGImagePropertyGPSDictionary] = gps
    }

    let destinationData = NSMutableData()
    guard let destination = CGImageDestinationCreateWithData(destinationData, type, 1, nil) else { return data }
    CGImageDestinationAddImageFromSource(destination, source, 0, updated as CFDictionary)
    guard CGImageDestinationFinalize(destination) else { return data }
    return destinationData as Data
}
