package omscompanion.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.function.Supplier;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import omscompanion.Main;

public final class AESUtil {
	private static final String PROP_AES_TRANSFORMATION_IDX = "aes_transformation_idx",
			PROP_AES_KEY_ALG_IDX = "aes_key_alg_idx", PROP_AES_SALT_LENGTH = "aes_salt_length",
			PROP_AES_KEYSPEC_ITER = "aes_keyspec_iterations", PROP_AES_KEY_LENGTH = "aes_key_length";
	private static final int DEF_AES_TRANSFORMATION_IDX = AesTransformation.AES_CBC_PKCS5Padding.ordinal(), 
			DEF_AES_KEY_ALG_IDX = AesKeyAlgorithm.PBKDF2WithHmacSHA256.ordinal(), DEF_AES_SALT_LENGTH = 16,
			DEF_AES_KEYSPEC_ITER = 1024, DEF_AES_KEY_LENGTH = 256;

	private AESUtil() {
	}

	public static byte[] getSecretKeyFromPassword(char[] password, byte[] salt, String keyAlgorithm, int keyLength,
			int keySpecIterations) throws NoSuchAlgorithmException, InvalidKeySpecException {

		var factory = SecretKeyFactory.getInstance(keyAlgorithm);
		var spec = new PBEKeySpec(password, salt, keySpecIterations, keyLength);
		return factory.generateSecret(spec).getEncoded();
	}

	public static byte[] generateRandomSecretKey(int keyLength) {
		var bArr = new byte[keyLength / 8];
		new SecureRandom().nextBytes(bArr);
		return bArr;
	}

	public static byte[] generateIv(AesTransformation aesTransformation) {
		var iv = new byte[aesTransformation.ivLength];
		new SecureRandom().nextBytes(iv);
		return iv;
	}

	public static byte[] generateSalt(int saltLength) {
		var salt = new byte[saltLength];
		new SecureRandom().nextBytes(salt);
		return salt;
	}

	public static byte[] process(int cipherMode, byte[] input, byte[] keyMaterial, byte[] iv, AesTransformation aesTransformation)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
			InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		var cipher = Cipher.getInstance(aesTransformation.transformation);
		cipher.init(cipherMode, new SecretKeySpec(keyMaterial, "AES"), new IvParameterSpec(iv));
		var output = cipher.doFinal(input);
		System.out.println("%scrypted %d bytes => %d".formatted(cipherMode == Cipher.DECRYPT_MODE ? "En" : "De", input.length, output.length));
		return output;
	}

	public static void process(int cipherMode, InputStream is, OutputStream os, byte[] aesKeyMaterial, byte[] iv,
			AesTransformation aesTransformation, Supplier<Boolean> cancelled)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
			InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IOException {

		var cipher = Cipher.getInstance(aesTransformation.transformation);
		cipher.init(cipherMode, new SecretKeySpec(aesKeyMaterial, "AES"), new IvParameterSpec(iv));

		var iArr = new byte[1024];
		int length;
		byte[] output;
		int inputLength = 0, outputLength = 0;

		while ((length = is.read(iArr)) > 0) {
			inputLength += length;
			output = cipher.update(iArr, 0, length);
			outputLength += output.length;
			os.write(output);

			if (cancelled != null && cancelled.get())
				return;
		}
		
		System.out.println("%scrypting - before doFinal: %d bytes => %d bytes".formatted(cipherMode == Cipher.DECRYPT_MODE ? "En" : "De", inputLength, outputLength));
		
		output = cipher.doFinal();
		os.write(output);
		outputLength += output.length;
		
		System.out.println("%scrypted - total %d bytes => %d bytes".formatted(cipherMode == Cipher.DECRYPT_MODE ? "En" : "De", inputLength, outputLength));
	}

	public static AesTransformation getTransformation() {
		var idx = Integer
				.parseInt(Main.properties.getProperty(PROP_AES_TRANSFORMATION_IDX, "" + DEF_AES_TRANSFORMATION_IDX));
		return AesTransformation.values()[idx];
	}

	public static AesKeyAlgorithm getAesKeyAlgorithm() {
		var idx = Integer.parseInt(Main.properties.getProperty(PROP_AES_KEY_ALG_IDX, "" + DEF_AES_KEY_ALG_IDX));
		return AesKeyAlgorithm.values()[idx];
	}

	public static int getSaltLength() {
		return Math.max(Integer.parseInt(Main.properties.getProperty(PROP_AES_SALT_LENGTH, "" + DEF_AES_SALT_LENGTH)),
				DEF_AES_SALT_LENGTH);
	}

	public static int getKeyspecIterations() {
		return Math.max(Integer.parseInt(Main.properties.getProperty(PROP_AES_KEYSPEC_ITER, "" + DEF_AES_KEYSPEC_ITER)),
				DEF_AES_KEYSPEC_ITER);
	}

	public static int getKeyLength() {
		return Math.max(Integer.parseInt(Main.properties.getProperty(PROP_AES_KEY_LENGTH, "" + DEF_AES_KEY_LENGTH)),
				DEF_AES_KEY_LENGTH);
	}

}
