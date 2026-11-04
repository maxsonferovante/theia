import SwiftUI

/// A catalog value prepared for display by the iOS app's catalog adapter.
/// Keys match the stable entries in the shared equipment catalog.
struct WorkbenchEquipment: Identifiable, Hashable {
    let id: String
    let nameKey: String
    let descriptionKey: String
    let collectionKey: String?
    let artworkKey: String
    let visualDescriptionKey: String
}

/// Platform-neutral selection payload. The iOS composition root maps these stable IDs
/// to the shared Kotlin `CaptureRecipe` before passing the recipe to capture flows.
struct WorkbenchRecipeSelection: Equatable {
    let cameraBodyID: String
    let lensID: String
    let filmStockID: String
}

private enum WorkbenchSection: String, CaseIterable, Identifiable {
    case body
    case lens
    case film

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .body: "Camera body"
        case .lens: "Lens"
        case .film: "Film stock"
        }
    }

    var symbol: String {
        switch self {
        case .body: "camera.macro"
        case .lens: "camera.aperture"
        case .film: "circle.lefthalf.filled"
        }
    }
}

struct WorkbenchView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let cameraBodies: [WorkbenchEquipment]
    let lenses: [WorkbenchEquipment]
    let filmStocks: [WorkbenchEquipment]
    let currentSelection: WorkbenchRecipeSelection?
    let onRecipeSelected: (WorkbenchRecipeSelection) -> Void

    @State private var section: WorkbenchSection = .body
    @State private var selectedBodyID: String?
    @State private var selectedLensID: String?
    @State private var selectedFilmStockID: String?

    init(
        cameraBodies: [WorkbenchEquipment],
        lenses: [WorkbenchEquipment],
        filmStocks: [WorkbenchEquipment],
        currentSelection: WorkbenchRecipeSelection? = nil,
        onRecipeSelected: @escaping (WorkbenchRecipeSelection) -> Void
    ) {
        self.cameraBodies = cameraBodies
        self.lenses = lenses
        self.filmStocks = filmStocks
        self.currentSelection = currentSelection
        self.onRecipeSelected = onRecipeSelected

        _selectedBodyID = State(initialValue: currentSelection?.cameraBodyID ?? cameraBodies.first?.id)
        _selectedLensID = State(initialValue: currentSelection?.lensID ?? lenses.first?.id)
        _selectedFilmStockID = State(initialValue: currentSelection?.filmStockID ?? filmStocks.first?.id)
    }

    private var itemsForSection: [WorkbenchEquipment] {
        switch section {
        case .body: cameraBodies
        case .lens: lenses
        case .film: filmStocks
        }
    }

    private var selectedItemForSection: WorkbenchEquipment? {
        switch section {
        case .body: selectedItem(in: cameraBodies, id: selectedBodyID)
        case .lens: selectedItem(in: lenses, id: selectedLensID)
        case .film: selectedItem(in: filmStocks, id: selectedFilmStockID)
        }
    }

    private var recipeSelection: WorkbenchRecipeSelection? {
        guard
            let body = selectedItem(in: cameraBodies, id: selectedBodyID),
            let lens = selectedItem(in: lenses, id: selectedLensID),
            let film = selectedItem(in: filmStocks, id: selectedFilmStockID)
        else {
            return nil
        }

        return WorkbenchRecipeSelection(
            cameraBodyID: body.id,
            lensID: lens.id,
            filmStockID: film.id
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            header

            sectionPicker
                .padding(.horizontal, 20)
                .padding(.top, 22)

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    selectedEquipmentDetail

                    Text("Available equipment")
                        .font(.headline)
                        .foregroundStyle(WorkbenchPalette.ivory)
                        .padding(.top, 2)

                    LazyVStack(spacing: 10) {
                        ForEach(itemsForSection) { item in
                            equipmentRow(item)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 22)
                .padding(.bottom, 24)
            }
            .accessibilityIdentifier("workbench.catalog-list")
        }
        .background(WorkbenchPalette.deepSky.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            recipeFooter
        }
        .onChange(of: currentSelection) { _, updatedSelection in
            guard let updatedSelection else { return }
            selectedBodyID = updatedSelection.cameraBodyID
            selectedLensID = updatedSelection.lensID
            selectedFilmStockID = updatedSelection.filmStockID
        }
        .transaction { transaction in
            if reduceMotion { transaction.animation = nil }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("The Workbench")
                .font(.largeTitle.weight(.semibold))
                .foregroundStyle(WorkbenchPalette.ivory)
                .accessibilityAddTraits(.isHeader)

            Text("Build a camera from one body, one lens, and one film stock.")
                .font(.subheadline)
                .foregroundStyle(WorkbenchPalette.stone)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.top, 20)
    }

    private var sectionPicker: some View {
        HStack(spacing: 8) {
            ForEach(WorkbenchSection.allCases) { option in
                let selected = section == option
                Button {
                    section = option
                } label: {
                    VStack(spacing: 7) {
                        Image(systemName: option.symbol)
                            .font(.system(size: 18, weight: .medium))
                        Text(option.title)
                            .font(.caption.weight(.semibold))
                            .multilineTextAlignment(.center)
                    }
                    .foregroundStyle(selected ? WorkbenchPalette.nearBlack : WorkbenchPalette.ivory)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 72)
                    .background(
                        selected ? WorkbenchPalette.solarGold : WorkbenchPalette.cosmicViolet,
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(option.title)
                .accessibilityValue(Text(selected ? "Selected" : "Not selected"))
                .accessibilityAddTraits(selected ? .isSelected : [])
                .accessibilityHint(Text("Shows equipment options in this category."))
                .accessibilityIdentifier("workbench.section.\(option.rawValue)")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("Equipment categories"))
    }

    @ViewBuilder
    private var selectedEquipmentDetail: some View {
        if let item = selectedItemForSection {
            VStack(alignment: .leading, spacing: 10) {
                CatalogArtworkView(
                    artworkKey: item.artworkKey,
                    visualDescription: localizedCatalogText(item.visualDescriptionKey)
                )

                HStack(spacing: 12) {
                    Image(systemName: section.symbol)
                        .font(.system(size: 23, weight: .medium))
                        .foregroundStyle(WorkbenchPalette.solarGold)
                        .frame(width: 48, height: 48)
                        .background(WorkbenchPalette.deepSky, in: Circle())
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 4) {
                            Text("Selected")
                            Text(section.title)
                        }
                            .font(.caption.weight(.medium))
                            .foregroundStyle(WorkbenchPalette.stone)
                        Text(localizedCatalogKey(item.nameKey))
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(WorkbenchPalette.ivory)
                    }
                }

                Text(localizedCatalogKey(item.descriptionKey))
                    .font(.body)
                    .foregroundStyle(WorkbenchPalette.ivory.opacity(0.9))
                    .fixedSize(horizontal: false, vertical: true)

                if section == .film, let collectionKey = item.collectionKey {
                    HStack(spacing: 6) {
                        Image(systemName: "square.stack.3d.up")
                        Text("Collection:")
                        Text(localizedCatalogKey(collectionKey))
                    }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(WorkbenchPalette.solarGold)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(WorkbenchPalette.cosmicViolet, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .accessibilityElement(children: .combine)
        } else {
            VStack(spacing: 10) {
                Image(systemName: section.symbol)
                    .font(.title2)
                Text("No equipment available")
                    .font(.headline)
                Text("Equipment for this category will appear here.")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(WorkbenchPalette.ivory)
            .frame(maxWidth: .infinity)
            .padding(20)
            .background(WorkbenchPalette.cosmicViolet, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    private func equipmentRow(_ item: WorkbenchEquipment) -> some View {
        let selected = selectedID(for: section) == item.id

        return Button {
            select(item, for: section)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: selected ? "checkmark.circle.fill" : section.symbol)
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(selected ? WorkbenchPalette.solarGold : WorkbenchPalette.stone)
                    .frame(width: 28)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    Text(localizedCatalogKey(item.nameKey))
                        .font(.body.weight(.semibold))
                        .foregroundStyle(WorkbenchPalette.ivory)
                        .multilineTextAlignment(.leading)

                    Text(localizedCatalogKey(item.descriptionKey))
                        .font(.caption)
                        .foregroundStyle(WorkbenchPalette.stone)
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)

                    if section == .film, let collectionKey = item.collectionKey {
                        Text(localizedCatalogKey(collectionKey))
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(WorkbenchPalette.solarGold)
                    }
                }

                Spacer(minLength: 4)

                if selected {
                    Text("Selected")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(WorkbenchPalette.solarGold)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
            .background(
                selected ? WorkbenchPalette.solarGold.opacity(0.14) : WorkbenchPalette.nearBlack.opacity(0.55),
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(selected ? WorkbenchPalette.solarGold : WorkbenchPalette.stone.opacity(0.25), lineWidth: 1)
            }
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(localizedCatalogKey(item.nameKey)))
        .accessibilityValue(Text(selected ? "Selected" : "Not selected"))
        .accessibilityHint(Text("Selects this equipment for your camera recipe."))
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("workbench.item.\(section.rawValue).\(item.id)")
    }

    private var recipeFooter: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Your recipe")
                .font(.headline)
                .foregroundStyle(WorkbenchPalette.ivory)

            HStack(spacing: 8) {
                recipeChip(title: "Body", item: selectedItem(in: cameraBodies, id: selectedBodyID))
                recipeChip(title: "Lens", item: selectedItem(in: lenses, id: selectedLensID))
                recipeChip(title: "Film", item: selectedItem(in: filmStocks, id: selectedFilmStockID))
            }

            Button {
                guard let recipeSelection else { return }
                onRecipeSelected(recipeSelection)
            } label: {
                Text("Use this recipe")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(WorkbenchPalette.nearBlack)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(WorkbenchPalette.solarGold, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(recipeSelection == nil)
            .opacity(recipeSelection == nil ? 0.45 : 1)
            .accessibilityHint(Text("Confirms exactly one body, one lens, and one film stock."))
            .accessibilityIdentifier("workbench.confirm-recipe")
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity)
        .background(WorkbenchPalette.deepSky.opacity(0.98))
        .overlay(alignment: .top) {
            Rectangle()
                .fill(WorkbenchPalette.stone.opacity(0.25))
                .frame(height: 1)
        }
    }

    private func recipeChip(title: LocalizedStringKey, item: WorkbenchEquipment?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(WorkbenchPalette.solarGold)
            Group {
                if let item {
                    Text(localizedCatalogKey(item.nameKey))
                } else {
                    Text("Choose one")
                }
            }
            .font(.caption.weight(.medium))
            .foregroundStyle(WorkbenchPalette.ivory)
            .multilineTextAlignment(.leading)
        }
        .frame(minHeight: 44, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(WorkbenchPalette.cosmicViolet, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private func selectedID(for section: WorkbenchSection) -> String? {
        switch section {
        case .body: selectedBodyID
        case .lens: selectedLensID
        case .film: selectedFilmStockID
        }
    }

    private func select(_ item: WorkbenchEquipment, for section: WorkbenchSection) {
        switch section {
        case .body: selectedBodyID = item.id
        case .lens: selectedLensID = item.id
        case .film: selectedFilmStockID = item.id
        }
    }

    private func selectedItem(in items: [WorkbenchEquipment], id: String?) -> WorkbenchEquipment? {
        guard let id else { return nil }
        return items.first(where: { $0.id == id })
    }
}

private func localizedCatalogKey(_ key: String) -> LocalizedStringKey {
    LocalizedStringKey(key.replacingOccurrences(of: ".", with: "_").replacingOccurrences(of: "-", with: "_"))
}

private func localizedCatalogText(_ key: String) -> String {
    NSLocalizedString(key.replacingOccurrences(of: ".", with: "_").replacingOccurrences(of: "-", with: "_"), comment: "Catalog artwork accessibility description")
}

private enum WorkbenchPalette {
    static let solarGold = Color(red: 234 / 255.0, green: 174 / 255.0, blue: 53 / 255.0)
    static let ivory = Color(red: 231 / 255.0, green: 215 / 255.0, blue: 183 / 255.0)
    static let deepSky = Color(red: 24 / 255.0, green: 35 / 255.0, blue: 68 / 255.0)
    static let cosmicViolet = Color(red: 45 / 255.0, green: 45 / 255.0, blue: 87 / 255.0)
    static let stone = Color(red: 182 / 255.0, green: 165 / 255.0, blue: 140 / 255.0)
    static let nearBlack = Color(red: 18 / 255.0, green: 21 / 255.0, blue: 34 / 255.0)
}
