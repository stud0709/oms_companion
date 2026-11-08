import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import org.junit.Test;
import static org.junit.Assert.*;
import org.onemoresecret.protocol.OmsProtocol;
import omscompanion.crypto.*;

public class CompanionProtocolTest {
    private java.util.Map<String, String> fixtures() throws IOException {
        return ProtocolTest.load(Path.of(System.getProperty("oms.fixtures")));
    }
    private RSAPublicKey publicKey() throws Exception {
        return (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(
                java.util.Base64.getDecoder().decode(fixtures().get("publicKey"))));
    }
    private void clean(Path dir) throws IOException {
        try (var entries = Files.list(dir)) { for (Path entry : entries.toList()) Files.delete(entry); }
        Files.delete(dir);
    }
    private byte[] openMirror(Path file, PrivateKey key) throws Exception {
        try (var input = new DataInputStream(Files.newInputStream(file))) {
            var header = OmsProtocol.readHeader(input);
            byte[] aes = OmsProtocol.rsa(Cipher.DECRYPT_MODE, key, header.wrappedKey);
            var plain = new ByteArrayOutputStream(); OmsProtocol.decryptFile(header, aes, input, plain, null, null);
            return plain.toByteArray();
        }
    }
    @Test public void fileSyncUpgradesEveryUnchangedLegacyMirrorOnce() throws Exception {
        Path dir = Files.createTempDirectory("oms-sync-test-");
        var fixture = fixtures(); var recipient = publicKey();
        var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(
                java.util.Base64.getDecoder().decode(fixture.get("privateKey"))));
        byte[] legacy = java.util.Base64.getDecoder().decode(fixture.get("legacyVault"));
        try {
            for (String name : java.util.List.of("first", "second", "broken")) {
                Path source = dir.resolve(name), target = dir.resolve(name + ".oms");
                byte[] body = name.getBytes(java.nio.charset.StandardCharsets.UTF_8); Files.write(source, body);
                Files.write(FileSyncMirror.legacyPath(target), name.equals("broken") ? new byte[0] : legacy);
                if (name.equals("second")) Files.write(target, legacy); // Renaming v0 is not migration.
                assertTrue(FileSyncMirror.sync(source, target, recipient, false, () -> false));
                assertFalse(Files.exists(FileSyncMirror.legacyPath(target)));
                assertArrayEquals(body, openMirror(target, privateKey));
                byte[] unchanged = Files.readAllBytes(target);
                assertFalse(FileSyncMirror.sync(source, target, recipient, false, () -> false));
                assertArrayEquals(unchanged, Files.readAllBytes(target));
                assertTrue(FileSyncMirror.sync(source, target, recipient, true, () -> false));
                assertFalse(java.util.Arrays.equals(unchanged, Files.readAllBytes(target)));
            }
            var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var newKey = generator.generateKeyPair();
            Path target = dir.resolve("first.oms");
            assertTrue(FileSyncMirror.sync(dir.resolve("first"), target, (RSAPublicKey)newKey.getPublic(), false, () -> false));
            assertArrayEquals("first".getBytes(java.nio.charset.StandardCharsets.UTF_8), openMirror(target, newKey.getPrivate()));
        } finally { clean(dir); }
    }
    @Test public void failedOrCancelledFileSyncPreservesLegacyCopiesAndFutureVersions() throws Exception {
        Path dir = Files.createTempDirectory("oms-sync-test-");
        Path source = dir.resolve("source"), target = dir.resolve("source.oms"), legacyPath = FileSyncMirror.legacyPath(target);
        byte[] legacy = java.util.Base64.getDecoder().decode(fixtures().get("legacyVault")); var recipient = publicKey();
        try {
            Files.write(legacyPath, legacy); Files.write(target, legacy);
            assertThrows(IOException.class, () -> FileSyncMirror.sync(source, target, recipient, false, () -> false));
            assertArrayEquals(legacy, Files.readAllBytes(target)); assertArrayEquals(legacy, Files.readAllBytes(legacyPath));
            Files.write(source, new byte[200000]); var calls = new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(InterruptedIOException.class, () -> FileSyncMirror.sync(source, target, recipient, false, () -> calls.getAndIncrement() > 2));
            assertArrayEquals(legacy, Files.readAllBytes(target)); assertArrayEquals(legacy, Files.readAllBytes(legacyPath));
            try (var entries = Files.list(dir)) { assertEquals(3L, entries.count()); }
            byte[] future = java.util.Base64.getDecoder().decode(fixtures().get("rsaFile")); future[5] = 2; Files.write(target, future);
            assertThrows(IOException.class, () -> FileSyncMirror.sync(source, target, recipient, true, () -> false));
            assertArrayEquals(future, Files.readAllBytes(target)); assertArrayEquals(legacy, Files.readAllBytes(legacyPath));
        } finally { clean(dir); }
    }
    @Test public void cancelledWriterPreservesDestinationAndRemovesStaging() throws Exception {
        Path dir = Files.createTempDirectory("oms-file-test-");
        Path target = dir.resolve("sample.oms");
        byte[] original = {1, 2, 3}; Files.write(target, original);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        try {
            assertThrows(InterruptedIOException.class, () -> EncryptedFile.create(new ByteArrayInputStream(new byte[70000]), target.toFile(), (RSAPublicKey)pair.getPublic(), RsaTransformation.values()[0], 128, AesTransformation.values()[0], () -> true));
            assertArrayEquals(original, Files.readAllBytes(target));
            try (var files = Files.list(dir)) { assertEquals(1L, files.count()); }
            byte[] body = new byte[70000]; Arrays.fill(body, (byte)42);
            EncryptedFile.create(new ByteArrayInputStream(body), target.toFile(), (RSAPublicKey)pair.getPublic(), RsaTransformation.values()[0], 128, AesTransformation.values()[0], () -> false);
            try (var input = new DataInputStream(Files.newInputStream(target))) {
                var header = OmsProtocol.readHeader(input);
                byte[] key = OmsProtocol.rsa(Cipher.DECRYPT_MODE, pair.getPrivate(), header.wrappedKey);
                var plain = new ByteArrayOutputStream(); OmsProtocol.decryptFile(header, key, input, plain, null, null);
                assertArrayEquals(body, plain.toByteArray());
            }
            try (var files = Files.list(dir)) { assertEquals(1L, files.count()); }
        } finally { Files.deleteIfExists(target); Files.delete(dir); }
    }
}
