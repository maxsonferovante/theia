import SwiftUI

/// Draws a stable, original vector illustration for a catalog entry.
/// The artwork key selects a repeatable composition; the description is read by assistive technology.
struct CatalogArtworkView: View {
    let artworkKey: String
    let visualDescription: String

    private let palette: [Color] = [
        Color(red: 0xEA / 255, green: 0xAE / 255, blue: 0x35 / 255), // solar gold
        Color(red: 0xA8 / 255, green: 0x6D / 255, blue: 0x1F / 255), // antique gold
        Color(red: 0xE7 / 255, green: 0xD7 / 255, blue: 0xB7 / 255), // ivory
        Color(red: 0x8E / 255, green: 0x48 / 255, blue: 0x2B / 255), // terracotta
        Color(red: 0x5E / 255, green: 0x32 / 255, blue: 0x1E / 255), // dark bronze
        Color(red: 0x4B / 255, green: 0x57 / 255, blue: 0x43 / 255), // muted olive
        Color(red: 0x18 / 255, green: 0x23 / 255, blue: 0x44 / 255), // deep sky
        Color(red: 0x2D / 255, green: 0x2D / 255, blue: 0x57 / 255), // cosmic violet
        Color(red: 0x6D / 255, green: 0x5A / 255, blue: 0x82 / 255), // nebula lilac
        Color(red: 0xB6 / 255, green: 0xA5 / 255, blue: 0x8C / 255), // ruin stone
        Color(red: 0x12 / 255, green: 0x15 / 255, blue: 0x22 / 255), // near black
    ]

