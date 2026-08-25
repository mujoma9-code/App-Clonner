import SwiftUI

@main
struct ClonnerApp: App {
    @StateObject private var store = CloneStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .onOpenURL { store.handle(url: $0) }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var store: CloneStore
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        TabView {
            ClonesView()
                .tabItem { Label("Clones", systemImage: "square.on.square") }

            ShieldView()
                .tabItem { Label("Shield", systemImage: "shield.lefthalf.filled") }

            AboutView()
                .tabItem { Label("About", systemImage: "info.circle") }
        }
        .tint(Palette.accent)
        .overlay(alignment: .bottom) {
            if let toast = store.toast {
                Text(toast)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(
                        Capsule().fill(Color.black.opacity(0.82))
                    )
                    .padding(.bottom, 68)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task {
                        try? await Task.sleep(nanoseconds: 2_400_000_000)
                        withAnimation { store.toast = nil }
                    }
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: store.toast)
    }
}
