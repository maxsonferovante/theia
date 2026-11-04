import SwiftUI

/// A local coordinate plot with no map tiles, geocoding, or network access.
struct OfflineLocationPlotView: View {
    let locations: [LocalGeotagCoordinate]
    var plotTitle: String = NSLocalizedString("location_plot_title", comment: "Offline location plot heading")

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(plotTitle)
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            if locations.isEmpty {
                ContentUnavailableView(
                    NSLocalizedString("location_plot_empty_title", comment: "Empty location plot heading"),
                    systemImage: "location.slash",
                    description: Text("location_plot_empty_description")
                )
                .frame(minHeight: 180)
            } else {
                Canvas { context, size in
                    drawPlot(context: &context, size: size)
                }
                .frame(height: 220)
                .background(Color(red: 0.07, green: 0.08, blue: 0.13), in: RoundedRectangle(cornerRadius: 16))
                .accessibilityElement()
                .accessibilityLabel(Text(String(format: NSLocalizedString("location_plot_accessibility", comment: "Offline plot screen reader label"), locations.count)))
                .accessibilityHint(Text("location_plot_accessibility_hint"))

                Text("location_plot_offline_note")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func drawPlot(context: inout GraphicsContext, size: CGSize) {
        let inset: CGFloat = 24
        let plot = CGRect(x: inset, y: inset, width: size.width - inset * 2, height: size.height - inset * 2)
        let gridColor = Color.white.opacity(0.16)
        for index in 0...4 {
            let fraction = CGFloat(index) / 4
            var grid = Path()
            grid.move(to: CGPoint(x: plot.minX + plot.width * fraction, y: plot.minY))
            grid.addLine(to: CGPoint(x: plot.minX + plot.width * fraction, y: plot.maxY))
            grid.move(to: CGPoint(x: plot.minX, y: plot.minY + plot.height * fraction))
            grid.addLine(to: CGPoint(x: plot.maxX, y: plot.minY + plot.height * fraction))
            context.stroke(grid, with: .color(gridColor), lineWidth: 1)
        }

        let latitudes = locations.map(\.latitude)
        let longitudes = locations.map(\.longitude)
        let latitudeRange = max(0.0001, (latitudes.max() ?? 0) - (latitudes.min() ?? 0))
        let longitudeRange = max(0.0001, (longitudes.max() ?? 0) - (longitudes.min() ?? 0))
        let minLatitude = latitudes.min() ?? 0
        let minLongitude = longitudes.min() ?? 0
        let points = locations.map { coordinate in
            CGPoint(
                x: plot.minX + CGFloat((coordinate.longitude - minLongitude) / longitudeRange) * plot.width,
                y: plot.maxY - CGFloat((coordinate.latitude - minLatitude) / latitudeRange) * plot.height
            )
        }

        if points.count > 1 {
            var route = Path()
            route.addLines(points)
            context.stroke(route, with: .color(Color(red: 0.92, green: 0.68, blue: 0.21)), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
        }
        for (index, point) in points.enumerated() {
            let radius: CGFloat = index == points.count - 1 ? 7 : 5
            let marker = CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)
            context.fill(Path(ellipseIn: marker), with: .color(index == points.count - 1 ? .white : Color(red: 0.92, green: 0.68, blue: 0.21)))
            context.stroke(Path(ellipseIn: marker), with: .color(.black.opacity(0.75)), lineWidth: 1.5)
        }
    }
}
