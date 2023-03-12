package omscompanion.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import omscompanion.Main;

public final class AESUtil {
	private static final String PROP_AES_TRANSFORMATION_IDX = "aes_transformation_idx",
			PROP_AES_KEY_ALG_IDX = "aes_key_alg_idx", PROP_AES_SALT_LENGTH = "aes_salt_length",
			PROP_AES_KEYSPEC_ITER = "aes_keyspec_iterations", PROP_AES_KEY_LENGTH = "aes_key_length";
	private static final int DEF_AES_TRANSFORMATION_IDX = 0, DEF_AES_KEY_ALG_IDX = 0, DEF_AES_SALT_LENGTH = 16,
			DEF_AES_KEYSPEC_ITER = 1024, DEF_AES_KEY_LENGTH = 256;

	private AESUtil() {
	}

	public static SecretKey getSecretKeyFromPassword(char[] password, byte[] salt, String keyAlgorithm, int keyLength,
			int keySpecIterations) throws NoSuchAlgorithmException, InvalidKeySpecException {

		var factory = SecretKeyFactory.getInstance(keyAlgorithm);
		var spec = new PBEKeySpec(password, salt, keySpecIterations, keyLength);
		var secret = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
		return secret;
	}

	public static SecretKey generateRandomSecretKey(int keyLength) {
		var bArr = new byte[keyLength / 8];
		new SecureRandom().nextBytes(bArr);
		return new SecretKeySpec(bArr, "AES");
	}

	public static IvParameterSpec generateIv() {
		var iv = new byte[16];
		new SecureRandom().nextBytes(iv);
		return new IvParameterSpec(iv);
	}

	public static byte[] generateSalt(int saltLength) {
		var salt = new byte[saltLength];
		new SecureRandom().nextBytes(salt);
		return salt;
	}

	public static byte[] encrypt(byte[] input, SecretKey secretKey, IvParameterSpec iv, String aesTransformation)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
			InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

		var cipher = Cipher.getInstance(aesTransformation);
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
		return cipher.doFinal(input);
	}

	public static byte[] decrypt(byte[] cipherText, SecretKey secretKey, IvParameterSpec iv, String aesTransformation)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
			InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

		var cipher = Cipher.getInstance(aesTransformation);
		cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
		return cipher.doFinal(cipherText);
	}

	public static int getAesTransformationIdx() {
		return Integer
				.parseInt(Main.properties.getProperty(PROP_AES_TRANSFORMATION_IDX, "" + DEF_AES_TRANSFORMATION_IDX));
	}

	public static AesTransformation getAesTransformation() {
		return AesTransformation.values()[getAesTransformationIdx()];
	}

	public static int getAesKeyAlgorithmIdx() {
		return Integer.parseInt(Main.properties.getProperty(PROP_AES_KEY_ALG_IDX, "" + DEF_AES_KEY_ALG_IDX));
	}

	public static AesKeyAlgorithm getAesKeyAlgorithm() {
		return AesKeyAlgorithm.values()[getAesKeyAlgorithmIdx()];
	}

	public static int getSaltLength() {
		return Integer.parseInt(Main.properties.getProperty(PROP_AES_SALT_LENGTH, "" + DEF_AES_SALT_LENGTH));
	}

	public static int getKeyspecIterations() {
		return Integer.parseInt(Main.properties.getProperty(PROP_AES_KEYSPEC_ITER, "" + DEF_AES_KEYSPEC_ITER));
	}

	public static int getKeyLength() {
		return Integer.parseInt(Main.properties.getProperty(PROP_AES_KEY_LENGTH, "" + DEF_AES_KEY_LENGTH));
	}

}
