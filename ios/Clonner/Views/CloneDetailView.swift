import SwiftUI

struct CloneDetailView: View {
    let cloneID: UUID

    @EnvironmentObject private var store: CloneStore
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    @State private var draftName = ""
    @State private var showShortcutHelp = false

    private var clone: Clone? { store.clone(withID: cloneID) }

    var body: some View {
        ScrollView {
            if let clone {
                VStack(spacing: 14) {
                    header(clone)
                    openButton(clone)
                    nameCard(clone)
                    tintCard(clone)
                    homeScreenCard(clone)
                    deleteButton(clone)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
        }
        .background(Palette.background(scheme).ignoresSafeArea())
        .navigationTitle("Clone")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { draftName = clone?.displayName ?? "" }
        .sheet(isPresented: $showShortcutHelp) {
            if let clone { ShortcutHelpView(clone: clone) }
        }
    }

    private func header(_ clone: Clone) -> some View {
        HStack(spacing: 16) {
            IconTile(symbol: clone.symbol, tint: clone.tint.color, size: 76)
            VStack(alignment: .leading, spacing: 4) {
                Text(clone.displayName)
                    .font(.title2.weight(.bold))
                Text(clone.entry?.name ?? "Unknown app")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Text("\(clone.openCount) opens")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(.top, 8)
    }

    private func openButton(_ clone: Clone) -> some View {
        Button {
            store.open(clone)
        } label: {
            Label("Open", systemImage: "arrow.up.forward.app.fill")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Palette.accent)
                )
                .foregroundStyle(.white)
        }
    }

    private func nameCard(_ clone: Clone) -> some View {
        CardSection(title: "Name") {
            TextField("Name", text: $draftName)
                .textFieldStyle(.roundedBorder)
                .onSubmit { commitName(clone) }

            if draftName != clone.displayName {
                Button("Save name") { commitName(clone) }
                    .font(.subheadline.weight(.semibold))
            }

            Text("Used on the shortcut you add to your home screen.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func tintCard(_ clone: Clone) -> some View {
        CardSection(title: "Colour") {
            HStack(spacing: 12) {
                ForEach(TintName.allCases, id: \.self) { tint in
                    Circle()
                        .fill(tint.color)
                        .frame(width: 34, height: 34)
                        .overlay(
                            Circle()
                                .strokeBorder(Color.primary, lineWidth: clone.tint == tint ? 3 : 0)
                        )
                        .onTapGesture {
                            var updated = clone
                            updated.tint = tint
                            store.update(updated)
                        }
                }
            }
        }
    }

    private func homeScreenCard(_ clone: Clone) -> some View {
        CardSection(title: "Home screen") {
            Text("iOS gives no app permission to place its own icons. The Shortcuts app is the supported route — and it lets you pick any photo as the icon.")
                .font(.caption)
                .foregroundStyle(.secondary)

            Button {
                showShortcutHelp = true
            } label: {
                Label("Show me how", systemImage: "square.and.arrow.up")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(Palette.accent.opacity(0.14))
                    )
                    .foregroundStyle(Palette.accent)
            }
        }
    }

    private func deleteButton(_ clone: Clone) -> some View {
        Button(role: .destructive) {
            store.delete(clone)
            dismiss()
        } label: {
            Label("Delete this clone", systemImage: "trash")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .padding(.top, 6)
    }

    private func commitName(_ clone: Clone) {
        var updated = clone
        let trimmed = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        updated.displayName = trimmed.isEmpty ? (clone.entry?.name ?? "Clone") : trimmed
        store.update(updated)
        draftName = updated.displayName
    }
}

/// Walks the user through the only supported way to get a custom home-screen icon on iOS.
struct ShortcutHelpView: View {
    let clone: Clone
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @State private var copied = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("Put “\(clone.displayName)” on your home screen")
                        .font(.title3.weight(.bold))

                    ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                        HStack(alignment: .top, spacing: 12) {
                            Text("\(index + 1)")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.white)
                                .frame(width: 22, height: 22)
                                .background(Circle().fill(Palette.accent))
                            Text(step)
                                .font(.subheadline)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }

                    if let link = clone.deepLink {
                        CardSection(title: "The link to paste") {
                            Text(link.absoluteString)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)

                            Button {
                                UIPasteboard.general.string = link.absoluteString
                                copied = true
                            } label: {
                                Label(copied ? "Copied" : "Copy link",
                                      systemImage: copied ? "checkmark" : "doc.on.doc")
                                    .font(.subheadline.weight(.semibold))
                            }
                        }
                    }
                }
                .padding(20)
            }
            .background(Palette.background(scheme).ignoresSafeArea())
            .navigationTitle("Add to Home Screen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var steps: [String] {
        [
            "Open the Shortcuts app and tap + to make a new shortcut.",
            "Add the action “Open URL” and paste the link below into it.",
            "Tap the shortcut’s name at the top, then “Add to Home Screen”.",
            "Tap the icon thumbnail to choose your own photo, and set the name to “\(clone.displayName)”.",
            "Tap Add. The icon lands on your home screen and opens \(clone.entry?.name ?? "the app") through Clonner.",
        ]
    }
}
