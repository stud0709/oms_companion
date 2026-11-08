package omscompanion.crypto;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.util.function.Supplier;
import org.onemoresecret.protocol.OmsProtocol;

/** FileSync can upgrade mirrors directly from their authoritative plaintext source. */
public final class FileSyncMirror {
    private FileSyncMirror() {}

    public static boolean requiresUpdate(Path target, RSAPublicKey recipient) throws IOException {
        if (!Files.exists(target)) return true;
        try (var input = new BufferedInputStream(Files.newInputStream(target))) {
            input.mark(5);
            byte[] prefix = input.readNBytes(5);
            if (!OmsProtocol.hasMagic(prefix)) return true;
            input.reset();
            // Unknown protocol versions fail here rather than silently downgrading a mirror.
            var header = OmsProtocol.readHeader(new DataInputStream(input));
            if (header.kind != OmsProtocol.RSA) throw new IOException("Mirror is not an RSA-encrypted file");
            return !MessageDigest.isEqual(header.fingerprint, OmsProtocol.fingerprint(recipient));
        }
    }

    public static Path legacyPath(Path target) {
        String name = target.getFileName().toString();
        if (!name.endsWith(".oms")) throw new IllegalArgumentException("Expected .oms mirror destination");
        return target.resolveSibling(name + "00");
    }

    /** Rebuild once, even when the plaintext checksum has not changed. */
    public static boolean sync(Path source, Path target, RSAPublicKey recipient, boolean sourceOrKeyChanged,
            Supplier<Boolean> cancelled) throws Exception {
        Path legacy = legacyPath(target);
        boolean removeLegacy = isLegacyMirror(legacy);
        boolean write = requiresUpdate(target, recipient) || sourceOrKeyChanged;
        if (cancelled.get()) throw new InterruptedIOException("FileSync cancelled");
        if (write) {
            try (var input = Files.newInputStream(source)) {
                EncryptedFile.create(input, target.toFile(), recipient, RsaTransformation.values()[2], 256,
                        AesTransformation.values()[2], cancelled);
            }
        }
        if (cancelled.get()) throw new InterruptedIOException("FileSync cancelled");
        if (removeLegacy) Files.delete(legacy);
        return write;
    }

    private static boolean isLegacyMirror(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var input = new BufferedInputStream(Files.newInputStream(path))) {
            input.mark(5);
            if (OmsProtocol.hasMagic(input.readNBytes(5))) {
                input.reset();
                if (OmsProtocol.readHeader(new DataInputStream(input)).kind != OmsProtocol.RSA)
                    throw new IOException("Unexpected legacy mirror content");
                return true;
            }
            // The source is authoritative: even a truncated/corrupt old mirror can be rebuilt.
            return true;
        }
    }
}
