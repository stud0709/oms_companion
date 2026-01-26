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
		super();

		// --- create message ---
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {
			// (1) application-ID
			dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER);

			// (2) alias
			dataOutputStream.writeString(alias);

			// --- AES parameter ---

			// (3) salt
			dataOutputStream.writeByteArray(salt);

			// (4) iv
			dataOutputStream.writeByteArray(iv);

			// (5) AES transformation index
			dataOutputStream.writeUnsignedShort(aesTransformation.ordinal());

			// (6) key algorithm index
			dataOutputStream.writeUnsignedShort(aesKeyAlgorithm.ordinal());

			// (7) keyspec length
			dataOutputStream.writeUnsignedShort(aesKeyLength);

			// (8) keyspec iterations
			dataOutputStream.writeUnsignedShort(aesKeyspecIterations);

			// --- encrypted data ---

			// (9) cipher text
			dataOutputStream.writeByteArray(getCipherText(rsaKeyPair, aesKeyMaterial, iv, aesTransformation));

			this.message = baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
