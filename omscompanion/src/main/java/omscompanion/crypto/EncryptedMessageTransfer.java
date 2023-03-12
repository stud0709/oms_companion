package omscompanion.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import omscompanion.MessageComposer;

public class EncryptedMessageTransfer extends MessageComposer {
	private final String message;

	public EncryptedMessageTransfer(byte[] message, RSAPublicKey rsaPublicKey, int rsaTransformationIdx,
			int aesKeyLength, int aesTransformationIdx) throws NoSuchAlgorithmException, NoSuchPaddingException,
			IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
		super();

		// init AES
		var iv = AESUtil.generateIv();
		var secretKey = AESUtil.generateRandomSecretKey(aesKeyLength);

		// encrypt AES secret key with RSA
		var cipher = Cipher.getInstance(RsaTransformation.values()[rsaTransformationIdx].transformation);
		cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);

		var encryptedSecretKey = cipher.doFinal(secretKey.getEncoded());

		var list = new ArrayList<String>();

		// (1) application-ID
		list.add(Integer.toString(APPLICATION_ENCRYPTED_MESSAGE_TRANSFER));

		// (2) RSA transformation index
		list.add(Integer.toString(rsaTransformationIdx));

		// (3) fingerprint
		list.add(Base64.getEncoder().encodeToString(getFingerprint(rsaPublicKey)));

		// (4) AES transformation index
		list.add(Integer.toString(aesTransformationIdx));

		// (5) IV
		list.add(Base64.getEncoder().encodeToString(iv.getIV()));

		// (6) RSA-encrypted AES secret key
		list.add(Base64.getEncoder().encodeToString(encryptedSecretKey));

		// (7) AES-encrypted message
		var encryptedMessage = AESUtil.encrypt(message, secretKey, iv,
				AesTransformation.values()[aesTransformationIdx].transformation);

		list.add(Base64.getEncoder().encodeToString(encryptedMessage));

		this.message = String.join("\t", list);
	}

	private byte[] getFingerprint(RSAPublicKey rsaPublicKey) throws NoSuchAlgorithmException {
		var sha256 = MessageDigest.getInstance("SHA-256");

		var modulus = rsaPublicKey.getModulus().toByteArray();
		var publicExp = rsaPublicKey.getPublicExponent().toByteArray();

		sha256.update(modulus);
		return sha256.digest(publicExp);
	}

	@Override
	public String getMessage() {
		return message;
	}
}
