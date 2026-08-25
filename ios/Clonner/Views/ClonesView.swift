import SwiftUI

struct ClonesView: View {
    @EnvironmentObject private var store: CloneStore
    @Environment(\.colorScheme) private var scheme
    @State private var showingPicker = false

    var body: some View {
        NavigationStack {
            Group {
                if store.clones.isEmpty {
                    emptyState
                } else {
                    List {
                        ForEach(store.clones) { clone in
                            NavigationLink(value: clone) {
                                CloneRow(clone: clone)
                            }
                            .listRowBackground(Palette.surface(scheme))
                            .listRowSeparatorTint(Color.primary.opacity(0.08))
                        }
                        .onDelete { indexSet in
                            indexSet.map { store.clones[$0] }.forEach(store.delete)
                        }
                        .onMove(perform: store.move)
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Palette.background(scheme).ignoresSafeArea())
            .navigationTitle("Clonner")
            .navigationDestination(for: Clone.self) { clone in
                CloneDetailView(cloneID: clone.id)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if !store.clones.isEmpty { EditButton() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        store.refreshInstalled()
                        showingPicker = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showingPicker) {
                AppPickerView()
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 18) {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Palette.accent.opacity(0.16))
                .frame(width: 84, height: 84)
                .overlay(
                    Image(systemName: "square.on.square")
                        .font(.system(size: 34, weight: .semibold))
                        .foregroundStyle(Palette.accent)
                )

            Text("No clones yet")
                .font(.title2.weight(.semibold))

            Text("Add an app to give it your own name and colour, then put it on your home screen through the Shortcuts app.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 34)

            Button {
                store.refreshInstalled()
                showingPicker = true
            } label: {
                Label("Add your first app", systemImage: "plus")
                    .font(.headline)
                    .padding(.horizontal, 22)
                    .padding(.vertical, 13)
                    .background(Capsule().fill(Palette.accent))
                    .foregroundStyle(.white)
            }
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct CloneRow: View {
    let clone: Clone

    var body: some View {
        HStack(spacing: 14) {
            IconTile(symbol: clone.symbol, tint: clone.tint.color)

            VStack(alignment: .leading, spacing: 3) {
                Text(clone.displayName)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)

                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(.vertical, 5)
    }

    private var subtitle: String {
        var parts: [String] = [clone.entry?.name ?? "Unknown app"]
        if clone.openCount > 0 { parts.append("\(clone.openCount) opens") }
        return parts.joined(separator: " · ")
    }
}
