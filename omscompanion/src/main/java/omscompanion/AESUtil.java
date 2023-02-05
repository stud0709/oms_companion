package omscompanion;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class AESUtil {
	/**
	 * See https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html for
	 * the list of supported transformations in Java
	 * 
	 * See https://developer.android.com/reference/javax/crypto/Cipher for Android
	 */
	public static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
	public static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
	public static final int KEY_LENGTH = 256;
	public static final int KEYSPEC_ITERATIONS = 1024;
	public static final int SALT_LENGTH = 16;

	private AESUtil() {
	}

	public static SecretKey getSecretKeyFromPassword(char[] password, byte[] salt)
			throws NoSuchAlgorithmException, InvalidKeySpecException {

		SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
		KeySpec spec = new PBEKeySpec(password, salt, KEYSPEC_ITERATIONS, KEY_LENGTH);
		SecretKey secret = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
		return secret;
	}

	public static SecretKey generateRandomSecretKey() {
		byte[] bArr = new byte[KEY_LENGTH / 8];
		new SecureRandom().nextBytes(bArr);
		return new SecretKeySpec(bArr, "AES");
	}

	public static IvParameterSpec generateIv() {
		byte[] iv = new byte[16];
		new SecureRandom().nextBytes(iv);
		return new IvParameterSpec(iv);
	}

	public static byte[] generateSalt() {
		byte[] salt = new byte[SALT_LENGTH];
		new SecureRandom().nextBytes(salt);
		return salt;
	}

	public static byte[] encrypt(byte[] input, SecretKey secretKey, IvParameterSpec iv, String aesTransformation)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
			InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

		Cipher cipher = Cipher.getInstance(aesTransformation);
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
		return cipher.doFinal(input);
	}

	public static byte[] decrypt(byte[] cipherText, SecretKey secretKey, IvParameterSpec iv, String aesTransformation)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
			InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

		Cipher cipher = Cipher.getInstance(aesTransformation);
		cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
		return cipher.doFinal(cipherText);
	}

}
