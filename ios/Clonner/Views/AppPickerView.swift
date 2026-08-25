import SwiftUI

struct AppPickerView: View {
    @EnvironmentObject private var store: CloneStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @State private var query = ""

    var body: some View {
        NavigationStack {
            Group {
                if store.installed.isEmpty {
                    noneDetected
                } else {
                    List {
                        ForEach(filteredCategories, id: \.0) { category, entries in
                            Section(category.rawValue) {
                                ForEach(entries) { entry in
                                    Button {
                                        _ = store.add(entry)
                                        dismiss()
                                    } label: {
                                        HStack(spacing: 14) {
                                            IconTile(symbol: entry.symbol, tint: entry.tint.color, size: 42)
                                            Text(entry.name)
                                                .font(.body)
                                                .foregroundStyle(.primary)
                                            Spacer()
                                            Image(systemName: "plus.circle.fill")
                                                .foregroundStyle(Palette.accent)
                                        }
                                        .padding(.vertical, 3)
                                    }
                                    .listRowBackground(Palette.surface(scheme))
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Palette.background(scheme).ignoresSafeArea())
            .searchable(text: $query, prompt: "Search apps")
            .navigationTitle("Add an app")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var filteredCategories: [(CatalogEntry.Category, [CatalogEntry])] {
        store.installedByCategory.compactMap { category, entries in
            let matches = query.isEmpty
                ? entries
                : entries.filter { $0.name.localizedCaseInsensitiveContains(query) }
            return matches.isEmpty ? nil : (category, matches)
        }
    }

    private var noneDetected: some View {
        VStack(spacing: 16) {
            Image(systemName: "questionmark.app.dashed")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)

            Text("No catalogue apps detected")
                .font(.title3.weight(.semibold))

            Text("iOS gives no app a way to list what you have installed. Clonner ships a catalogue of 50 apps and checks each one — none of them answered on this device.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
