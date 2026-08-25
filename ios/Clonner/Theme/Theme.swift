import SwiftUI

extension TintName {
    var color: Color {
        switch self {
        case .violet: return Color(red: 0.486, green: 0.302, blue: 1.0)
        case .teal:   return Color(red: 0.071, green: 0.941, blue: 0.882)
        case .coral:  return Color(red: 1.0, green: 0.420, blue: 0.420)
        case .amber:  return Color(red: 1.0, green: 0.690, blue: 0.125)
        case .lime:   return Color(red: 0.482, green: 0.878, blue: 0.310)
        case .pink:   return Color(red: 1.0, green: 0.373, blue: 0.635)
        case .sky:    return Color(red: 0.208, green: 0.655, blue: 1.0)
        }
    }

    var label: String { rawValue.capitalized }
}

enum Palette {
    static let accent = Color(red: 0.486, green: 0.302, blue: 1.0)
    static let accentDeep = Color(red: 0.357, green: 0.169, blue: 0.878)
    static let teal = Color(red: 0.071, green: 0.941, blue: 0.882)

    /// Card fill that stays legible in both appearances.
    static func surface(_ scheme: ColorScheme) -> Color {
        scheme == .dark
            ? Color(red: 0.090, green: 0.078, blue: 0.122)
            : Color.white
    }

    static func background(_ scheme: ColorScheme) -> Color {
        scheme == .dark
            ? Color(red: 0.059, green: 0.051, blue: 0.090)
            : Color(red: 0.969, green: 0.957, blue: 1.0)
    }
}

/// Rounded icon tile used for every clone and catalogue row.
struct IconTile: View {
    let symbol: String
    let tint: Color
    var size: CGFloat = 52

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.26, style: .continuous)
            .fill(
                LinearGradient(
                    colors: [tint, tint.opacity(0.62)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: symbol)
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .foregroundStyle(.white)
            )
            .shadow(color: tint.opacity(0.32), radius: 7, x: 0, y: 4)
    }
}

/// Card container matching the Android build's surface treatment.
struct CardSection<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title.uppercased())
                .font(.caption2.weight(.semibold))
                .kerning(0.6)
                .foregroundStyle(Palette.accent)
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Palette.surface(scheme))
        )
    }
}
