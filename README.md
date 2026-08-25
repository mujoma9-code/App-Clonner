# Clonner

An Android app that gives your installed apps **clones** — each with its own name, its own
badge colour, its own home-screen icon and its own ad-blocking policy — plus **Ad Shield**,
an on-device DNS filter that stops ads and trackers from loading in the first place.

Built with Kotlin, Jetpack Compose and Material 3. No root required.

---

## What it does

**Clones.** Pick any installed app and make a clone of it. A clone is a *launch profile*:
your own name for it, a coloured badge so two clones of the same app stay tellable apart,
and a per-clone switch for ad blocking.

**Home-screen shortcuts.** One tap pins a clone to your launcher with a badged version of
the app's icon. Tapping it brings Ad Shield up (if that clone wants it) and then opens the
app.

**Ad Shield.** A local VPN that filters DNS. Lookups for known ad and tracker domains are
answered `NXDOMAIN` on the device, so the ad request never leaves your phone. Everything
else is forwarded to the upstream resolver untouched. Live counters show what's being
blocked, and you can add your own rules.

---

## How the ad blocking works — and what it deliberately does not do

Clonner **does not modify, repackage, patch or redistribute other apps.** No APK is
rewritten, no ad SDK is stripped, no code is injected into another process. That approach
is copyright infringement, it breaks app signatures, and it will get you removed from the
Play Store. Clonner does not do it.

Instead ads are blocked at the **network layer**, the same approach used by AdAway, DNS66,
Blokada and RethinkDNS:

1. `ClonnerVpnService` establishes a TUN interface and routes **only** the DNS server
   address into it (`addRoute(10.215.173.2, 32)`). All other traffic takes its normal path
   and never passes through Clonner.
2. Each DNS query is parsed for its question name.
3. `BlocklistEngine` checks the domain and each of its parent domains against the loaded
   rules. User overrides win over list rules, and the most specific override wins.
4. Blocked → Clonner synthesises an `NXDOMAIN` reply itself and writes it back.
   Allowed → the query goes to the upstream resolver over a `protect()`ed socket, and the
   reply is relayed back verbatim.

The word "VPN" here is only the Android API that lets an app see its own device's DNS
traffic. **There is no Clonner server and no remote endpoint.** Nothing about the apps you
have installed or the domains you look up is uploaded anywhere.

### A clone is not a sandbox

A clone launches the **real installed app**, and shares that app's data and login session.
Clonner does not give you two independently logged-in copies of one app — that requires a
full app-virtualisation container or a work profile, neither of which this app implements.
If you want two separate accounts, use your device's built-in Dual Apps / Work Profile
feature. Clonner's clones are about *organisation and shielding*, not isolation.

---

## Project layout

```
app/src/main/java/dev/clonner/
├── ClonnerApp.kt              Application singleton wiring
├── MainActivity.kt            Compose host, navigation, VPN consent flow
├── data/
│   ├── AppRepository.kt       Installed-app enumeration, icon rasterising
│   ├── CloneRepository.kt     Clone persistence (DataStore + kotlinx.serialization)
│   ├── BlocklistRepository.kt Blocklist download, caching, user rules
│   ├── HostsParser.kt         One line of a hosts file -> a domain
│   └── model/Models.kt        Clone, InstalledApp, BlocklistSource, BlockEvent
├── vpn/
│   ├── ClonnerVpnService.kt   The TUN loop and upstream forwarding
│   ├── Packets.kt             IPv4 + UDP parse/build, header and pseudo-header checksums
│   ├── DnsMessage.kt          Question-name reading, NXDOMAIN synthesis
│   └── BlocklistEngine.kt     Domain matching, overrides, live stats
├── shortcut/
│   ├── CloneShortcuts.kt      Pin requests, badged adaptive icons
│   ├── CloneLaunchActivity.kt Invisible trampoline behind each pinned icon
│   └── PinResultReceiver.kt   Records that the launcher accepted a pin
└── ui/                        Theme, shared components, screens
```

## Build

Requires JDK 17+ and the Android SDK (compileSdk 34).

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release APK (R8 shrinking on)
./gradlew :app:testDebugUnitTest    # unit tests
```

Output lands in `app/build/outputs/apk/`.

## Tests

33 JVM unit tests cover the parts that are easy to get subtly wrong and impossible to
eyeball:

- **`PacketsTest`** — IPv4/UDP round-trips, odd-length payloads, header field layout, and
  verification that both the IP header checksum and the UDP pseudo-header checksum sum to
  zero. Also that IPv6, TCP, runts and fragments are rejected.
- **`DnsMessageTest`** — question-name reading, rejection of responses, non-QUERY opcodes
  and compression pointers; and that an `NXDOMAIN` reply echoes the transaction id, sets
  QR/RA, carries RD over, zeroes the record counts and preserves the question verbatim.
- **`BlocklistEngineTest`** — subdomain coverage, that `ads.example.com` never leaks into
  `example.com` or `notads.example.com`, override precedence, and counter behaviour.
- **`HostsParserTest`** — hosts and bare-domain formats, comments, normalisation, and
  rejection of malformed hostnames and bare IPs.

## Permissions

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | Listing your installed apps is the app's core function. The list never leaves the device. |
| `BIND_VPN_SERVICE` | Required to run the local DNS filter. Android shows its own consent dialog first. |
| `INTERNET` | Downloading blocklists and forwarding allowed DNS queries upstream. |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE`) | Keeps the shield alive with a visible ongoing notification. |
| `POST_NOTIFICATIONS` | That ongoing notification. The shield still works if you decline. |

## Blocklist sources

Bundled seed list ships with the APK so the shield works before the first download. Full
lists are fetched on demand from StevenBlack, AdAway and Peter Lowe's list — all
community-maintained and freely available.

## Requirements

- Android 8.0 (API 26) or newer — pinned shortcuts and adaptive icons need it
- Dynamic Material You colour on Android 12+, with a hand-tuned violet/teal fallback below
