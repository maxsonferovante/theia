import Foundation
import TheiaShared

/// SwiftUI adapter for the stable catalog owned by the shared Kotlin domain.
enum BuiltInCatalog {
    private static let shared = BuiltInEquipmentCatalog.shared

    static var cameraBodies: [WorkbenchEquipment] {
        shared.cameraBodyEntries.compactMap { entry in
            guard let item = entry.item else { return nil }
            return WorkbenchEquipment(
                id: stableString(item.id),
                nameKey: item.nameKey,
                descriptionKey: item.descriptionKey,
                collectionKey: nil,
                artworkKey: entry.artwork.artworkKey,
                visualDescriptionKey: entry.artwork.visualCharacteristicsKey
            )
        }
    }

    static var lenses: [WorkbenchEquipment] {
        shared.lensEntries.compactMap { entry in
            guard let item = entry.item else { return nil }
            return WorkbenchEquipment(
                id: stableString(item.id),
                nameKey: item.nameKey,
                descriptionKey: item.descriptionKey,
                collectionKey: nil,
                artworkKey: entry.artwork.artworkKey,
                visualDescriptionKey: entry.artwork.visualCharacteristicsKey
            )
        }
    }

    static var filmStocks: [WorkbenchEquipment] {
        shared.filmEntries.compactMap { entry in
            guard let item = entry.item else { return nil }
            let collectionNameKey = shared.collection(key: item.collectionKey)?.nameKey
            return WorkbenchEquipment(
                id: stableString(item.id),
                nameKey: item.nameKey,
                descriptionKey: item.descriptionKey,
                collectionKey: collectionNameKey,
                artworkKey: entry.artwork.artworkKey,
                visualDescriptionKey: entry.artwork.visualCharacteristicsKey
            )
        }
    }

    private static func stableString(_ value: Any) -> String {
        if let string = value as? String { return string }
        return String(describing: value)
    }
}
