import Foundation
import SwiftUI
import UIKit

/// Owns the clone list and the app-installed probe. Persists to `UserDefaults` as JSON —
/// the data is a few hundred bytes and never leaves the device.
@MainActor
final class CloneStore: ObservableObject {

    @Published private(set) var clones: [Clone] = []
    @Published private(set) var installed: [CatalogEntry] = []
    @Published var toast: String?

    private let defaultsKey = "dev.clonner.clones"

    init() {
        load()
        refreshInstalled()
    }

    // MARK: - Installed apps

    func refreshInstalled() {
        installed = AppCatalog.installed()
    }

    var installedByCategory: [(CatalogEntry.Category, [CatalogEntry])] {
        CatalogEntry.Category.allCases.compactMap { category in
            let matches = installed.filter { $0.category == category }
            return matches.isEmpty ? nil : (category, matches)
        }
    }

    // MARK: - Clones

    func add(_ entry: CatalogEntry) -> Clone {
        let siblings = clones.filter { $0.catalogID == entry.id }.count
        let clone = Clone(
            catalogID: entry.id,
            displayName: siblings == 0 ? entry.name : "\(entry.name) \(siblings + 1)",
            tint: TintName.allCases[clones.count % TintName.allCases.count]
        )
        clones.append(clone)
        save()
        return clone
    }

    func update(_ clone: Clone) {
        guard let index = clones.firstIndex(where: { $0.id == clone.id }) else { return }
        clones[index] = clone
        save()
    }

    func delete(_ clone: Clone) {
        clones.removeAll { $0.id == clone.id }
        save()
    }

    func move(from source: IndexSet, to destination: Int) {
        clones.move(fromOffsets: source, toOffset: destination)
        save()
    }

    func clone(withID id: UUID) -> Clone? {
        clones.first { $0.id == id }
    }

    // MARK: - Launching

    /// Opens the real app behind a clone. Returns false when the app is gone or refuses
    /// its own scheme, so the caller can say something useful instead of failing silently.
    @discardableResult
    func open(_ clone: Clone) -> Bool {
        guard let entry = clone.entry, let url = entry.probeURL else {
            toast = "\(clone.displayName) is not in the catalogue any more"
            return false
        }
        guard UIApplication.shared.canOpenURL(url) else {
            toast = "\(entry.name) does not look installed"
            return false
        }

        if let index = clones.firstIndex(where: { $0.id == clone.id }) {
            clones[index].openCount += 1
            save()
        }
        UIApplication.shared.open(url)
        return true
    }

    /// Handles `clonner://open?id=<uuid>` from a home-screen shortcut.
    func handle(url: URL) {
        guard url.scheme == "clonner", url.host == "open",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let raw = components.queryItems?.first(where: { $0.name == "id" })?.value,
              let id = UUID(uuidString: raw),
              let clone = clone(withID: id)
        else { return }
        open(clone)
    }

    // MARK: - Persistence

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey) else { return }
        clones = (try? JSONDecoder().decode([Clone].self, from: data)) ?? []
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(clones) else { return }
        UserDefaults.standard.set(data, forKey: defaultsKey)
    }
}
