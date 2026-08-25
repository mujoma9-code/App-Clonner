import Foundation
import UIKit

/// One app Clonner knows how to open.
///
/// iOS has no API for listing installed apps, so Clonner ships a curated catalogue and
/// probes each entry with `canOpenURL`. That call only answers for schemes declared in
/// `LSApplicationQueriesSchemes`, which iOS caps at 50 — so the catalogue and that list
/// are kept in step deliberately.
struct CatalogEntry: Identifiable, Hashable {
    let id: String
    let name: String
    let scheme: String
    let category: Category
    let symbol: String
    let tint: TintName

    var probeURL: URL? { URL(string: "\(scheme)://") }

    enum Category: String, CaseIterable, Hashable {
        case social = "Social"
        case media = "Music & Video"
        case browsers = "Browsers"
        case work = "Mail & Work"
        case files = "Files & Notes"
        case travel = "Maps & Travel"
    }
}

enum AppCatalog {

    /// Detects which catalogue entries are actually installed on this device.
    static func installed() -> [CatalogEntry] {
        all.filter { entry in
            guard let url = entry.probeURL else { return false }
            return UIApplication.shared.canOpenURL(url)
        }
    }

    static func entry(id: String) -> CatalogEntry? {
        all.first { $0.id == id }
    }

    static let all: [CatalogEntry] = [
        // Social
        .init(id: "instagram", name: "Instagram", scheme: "instagram", category: .social, symbol: "camera.fill", tint: .pink),
        .init(id: "facebook", name: "Facebook", scheme: "fb", category: .social, symbol: "person.2.fill", tint: .sky),
        .init(id: "x", name: "X", scheme: "twitter", category: .social, symbol: "bubble.left.fill", tint: .violet),
        .init(id: "snapchat", name: "Snapchat", scheme: "snapchat", category: .social, symbol: "bolt.fill", tint: .amber),
        .init(id: "tiktok", name: "TikTok", scheme: "tiktok", category: .social, symbol: "music.note", tint: .teal),
        .init(id: "whatsapp", name: "WhatsApp", scheme: "whatsapp", category: .social, symbol: "phone.fill", tint: .lime),
        .init(id: "telegram", name: "Telegram", scheme: "tg", category: .social, symbol: "paperplane.fill", tint: .sky),
        .init(id: "discord", name: "Discord", scheme: "discord", category: .social, symbol: "gamecontroller.fill", tint: .violet),
        .init(id: "reddit", name: "Reddit", scheme: "reddit", category: .social, symbol: "text.bubble.fill", tint: .coral),
        .init(id: "pinterest", name: "Pinterest", scheme: "pinterest", category: .social, symbol: "pin.fill", tint: .coral),
        .init(id: "linkedin", name: "LinkedIn", scheme: "linkedin", category: .social, symbol: "briefcase.fill", tint: .sky),
        .init(id: "threads", name: "Threads", scheme: "threads", category: .social, symbol: "at", tint: .violet),

        // Music & video
        .init(id: "youtube", name: "YouTube", scheme: "youtube", category: .media, symbol: "play.rectangle.fill", tint: .coral),
        .init(id: "spotify", name: "Spotify", scheme: "spotify", category: .media, symbol: "music.note", tint: .lime),
        .init(id: "applemusic", name: "Apple Music", scheme: "music", category: .media, symbol: "music.note.list", tint: .pink),
        .init(id: "soundcloud", name: "SoundCloud", scheme: "soundcloud", category: .media, symbol: "waveform", tint: .amber),
        .init(id: "netflix", name: "Netflix", scheme: "nflx", category: .media, symbol: "film.fill", tint: .coral),
        .init(id: "primevideo", name: "Prime Video", scheme: "primevideo", category: .media, symbol: "play.tv.fill", tint: .sky),
        .init(id: "twitch", name: "Twitch", scheme: "twitch", category: .media, symbol: "dot.radiowaves.left.and.right", tint: .violet),
        .init(id: "deezer", name: "Deezer", scheme: "deezer", category: .media, symbol: "hifispeaker.fill", tint: .violet),
        .init(id: "shazam", name: "Shazam", scheme: "shazam", category: .media, symbol: "waveform.circle.fill", tint: .sky),
        .init(id: "vlc", name: "VLC", scheme: "vlc", category: .media, symbol: "cone.fill", tint: .amber),

        // Browsers
        .init(id: "chrome", name: "Chrome", scheme: "googlechrome", category: .browsers, symbol: "globe", tint: .sky),
        .init(id: "firefox", name: "Firefox", scheme: "firefox", category: .browsers, symbol: "flame.fill", tint: .amber),
        .init(id: "opera", name: "Opera", scheme: "opera", category: .browsers, symbol: "globe.europe.africa.fill", tint: .coral),
        .init(id: "brave", name: "Brave", scheme: "brave", category: .browsers, symbol: "shield.lefthalf.filled", tint: .amber),
        .init(id: "duckduckgo", name: "DuckDuckGo", scheme: "ddgQuickLink", category: .browsers, symbol: "magnifyingglass", tint: .coral),

        // Mail & work
        .init(id: "gmail", name: "Gmail", scheme: "googlegmail", category: .work, symbol: "envelope.fill", tint: .coral),
        .init(id: "outlook", name: "Outlook", scheme: "ms-outlook", category: .work, symbol: "envelope.badge.fill", tint: .sky),
        .init(id: "protonmail", name: "Proton Mail", scheme: "protonmail", category: .work, symbol: "lock.fill", tint: .violet),
        .init(id: "slack", name: "Slack", scheme: "slack", category: .work, symbol: "number.square.fill", tint: .violet),
        .init(id: "teams", name: "Teams", scheme: "msteams", category: .work, symbol: "person.3.fill", tint: .violet),
        .init(id: "zoom", name: "Zoom", scheme: "zoomus", category: .work, symbol: "video.fill", tint: .sky),
        .init(id: "webex", name: "Webex", scheme: "webex", category: .work, symbol: "video.circle.fill", tint: .lime),

        // Files & notes
        .init(id: "googledrive", name: "Google Drive", scheme: "googledrive", category: .files, symbol: "externaldrive.fill", tint: .lime),
        .init(id: "dropbox", name: "Dropbox", scheme: "dropbox", category: .files, symbol: "shippingbox.fill", tint: .sky),
        .init(id: "onedrive", name: "OneDrive", scheme: "onedrive", category: .files, symbol: "cloud.fill", tint: .sky),
        .init(id: "notion", name: "Notion", scheme: "notion", category: .files, symbol: "doc.text.fill", tint: .violet),
        .init(id: "evernote", name: "Evernote", scheme: "evernote", category: .files, symbol: "note.text", tint: .lime),
        .init(id: "todoist", name: "Todoist", scheme: "todoist", category: .files, symbol: "checkmark.circle.fill", tint: .coral),
        .init(id: "things", name: "Things", scheme: "things", category: .files, symbol: "list.bullet.circle.fill", tint: .sky),
        .init(id: "bear", name: "Bear", scheme: "bear", category: .files, symbol: "pencil.circle.fill", tint: .coral),

        // Maps & travel
        .init(id: "googlemaps", name: "Google Maps", scheme: "comgooglemaps", category: .travel, symbol: "map.fill", tint: .lime),
        .init(id: "waze", name: "Waze", scheme: "waze", category: .travel, symbol: "car.fill", tint: .teal),
        .init(id: "citymapper", name: "Citymapper", scheme: "citymapper", category: .travel, symbol: "tram.fill", tint: .lime),
        .init(id: "uber", name: "Uber", scheme: "uber", category: .travel, symbol: "car.circle.fill", tint: .violet),
        .init(id: "lyft", name: "Lyft", scheme: "lyft", category: .travel, symbol: "car.2.fill", tint: .pink),
        .init(id: "airbnb", name: "Airbnb", scheme: "abnb", category: .travel, symbol: "house.fill", tint: .coral),
        .init(id: "booking", name: "Booking.com", scheme: "booking", category: .travel, symbol: "bed.double.fill", tint: .sky),
        .init(id: "shopify", name: "Shopify", scheme: "shopify", category: .travel, symbol: "bag.fill", tint: .lime),
    ]
}
