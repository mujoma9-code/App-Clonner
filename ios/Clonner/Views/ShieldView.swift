import SwiftUI

/// The ad-blocking tab.
///
/// A sideloaded app cannot run a DNS filter itself — `NEDNSSettingsManager` needs the
/// Network Extension entitlement, which a free or personal signing certificate does not
/// carry. So Clonner does the honest thing and hands the user a configuration profile,
/// which needs no entitlement, no extension and no App Store review.
struct ShieldView: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL

    private let profileURL = URL(string: "https://raw.githubusercontent.com/mujoma9-code/App-Clonner/main/ios/Clonner-AdShield-AdGuard.mobileconfig")!

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    hero

                    CardSection(title: "Install it") {
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

                        Button {
                            openURL(profileURL)
                        } label: {
                            Label("Download the profile", systemImage: "arrow.down.circle.fill")
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(Palette.accent)
                                )
                                .foregroundStyle(.white)
                        }
                        .padding(.top, 4)
                    }

                    CardSection(title: "Check it worked") {
                        Text("Open Settings → General → VPN & Device Management. “Clonner Ad Shield” should be listed under Configuration Profile.")
                            .font(.subheadline)
                        Text("Ads stop loading in every app on the device, not just the ones you added here. Remove the profile the same way to turn it off.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    CardSection(title: "What it does not do") {
                        Text("DNS filtering blocks the request that fetches an ad. It cannot remove ad space already baked into an app's own layout, and it will not touch ads served from the same domain as the app's real content — YouTube's in-video ads being the well-known case.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
            .background(Palette.background(scheme).ignoresSafeArea())
            .navigationTitle("Ad Shield")
        }
    }

    private var hero: some View {
        VStack(spacing: 12) {
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: 46, weight: .semibold))
                .foregroundStyle(
                    LinearGradient(
                        colors: [Palette.accent, Palette.teal],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            Text("Block ads everywhere")
                .font(.title2.weight(.bold))

            Text("A configuration profile points your DNS at a filtering resolver over encrypted HTTPS. Ad and tracker lookups fail, so the ads never load.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 26)
        .padding(.horizontal, 18)
        .background(
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(Palette.accent.opacity(scheme == .dark ? 0.16 : 0.10))
        )
        .padding(.top, 8)
    }

    private var steps: [String] {
        [
            "Tap the button below — Safari downloads the profile.",
            "Open Settings. A “Profile Downloaded” row appears near the top.",
            "Tap it, then Install, and enter your passcode.",
            "Confirm Install once more. That's it.",
        ]
    }
}

struct AboutView: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    CardSection(title: "What a clone is here") {
                        Text("On iOS a clone is a renamed, recoloured shortcut to an app you already have. It opens the real app and shares its login.")
                            .font(.subheadline)
                        Text("It is not a second copy with a separate account. iOS gives no app access to another app's container, and there is no entitlement that changes that — signing an app does not grant it new permissions.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    CardSection(title: "Why the app list is a catalogue") {
                        Text("iOS has no API for enumerating installed apps. Clonner ships a list of 50 well-known apps and probes each with canOpenURL, which only answers for schemes declared up front — and iOS caps that at 50.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    CardSection(title: "Nothing leaves your device") {
                        Text("Your clones live in this app's own storage. There is no Clonner account, no server and no analytics. The blocking profile sends DNS lookups to AdGuard's resolver — that is the only network traffic Clonner arranges.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    Text("Clonner 1.0.0")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 6)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
            .background(Palette.background(scheme).ignoresSafeArea())
            .navigationTitle("About")
        }
    }
}
