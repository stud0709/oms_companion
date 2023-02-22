package omscompanion.crypto;

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
	private AESUtil() {
	}

	public static SecretKey getSecretKeyFromPassword(char[] password, byte[] salt, String keyAlgorithm, int keyLength,
			int keySpecIterations) throws NoSuchAlgorithmException, InvalidKeySpecException {

		SecretKeyFactory factory = SecretKeyFactory.getInstance(keyAlgorithm);
		KeySpec spec = new PBEKeySpec(password, salt, keySpecIterations, keyLength);
		SecretKey secret = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
		return secret;
	}

	public static SecretKey generateRandomSecretKey(int keyLength) {
		byte[] bArr = new byte[keyLength / 8];
		new SecureRandom().nextBytes(bArr);
		return new SecretKeySpec(bArr, "AES");
	}

	public static IvParameterSpec generateIv() {
		byte[] iv = new byte[16];
		new SecureRandom().nextBytes(iv);
		return new IvParameterSpec(iv);
	}

	public static byte[] generateSalt(int saltLength) {
		byte[] salt = new byte[saltLength];
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

	public static int getAesTransformationIdx() {
		// todo: make configurable
		return 0;
	}

	public static AesTransformation getAesTransformation() {
		return AesTransformation.values()[getAesTransformationIdx()];
	}

	public static int getAesKeyAlgorithmIdx() {
		// todo: make configurable
		return 0;
	}

	public static AesKeyAlgorithm getAesKeyAlgorithm() {
		return AesKeyAlgorithm.values()[getAesKeyAlgorithmIdx()];
	}

	public static int getSaltLength() {
		// todo: make configurable
		return 16;
	}

	public static int getKeyspecIterations() {
		// todo: make configurable
		return 1024;
	}

	public static int getKeyLength() {
		// todo: make configurable
		return 256;
	}

}
