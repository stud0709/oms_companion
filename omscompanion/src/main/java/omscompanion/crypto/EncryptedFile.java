package omscompanion.crypto;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.function.Supplier;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import omscompanion.MessageComposer;
import omscompanion.OmsDataOutputStream;

public class EncryptedFile {
	public static void create(InputStream fis, File oFile, RSAPublicKey rsaPublicKey, RsaTransformation rsaTransformation,
			int aesKeyLength, AesTransformation aesTransformation, Supplier<Boolean> cancelled)
			throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException, IOException {
        var target = oFile.toPath();
        var staging = java.nio.file.Files.createTempFile(target.toAbsolutePath().getParent(), ".oms-", ".part");
        try {
            try (var out = new FileOutputStream(staging.toFile())) {
                org.onemoresecret.protocol.OmsProtocol.encrypt(rsaPublicKey, 3, fis, out, cancelled, null);
            }
            java.nio.file.Files.move(staging, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { java.nio.file.Files.deleteIfExists(staging); }
    }
}
