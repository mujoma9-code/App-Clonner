import Foundation

/// A user-configured launcher entry.
///
/// On iOS this is presentation only: the sandbox has no way to duplicate an app or give
/// it a second data container, so a clone renames and recolours a *shortcut* to the real
/// app. `CloneStore` and the views never imply otherwise.
struct Clone: Identifiable, Codable, Hashable {
    let id: UUID
    var catalogID: String
    var displayName: String
    var tint: TintName
    var symbolOverride: String?
    var openCount: Int
    let createdAt: Date

    init(
        id: UUID = UUID(),
        catalogID: String,
        displayName: String,
        tint: TintName,
        symbolOverride: String? = nil,
        openCount: Int = 0,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.catalogID = catalogID
        self.displayName = displayName
        self.tint = tint
        self.symbolOverride = symbolOverride
        self.openCount = openCount
        self.createdAt = createdAt
    }

    var entry: CatalogEntry? { AppCatalog.entry(id: catalogID) }

    var symbol: String { symbolOverride ?? entry?.symbol ?? "app.fill" }

    /// The deep link that a Shortcuts-app shortcut points at to open this clone.
    var deepLink: URL? {
        URL(string: "clonner://open?id=\(id.uuidString)")
    }
}

enum TintName: String, Codable, CaseIterable, Hashable {
    case violet, teal, coral, amber, lime, pink, sky
}
