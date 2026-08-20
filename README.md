# ![App Icon](readme_images/qr-code.png) omsCompanion

The supplementary desktop software for [OneMoreSecret](https://github.com/stud0709/OneMoreSecret). Its main purpose is to bridge the *Air Gap* between your desktop PC and the OneMoreSecret app on your phone, enabling secure cryptographic operations for passwords, TOTP tokens, and files.

---

## 📖 Documentation

All guides, setup instructions, and feature references are available in the **[omsCompanion Wiki](https://github.com/stud0709/oms_companion/wiki)**:

- 🚀 **[Setting Up](https://github.com/stud0709/oms_companion/wiki/Setting-Up)** – Requirements, installation, and launch options.
- 🔑 **[Key Management](https://github.com/stud0709/oms_companion/wiki/Key-Management)** – Creating private keys, importing public keys, and backups.
- 📋 **[Passwords & TOTP Tokens](https://github.com/stud0709/oms_companion/wiki/Passwords-and-TOTP)** – Clipboard encryption/decryption, animated GIFs, and password generator.
- 📁 **[File Encryption & Decryption](https://github.com/stud0709/oms_companion/wiki/File-Encryption-and-Decryption)** – Hybrid RSA/AES-256 file cryptography and air-gap decryption.
- 🔄 **[FileSync](https://github.com/stud0709/oms_companion/wiki/FileSync)** – Maintain an encrypted mirror of folders in the cloud.
- ⚡ **[Nostr Connection](https://github.com/stud0709/oms_companion/wiki/Nostr-Connection)** – Fast, zero-configuration device pairing over decentralized Nostr relays.

---

## Quick Start

Requires **Java Runtime Environment (JRE) 17+**.

```cmd
# Run directly with Java:
java -jar omscompanion.jar

# Or run silently in the background on Windows:
start javaw -jar omscompanion.jar
```

*omsCompanion* will minimize to your system tray. Double-click the tray icon to process clipboard data, or right-click to access the full menu.

---

## Issues & Support

For bug reports and feature requests, please open an issue on the [GitHub Issues tracker](https://github.com/stud0709/oms_companion/issues).

## Credits & License

- Icons by [Steve Schoger](https://www.iconfinder.com/search?q=&iconset=heroicons-solid)
- Application tray icon from [Flaticon](https://www.flaticon.com/free-icon/qr-code_630939?related_id=631000)
- Licensed under the [Apache License 2.0](LICENSE).