    var body: some View {
        Canvas { context, size in
            drawArtwork(context: &context, size: size)
        }
        .aspectRatio(4 / 3, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .accessibilityElement()
        .accessibilityLabel(Text(visualDescription))
    }

    private func drawArtwork(context: inout GraphicsContext, size: CGSize) {
        let seed = stableSeed(artworkKey)
        let colors = artworkColors(seed)
        let bounds = CGRect(origin: .zero, size: size)
        let gradient = Gradient(colors: [colors[0], colors[1], colors[2]])
        context.fill(
            Path(bounds),
            with: .linearGradient(
                gradient,
                startPoint: .zero,
                endPoint: CGPoint(x: size.width, y: size.height)
            )
        )

        drawAmbientStars(context: &context, size: size, seed: seed, color: colors[3])
        switch artworkKey {
        case let key where key.hasPrefix("artwork.body."):
            drawCameraBody(context: &context, size: size, seed: seed, colors: colors)
        case let key where key.hasPrefix("artwork.lens."):
            drawLens(context: &context, size: size, seed: seed, colors: colors)
        case let key where key.hasPrefix("artwork.film."):
            drawFilmPrint(context: &context, size: size, seed: seed, colors: colors)
        default:
            drawCollectionMotif(context: &context, size: size, seed: seed, colors: colors)
        }
        drawFrame(context: &context, size: size, color: colors[4])
    }

    private func drawAmbientStars(context: inout GraphicsContext, size: CGSize, seed: UInt32, color: Color) {
        let count = 6 + Int(seed & 3)
        for index in 0..<count {
            let x = size.width * (0.1 + stableFraction(seed, index * 3) * 0.8)
            let y = size.height * (0.08 + stableFraction(seed, index * 3 + 1) * 0.38)
            let radius = min(size.width, size.height) * (0.006 + stableFraction(seed, index * 3 + 2) * 0.009)
            let rect = CGRect(x: x, y: y, width: radius * 2, height: radius * 2)
            context.fill(Path(ellipseIn: rect), with: .color(color.opacity(0.55)))
        }
    }

    private func drawCameraBody(context: inout GraphicsContext, size: CGSize, seed: UInt32, colors: [Color]) {
        let w = size.width
        let h = size.height
        let bodyWidth = w * (0.55 + stableFraction(seed, 12) * 0.12)
        let bodyHeight = h * (0.38 + stableFraction(seed, 13) * 0.06)
        let left = (w - bodyWidth) / 2
        let top = h * 0.34
        context.fill(
            Path(roundedRect: CGRect(x: left, y: top, width: bodyWidth, height: bodyHeight), cornerSize: CGSize(width: h * 0.045, height: h * 0.045)),
            with: .color(colors[3])
        )
        context.fill(
            Path(roundedRect: CGRect(x: left + bodyWidth * 0.08, y: top - bodyHeight * 0.13, width: bodyWidth * 0.34, height: bodyHeight * 0.18), cornerSize: CGSize(width: h * 0.025, height: h * 0.025)),
            with: .color(colors[2].opacity(0.9))
        )

        let lensCenter = CGPoint(x: w * 0.51, y: top + bodyHeight * 0.52)
        let lensRadius = bodyHeight * (0.4 + stableFraction(seed, 14) * 0.06)
        fillCircle(context: &context, center: lensCenter, radius: lensRadius * 1.27, color: colors[4])
        strokeCircle(context: &context, center: lensCenter, radius: lensRadius, color: colors[0], lineWidth: h * 0.018)
        fillCircle(context: &context, center: lensCenter, radius: lensRadius * 0.67, color: colors[1].opacity(0.85))
        fillCircle(context: &context, center: lensCenter, radius: lensRadius * 0.34, color: colors[2].opacity(0.82))
        fillCircle(context: &context, center: CGPoint(x: lensCenter.x - lensRadius * 0.2, y: lensCenter.y - lensRadius * 0.2), radius: lensRadius * 0.1, color: colors[0].opacity(0.7))

        for index in 0..<3 {
            let dialX = left + bodyWidth * (0.16 + Double(index) * 0.13)
            fillCircle(context: &context, center: CGPoint(x: dialX, y: top + bodyHeight * 0.18), radius: h * (0.023 + Double(index) * 0.004), color: colors[0])
        }
        var marker = Path()
        marker.move(to: CGPoint(x: left + bodyWidth * 0.73, y: top + bodyHeight * 0.18))
        marker.addLine(to: CGPoint(x: left + bodyWidth * 0.9, y: top + bodyHeight * 0.18))
        context.stroke(marker, with: .color(colors[2].opacity(0.8)), lineWidth: h * 0.014)
    }

    private func drawLens(context: inout GraphicsContext, size: CGSize, seed: UInt32, colors: [Color]) {
        let w = size.width
        let h = size.height
        let centerX = w * (0.5 + (stableFraction(seed, 20) - 0.5) * 0.12)
        let centerY = h * 0.57
        let barrelWidth = w * (0.28 + stableFraction(seed, 21) * 0.1)
        let barrelHeight = h * (0.3 + stableFraction(seed, 22) * 0.12)
        let left = centerX - barrelWidth / 2
        let top = centerY - barrelHeight / 2
        context.fill(
            Path(roundedRect: CGRect(x: left, y: top, width: barrelWidth, height: barrelHeight), cornerSize: CGSize(width: h * 0.035, height: h * 0.035)),
            with: .color(colors[3])
        )

        for index in 0..<4 {
            let x = left + barrelWidth * (0.16 + Double(index) * 0.22)
            let ring = CGRect(x: x, y: top + barrelHeight * 0.12, width: barrelWidth * 0.09, height: barrelHeight * 0.76)
            context.fill(Path(roundedRect: ring, cornerSize: CGSize(width: h * 0.012, height: h * 0.012)), with: .color(index.isMultiple(of: 2) ? colors[0] : colors[4]))
        }

        let glassCenter = CGPoint(x: centerX, y: top + barrelHeight * 0.52)
        let glassRadius = barrelHeight * 0.47
        fillCircle(context: &context, center: glassCenter, radius: glassRadius, color: colors[2])
        fillCircle(context: &context, center: glassCenter, radius: glassRadius * 0.78, color: colors[1])
        fillCircle(context: &context, center: glassCenter, radius: glassRadius * 0.56, color: colors[6])
        fillCircle(context: &context, center: CGPoint(x: glassCenter.x + glassRadius * 0.16, y: glassCenter.y - glassRadius * 0.15), radius: glassRadius * 0.3, color: colors[0].opacity(0.9))
        fillCircle(context: &context, center: CGPoint(x: glassCenter.x - glassRadius * 0.2, y: glassCenter.y - glassRadius * 0.24), radius: glassRadius * 0.1, color: colors[2].opacity(0.8))

        var flare = Path()
        flare.move(to: CGPoint(x: centerX - w * 0.32, y: h * 0.78))
        flare.addLine(to: CGPoint(x: centerX - w * 0.04, y: top + barrelHeight * 0.28))
        flare.addLine(to: CGPoint(x: centerX + w * 0.33, y: h * 0.78))
        context.fill(flare, with: .color(colors[2].opacity(0.16)))
    }

    private func drawFilmPrint(context: inout GraphicsContext, size: CGSize, seed: UInt32, colors: [Color]) {
        let w = size.width
        let h = size.height
        let cardWidth = w * (0.47 + stableFraction(seed, 30) * 0.13)
        let cardHeight = h * 0.76
        let tilt = (stableFraction(seed, 31) - 0.5) * 0.12
        let left = (w - cardWidth) / 2 + tilt * w
        let top = (h - cardHeight) / 2
        context.fill(
            Path(roundedRect: CGRect(x: left, y: top, width: cardWidth, height: cardHeight), cornerSize: CGSize(width: h * 0.035, height: h * 0.035)),
            with: .color(colors[2])
        )

        let imageRect = CGRect(x: left + cardWidth * 0.08, y: top + cardHeight * 0.08, width: cardWidth * 0.84, height: cardHeight * 0.66)
        let gradient = Gradient(colors: [colors[4], colors[0], colors[1]])
        context.fill(Path(imageRect), with: .linearGradient(gradient, startPoint: imageRect.origin, endPoint: CGPoint(x: imageRect.minX, y: imageRect.maxY)))
        let sunRadius = imageRect.height * (0.1 + stableFraction(seed, 33) * 0.06)
        let sunCenter = CGPoint(x: imageRect.minX + imageRect.width * (0.28 + stableFraction(seed, 34) * 0.42), y: imageRect.minY + imageRect.height * 0.33)
        fillCircle(context: &context, center: sunCenter, radius: sunRadius, color: colors[2].opacity(0.86))

        let horizon = imageRect.minY + imageRect.height * (0.43 + stableFraction(seed, 32) * 0.18)
        var ridge = Path()
        ridge.move(to: CGPoint(x: imageRect.minX, y: horizon))
        ridge.addLine(to: CGPoint(x: imageRect.minX + imageRect.width * 0.28, y: horizon - imageRect.height * 0.18))
        ridge.addLine(to: CGPoint(x: imageRect.minX + imageRect.width * 0.55, y: horizon + imageRect.height * 0.03))
        ridge.addLine(to: CGPoint(x: imageRect.minX + imageRect.width * 0.77, y: horizon - imageRect.height * 0.14))
        ridge.addLine(to: CGPoint(x: imageRect.maxX, y: horizon + imageRect.height * 0.04))
        ridge.addLine(to: CGPoint(x: imageRect.maxX, y: imageRect.maxY))
        ridge.addLine(to: CGPoint(x: imageRect.minX, y: imageRect.maxY))
        ridge.closeSubpath()
        context.fill(ridge, with: .color(colors[3].opacity(0.9)))

        for index in 0..<4 {
            let hole = CGRect(x: left + cardWidth * (0.22 + Double(index) * 0.19), y: top + cardHeight * 0.84, width: h * 0.024, height: h * 0.024)
            context.fill(Path(ellipseIn: hole), with: .color(colors[0]))
        }
        let caption = CGRect(x: left + cardWidth * 0.23, y: top + cardHeight * 0.91, width: cardWidth * 0.54, height: h * 0.012)
        context.fill(Path(roundedRect: caption, cornerSize: CGSize(width: h * 0.01, height: h * 0.01)), with: .color(colors[0].opacity(0.76)))
    }

    private func drawCollectionMotif(context: inout GraphicsContext, size: CGSize, seed: UInt32, colors: [Color]) {
        let w = size.width
        let h = size.height
        let center = CGPoint(x: w * 0.5, y: h * 0.55)
        let radius = min(w, h) * 0.24
        fillCircle(context: &context, center: center, radius: radius * 1.55, color: colors[2].opacity(0.14))
        fillCircle(context: &context, center: center, radius: radius, color: colors[0])
        fillCircle(context: &context, center: center, radius: radius * 0.74, color: colors[4])
        fillCircle(context: &context, center: CGPoint(x: center.x + radius * 0.16, y: center.y - radius * 0.12), radius: radius * 0.47, color: colors[3])
        fillCircle(context: &context, center: CGPoint(x: center.x - radius * 0.18, y: center.y - radius * 0.2), radius: radius * 0.2, color: colors[2])

        var arc = Path()
        arc.move(to: CGPoint(x: w * 0.15, y: h * 0.82))
        arc.addCurve(to: CGPoint(x: w * 0.86, y: h * 0.77), control1: CGPoint(x: w * 0.34, y: h * 0.7), control2: CGPoint(x: w * 0.65, y: h * 0.95))
        context.stroke(arc, with: .color(colors[2].opacity(0.8)), lineWidth: h * 0.025)

        for index in 0..<4 {
            let angle = Double(index) * 1.57 + stableFraction(seed, 40 + index) * 0.3
            let point = CGPoint(x: center.x + cos(angle) * radius * 1.4, y: center.y + sin(angle) * radius * 1.4)
            fillCircle(context: &context, center: point, radius: h * 0.018, color: colors[(index + 1) % colors.count])
        }
    }

    private func drawFrame(context: inout GraphicsContext, size: CGSize, color: Color) {
        let frame = CGRect(x: size.width * 0.035, y: size.height * 0.05, width: size.width * 0.93, height: size.height * 0.9)
        context.stroke(
            Path(roundedRect: frame, cornerSize: CGSize(width: min(size.width, size.height) * 0.05, height: min(size.width, size.height) * 0.05)),
            with: .color(color.opacity(0.55)),
            lineWidth: min(size.width, size.height) * 0.009
        )
    }

    private func fillCircle(context: inout GraphicsContext, center: CGPoint, radius: Double, color: Color) {
        let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
        context.fill(Path(ellipseIn: rect), with: .color(color))
    }

    private func strokeCircle(context: inout GraphicsContext, center: CGPoint, radius: Double, color: Color, lineWidth: Double) {
        let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
        context.stroke(Path(ellipseIn: rect), with: .color(color), lineWidth: lineWidth)
    }

    private func artworkColors(_ seed: UInt32) -> [Color] {
        let start = Int(seed % UInt32(palette.count))
        return (0...6).map { palette[(start + $0 * 2) % palette.count] }
    }

    private func stableSeed(_ value: String) -> UInt32 {
        value.utf8.reduce(UInt32(0x811C9DC5)) { hash, byte in
            (hash ^ UInt32(byte)) &* 0x01000193
        }
    }

    private func stableFraction(_ seed: UInt32, _ salt: Int) -> Double {
        let mixed = (UInt64(seed) &* 31) &+ (UInt64(salt) &* 0x045D9F3B)
        return Double((mixed >> 8) & 0x00FF_FFFF) / 16_777_215
    }
}
