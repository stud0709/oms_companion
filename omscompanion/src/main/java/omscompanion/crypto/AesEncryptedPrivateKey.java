package omscompanion.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import omscompanion.MessageComposer;
import omscompanion.OmsDataOutputStream;

public class AesEncryptedPrivateKey {
	private final byte[] message;

	public AesEncryptedPrivateKey(String alias, KeyPair rsaKeyPair, byte[] aesKeyMaterial, byte[] iv, byte[] salt,
			AesTransformation aesTransformation, AesKeyAlgorithm aesKeyAlgorithm, int aesKeyLength,
			int aesKeyspecIterations) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException,
			InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException {
        if (alias.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096 || rsaKeyPair.getPrivate().getEncoded().length > 16384 || rsaKeyPair.getPublic().getEncoded().length > 16384) throw new IllegalArgumentException("Backup field too large");
        org.onemoresecret.protocol.OmsProtocol.validateKeyPair(rsaKeyPair.getPrivate().getEncoded(), rsaKeyPair.getPublic().getEncoded());
        try {
            var body = new ByteArrayOutputStream(); var out = new java.io.DataOutputStream(body);
            org.onemoresecret.protocol.OmsProtocol.writeBlob(out, alias.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            org.onemoresecret.protocol.OmsProtocol.writeBlob(out, rsaKeyPair.getPrivate().getEncoded());
            org.onemoresecret.protocol.OmsProtocol.writeBlob(out, rsaKeyPair.getPublic().getEncoded());
            message = org.onemoresecret.protocol.OmsProtocol.passwordSeal(aesKeyMaterial, salt, aesKeyspecIterations, 0, body.toByteArray());
        } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }

	private byte[] getCipherText(KeyPair rsaKeyPair, byte[] aesKeyMaterial, byte[] iv,
			AesTransformation aesTransformation) throws InvalidAlgorithmParameterException, NoSuchPaddingException,
			IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStreamCipher = new OmsDataOutputStream(baos)) {
			RSAPublicKey publicKey = (RSAPublicKey) rsaKeyPair.getPublic();
			RSAPrivateKey privateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();

			// (9.1) - private key material
			dataOutputStreamCipher.writeByteArray(privateKey.getEncoded());

			// (9.2) - public key material
			dataOutputStreamCipher.writeByteArray(publicKey.getEncoded());

			return AESUtil.process(Cipher.ENCRYPT_MODE, baos.toByteArray(), aesKeyMaterial, iv, aesTransformation);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public byte[] getMessage() {
		return message;
	}

}
