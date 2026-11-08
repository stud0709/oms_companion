# omsCompanion v1.1

omsCompanion 1.1 introduces the OMS v1 encrypted format, automatic upgrades of legacy FileSync mirrors, and stronger validation of paired key exchanges. Existing RSA keys and legacy encrypted data remain usable.

## Upgrade together

- **Update OneMoreSecret, omsCompanion and oms4web together.** Older clients cannot read newly generated v1 output.
- **Recreate Nostr pairings after updating.** The paired-message format has changed.
- Existing `oms00_` messages, `.oms00` files and printed backups remain readable. There is no need to generate new RSA keys or replace all stored secrets at once.
- Keep independent backups before upgrading, especially before running FileSync against a managed mirror directory.

## New encrypted format

- New clipboard text and QR payloads are bare Base64. The decoded data contains a binary `%OMS-` marker and protocol version; there is no human-readable `oms00_` prefix on new output.
- Clipboard monitoring recognizes both v1 messages and legacy `oms00_` ciphertext.
- New encrypted files use `.oms`. Legacy `.oms00` files remain supported, and v1 files can also be recognized by their binary marker after renaming.
- New RSA-encrypted output uses RSA-OAEP with SHA-256 and MGF1-SHA256, plus AES-256-GCM. Secret application IDs and application-format versions are encrypted with their payloads.
- The encryption toolbar now labels text output **Encrypted text**. Export confirmations and generated backup descriptions reflect the new format.

## Automatic FileSync migration

On the first sync with this version, FileSync regenerates every included legacy mirror from its plaintext source using the selected public key, even when the source checksum has not changed. No phone unlock or private-key exchange is needed.

- Each new `.oms` file is staged and atomically published before its old `.oms00` copy is removed.
- Failed or cancelled writes preserve the existing mirror copies.
- Later syncs skip unchanged files that already use the current format and recipient key. Interrupted migrations can resume file by file.
- Renaming a legacy file to `.oms` does not bypass migration. Unknown future OMS versions produce an error instead of being overwritten with an older format.

## File decryption and Nostr

- V1 files use authenticated chunks and a completion record. Decryption validates the complete payload before publishing plaintext; failed or cancelled writes preserve an existing destination.
- Key requests carry a request ID and target-format version. Responses must match the pending request.
- Paired transport authenticates the topic, signed sender and timestamp, validates event signatures and freshness, and rejects replayed authenticated messages.
- The default 120-second request timeout remains separate from the 24-hour cleanup of stale pairings.

## Private-key backups

New backup documents preserve the full PBKDF2 iteration count in a 32-bit field, fixing the legacy overflow. They use an authenticated password envelope that encrypts the alias and RSA key pair together. Keep the transport passphrase to restore the key in OneMoreSecret.

## Downloads

- **omscompanion.jar:** runnable JAR with bundled JavaFX dependencies for Windows, Linux and macOS; requires Java 17 or newer.
- **oms.cmd:** Windows launcher. Place it beside the JAR and run it, or start the application with `java -jar omscompanion.jar`.

See the [protocol specification](https://github.com/stud0709/oms_companion/blob/v1.1/docs/protocol-v1.md) and [transition guide](https://github.com/stud0709/oms_companion/blob/v1.1/docs/protocol-transition.md) for format and compatibility details.
