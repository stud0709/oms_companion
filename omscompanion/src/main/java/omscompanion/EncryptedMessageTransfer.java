package omscompanion;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import omscompanion.crypto.AESUtil;

public class EncryptedMessageTransfer extends MessageComposer {
	private final String message;
	/**
	 * See https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html for
	 * the list of supported transformations
	 * 
	 * See https://developer.android.com/reference/javax/crypto/Cipher for Android
	 */
	public static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
	// or RSA/ECB/OAEPWithSHA-1AndMGF1Padding
	// or RSA/ECB/OAEPWithSHA-256AndMGF1Padding
	// or RSA/ECB/PKCS1Padding

	public EncryptedMessageTransfer(byte[] message, RSAPublicKey rsaPublicKey, String rsaTransformation)
			throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, IllegalBlockSizeException,
			BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {

		// init AES
		IvParameterSpec iv = AESUtil.generateIv();
		SecretKey secretKey = AESUtil.generateRandomSecretKey();

		// encrypt AES secret key with RSA
		Cipher cipher = Cipher.getInstance(rsaTransformation);
		cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);

		byte[] encryptedSecretKey = cipher.doFinal(secretKey.getEncoded());

		List<String> list = new ArrayList<>();

		// (1) application-ID
		list.add(Integer.toString(APPLICATION_ENCRYPTED_MESSAGE_TRANSFER));

		// (2) RSA transformation
		list.add(rsaTransformation);

		// (3) fingerprint
		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		RSAPublicKey rsapk = rsaPublicKey;

		byte[] modulus = rsapk.getModulus().toByteArray();
		byte[] publicExp = rsapk.getPublicExponent().toByteArray();

		sha256.update(modulus);

		list.add(Base64.getEncoder().encodeToString(sha256.digest(publicExp)));

		// (4) AES transformation
		list.add(AESUtil.AES_TRANSFORMATION);

		// (5) IV
		list.add(Base64.getEncoder().encodeToString(iv.getIV()));

		// (6) RSA-encrypted AES secret key
		list.add(Base64.getEncoder().encodeToString(encryptedSecretKey));

		// (7) AES-encrypted message
		byte[] encryptedMessage = encrypt(message, AESUtil.AES_TRANSFORMATION, secretKey, iv);
		list.add(Base64.getEncoder().encodeToString(encryptedMessage));

		this.message = list.stream().collect(Collectors.joining("\t"));
	}

	@Override
	public String getMessage() {
		return message;
	}

	protected byte[] encrypt(byte[] message, String aesTransformation, SecretKey secretKey, IvParameterSpec iv)
			throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException,
			InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException {

		List<String> list = new ArrayList<>();

		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		byte[] hash = sha256.digest(message);

		// (1) data
		list.add(Base64.getEncoder().encodeToString(message));

		// (2) hash
		list.add(Base64.getEncoder().encodeToString(hash));

		String s = list.stream().collect(Collectors.joining("\t"));

		byte[] mArr = s.getBytes();

		byte[] encryptedMessage = AESUtil.encrypt(mArr, secretKey, iv, aesTransformation);

		return encryptedMessage;
	}

}
