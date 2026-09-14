# omsCompanion v1.0 Release Notes

**omsCompanion v1.0** is a major milestone release that elevates the application from beta to its first production-ready release. This release introduces zero-configuration, decentralized smartphone pairing over Nostr relays, multi-layer authenticated cryptography, significant UI/UX polish, complete removal of external cryptographic dependencies, and automated cross-platform builds.

---

## 🌟 Major Highlights

### ⚡ Decentralized Nostr Pairing & Wireless Communication
*omsCompanion* can now establish a secure, real-time wireless bridge with the **OneMoreSecret** mobile app over decentralized Nostr relays (NIP-01, Kind 25000):
- **No Port-Forwarding or Servers Required**: Works seamlessly through NATs and firewalls across decentralized Nostr relays (`relay.damus.io`, `nos.lol`, `relay.snort.social`).
- **Eliminates Manual Camera Scanning**: Once paired, encrypted requests and responses (such as TOTP codes, clipboard payloads, and file cryptography tokens) are exchanged instantly in the background without needing to repeatedly scan QR codes with your smartphone.

### 🔐 Multi-Layer Authenticated Cryptography
- **RSA-Wrapped Pairing Prompts**: Pairing QR codes are now encrypted with the Master RSA Public Key (Application ID 10 wrapped in Application ID 6 envelope), ensuring only the authorized OneMoreSecret mobile device can read the prompt and accept pairing.
- **256-bit PSK & AES-256-GCM Payload Encryption**: All Nostr message payloads are encrypted with a 256-bit Pre-Shared Key (PSK) using authenticated AES-GCM, providing end-to-end confidentiality and active tampering detection.
- **Pure-Java BIP-340 Schnorr Signatures**: Implemented native BIP-340 Schnorr signatures on the `secp256k1` curve in pure Java, providing cryptographic event verification without any external libraries.

### 🔄 Robust Session Management & Security Hardening
- **24-Hour Session Expiration**: Pairing sessions feature automatic 24-hour expiration and lifecycle management.
- **Permanent Sessions with Auto-Reconnect**: Maintains reliable pairing state with background reconnect and retired heartbeat timeouts.
- **Deduplication & Replay Protection**: Integrated a bounded LRU event cache to reject duplicate or echoed relay events.
- **Sanitized Logging**: All sensitive topic hashes, private/public keys, and subscription identifiers are automatically redacted from console logs.

---

## 🖥️ User Interface & Experience

- **Nostr Pairing Dialog**: Added a dedicated pairing configuration window with RSA public key selection and default key preference persistence.
- **Automatic Popup Dismissal**: The pairing QR window closes automatically as soon as the mobile app confirms the connection.
- **Topic Prefix Verification**: Displays the first 8 characters of the session topic below the QR code for quick visual verification.
- **Dynamic Tray States**: The system tray menu dynamically updates to show a checkmark (`✓`) when actively paired with your smartphone.

---

## ⚡ Under the Hood & Performance

- **Zero External Cryptographic Dependencies**: Completely removed BouncyCastle (`bcprov` and `bcpkix`), eliminating over 10 MB of bloat and eliminating Dependabot CVE noise.
- **50% Smaller Footprint**: JAR size was reduced from ~20 MB down to **14.8 MB**, despite bundling cross-platform runtime binaries.
- **True Cross-Platform Executable JAR**: Standalone `omscompanion.jar` now bundles OpenJFX native libraries for **Windows (.dll)**, **Linux (.so)**, and **macOS (.dylib)** in a single binary.
- **Automated GitHub Actions CI/CD**: Releases are now built, packaged, and published automatically on tag pushes via reproducible GitHub Actions workflows.

---

## 📦 Assets in this Release

| Asset | Description | Size |
| :--- | :--- | :--- |
| **`omscompanion.jar`** | Self-contained executable fat JAR for Windows, Linux, and macOS (requires JRE 17+) | ~14.8 MB |
| **`oms.cmd`** | Windows quick-launch command script (`java -jar omscompanion.jar`) | 26 B |

---

## 📝 Commits (v0.7-beta → v1.0)

- `e206a99` - feat: add CI/CD release workflow and replace BouncyCastle with pure-Java secp256k1 math
- `6909c4d` - Implement 24-hour expiration for PairingInfo session
- `27a83aa` - feat(ui): add public key selection and default key option to Nostr pairing dialog
- `cf41a6e` - feat(nostr): encrypt Nostr pairing QR code with Master RSA Public Key (envelope 6 wrapping app 10)
- `5bee3a0` - fix(nostr): implement bounded LRU event cache and robust response de-duplication
- `5101e2e` - dependabot
- `0770dca` - build(deps): update bouncycastle, jackson-databind, and zxing to resolve Dependabot alerts
- `6a9dadb` - nostr with psk
- `6372468` - feat(nostr): implement 256-bit PSK authenticated pairing and AES-256-GCM event encryption
- `39fd0d1` - dependabot
- `b86f792` - feat(nostr): implement permanent pairing sessions with auto-reconnect and retired heartbeat expiration
- `a8309f4` - feat(nostr): set heartbeat ping interval to 30s and default TTL to 120s
- `b68ad71` - feat(protocol): add APPLICATION_OMS4WEB_CALLBACK_REQUEST application identifier
- `10f06a8` - fix(nostr): ignore echoed self-events and add response logging
- `f55cf45` - fix(ui): dynamic tray menu states and reliable disconnect propagation for Nostr pairing
- `b4a9963` - fix(ui): close QR code pop-up immediately upon successful peer pairing
- `52c6844` - refactor: retire dead pairing code/assets and apply audit fixes
- `0c86a0e` - feat(ui): display topic prefix below pairing QR code for user verification
- `541d91a` - fix(logging): sanitize log output to prevent leaking topic and subscription details
- `69c9939` - fix: update FXML resource path to openjfx/NostrPairing.fxml
- `50c427c` - fix(eclipse): restore limit-modules on JRE container to resolve JavaFX split package error
- `d5d7d18` - feat: implement Nostr-based pairing and communication protocol (Kind 25000)
- `032ed7a` - chore: baseline commit before nostr pairing implementation
